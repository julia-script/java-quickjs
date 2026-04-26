package com.github.julia_script.quickjs;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class JsContext implements AutoCloseable {
    private static final MemoryLayout JS_EVAL_OPTIONS_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("version"),
            ValueLayout.JAVA_INT.withName("eval_flags"),
            ValueLayout.ADDRESS.withName("filename"),
            ValueLayout.JAVA_INT.withName("line_num"),
            MemoryLayout.paddingLayout(32));
    @FunctionalInterface
    public interface ModuleInitFunction {
        boolean init(JsContext context, JsModuleDef moduleDef);
    }

    private record ModuleInitRegistration(QuickJsNative nativeApi, ModuleInitFunction callback) {
    }

    private static final ConcurrentHashMap<Long, ModuleInitRegistration> MODULE_INIT_CALLBACKS = new ConcurrentHashMap<>();
    private static final MethodHandle MODULE_INIT_DISPATCH_HANDLE = bindModuleInitDispatch();

    final QuickJsNative nativeApi;
    final MemorySegment contextPtr;
    private final Arena callbackArena;
    private final List<Object> callbackRegistrations = new ArrayList<>();
    private MemorySegment moduleInitCallbackStub;
    private boolean closed;

    public JsContext(QuickJsNative nativeApi, MemorySegment contextPtr) {
        this(nativeApi, contextPtr, true);
    }

    JsContext(QuickJsNative nativeApi, MemorySegment contextPtr, boolean withCallbackArena) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
        this.callbackArena = withCallbackArena ? Arena.ofShared() : null;
        this.moduleInitCallbackStub = MemorySegment.NULL;
    }

    public JsValue eval(String input, long inputLen, String filename, int evalFlags) {
        ensureOpen();
        MemorySegment sourceC = nativeApi.arena.allocateFrom(input);
        MemorySegment fileNameC = nativeApi.arena.allocateFrom(filename);
        try {
            MemorySegment value = (MemorySegment) nativeApi.evalHandle.invokeExact(
                (SegmentAllocator) nativeApi.arena,
                contextPtr,
                sourceC,
                inputLen,
                fileNameC,
                evalFlags
            );
            return new JsValue(nativeApi, contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_Eval", throwable);
        }
    }

    public JsValue eval(String input, String filename, int evalFlags) {
        long inputLen = input.getBytes(StandardCharsets.UTF_8).length;
        return eval(input, inputLen, filename, evalFlags);
    }

    public JsValue eval(String input, EvalOptions options) {
        ensureOpen();
        byte[] utf8 = input.getBytes(StandardCharsets.UTF_8);
        MemorySegment sourceC = nativeApi.arena.allocateFrom(input);
        if (nativeApi.eval2Handle == null) {
            return eval(input, utf8.length, options.filename(), options.evalFlags());
        }
        MemorySegment optionsSegment = allocateEvalOptions(options);
        try {
            MemorySegment value = (MemorySegment) nativeApi.eval2Handle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    sourceC,
                    (long) utf8.length,
                    optionsSegment);
            return new JsValue(nativeApi, contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_Eval2", throwable);
        }
    }

    public JsValue evalThis(String input, JsValue thisObj, EvalOptions options) {
        ensureOpen();
        byte[] utf8 = input.getBytes(StandardCharsets.UTF_8);
        MemorySegment sourceC = nativeApi.arena.allocateFrom(input);
        if (nativeApi.evalThis2Handle != null) {
            MemorySegment optionsSegment = allocateEvalOptions(options);
            try {
                MemorySegment value = (MemorySegment) nativeApi.evalThis2Handle.invokeExact(
                        (SegmentAllocator) nativeApi.arena,
                        contextPtr,
                        thisObj.value(),
                        sourceC,
                        (long) utf8.length,
                        optionsSegment);
                return new JsValue(nativeApi, contextPtr, value);
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to call JS_EvalThis2", throwable);
            }
        }
        requireSupported(nativeApi.evalThisHandle, "JS_EvalThis");
        try {
            MemorySegment filenameC = nativeApi.arena.allocateFrom(options.filename());
            MemorySegment value = (MemorySegment) nativeApi.evalThisHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    thisObj.value(),
                    sourceC,
                    (long) utf8.length,
                    filenameC,
                    options.evalFlags());
            return new JsValue(nativeApi, contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_EvalThis", throwable);
        }
    }

    public boolean hasException() {
        ensureOpen();
        try {
            return (boolean) nativeApi.hasExceptionHandle.invokeExact(contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_HasException", throwable);
        }
    }

    public JsValue getException() {
        ensureOpen();
        try {
            MemorySegment result = (MemorySegment) nativeApi.getExceptionHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetException", throwable);
        }
    }

    public MemorySegment getRuntimePtr() {
        ensureOpen();
        requireSupported(nativeApi.getRuntimeHandle, "JS_GetRuntime");
        try {
            return (MemorySegment) nativeApi.getRuntimeHandle.invokeExact(contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetRuntime", throwable);
        }
    }

    public JsValue getGlobalObject() {
        ensureOpen();
        requireSupported(nativeApi.getGlobalObjectHandle, "JS_GetGlobalObject");
        try {
            MemorySegment result = (MemorySegment) nativeApi.getGlobalObjectHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetGlobalObject", throwable);
        }
    }

    public JsValue throwOutOfMemory() {
        ensureOpen();
        try {
            MemorySegment result = (MemorySegment) nativeApi.throwOutOfMemoryHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ThrowOutOfMemory", throwable);
        }
    }

    public void resetUncatchableError() {
        ensureOpen();
        requireSupported(nativeApi.resetUncatchableErrorHandle, "JS_ResetUncatchableError");
        try {
            nativeApi.resetUncatchableErrorHandle.invokeExact(contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ResetUncatchableError", throwable);
        }
    }

    public JsValue evalFunction(JsValue functionObject) {
        ensureOpen();
        requireSupported(nativeApi.evalFunctionHandle, "JS_EvalFunction");
        try {
            MemorySegment result = (MemorySegment) nativeApi.evalFunctionHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    functionObject.value());
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_EvalFunction", throwable);
        }
    }

    public JsContext dup() {
        ensureOpen();
        requireSupported(nativeApi.dupContextHandle, "JS_DupContext");
        try {
            MemorySegment result = (MemorySegment) nativeApi.dupContextHandle.invokeExact(contextPtr);
            if (result.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("JS_DupContext returned null");
            }
            return new JsContext(nativeApi, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DupContext", throwable);
        }
    }

    public JsValue throwTypeError(String message) {
        return throwFormatted(nativeApi.throwTypeErrorHandle, message, "JS_ThrowTypeError");
    }

    public JsValue throwSyntaxError(String message) {
        return throwFormatted(nativeApi.throwSyntaxErrorHandle, message, "JS_ThrowSyntaxError");
    }

    public JsValue throwReferenceError(String message) {
        return throwFormatted(nativeApi.throwReferenceErrorHandle, message, "JS_ThrowReferenceError");
    }

    public JsValue throwRangeError(String message) {
        return throwFormatted(nativeApi.throwRangeErrorHandle, message, "JS_ThrowRangeError");
    }

    public JsValue throwInternalError(String message) {
        return throwFormatted(nativeApi.throwInternalErrorHandle, message, "JS_ThrowInternalError");
    }

    public void setClassProto(int classId, JsValue proto) {
        ensureOpen();
        try {
            nativeApi.setClassProtoHandle.invokeExact(contextPtr, classId, proto.value());
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetClassProto", throwable);
        }
    }

    public JsValue getClassProto(int classId) {
        ensureOpen();
        try {
            MemorySegment result = (MemorySegment) nativeApi.getClassProtoHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    classId);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetClassProto", throwable);
        }
    }

    public void freeCString(MemorySegment cStringPtr) {
        ensureOpen();
        if (cStringPtr.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            nativeApi.freeCStringHandle.invokeExact(contextPtr, cStringPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_FreeCString", throwable);
        }
    }

    public MemorySegment getOpaque() {
        ensureOpen();
        requireSupported(nativeApi.getContextOpaqueHandle, "JS_GetContextOpaque");
        try {
            return (MemorySegment) nativeApi.getContextOpaqueHandle.invokeExact(contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetContextOpaque", throwable);
        }
    }

    public void setOpaque(MemorySegment opaquePointer) {
        ensureOpen();
        requireSupported(nativeApi.setContextOpaqueHandle, "JS_SetContextOpaque");
        try {
            nativeApi.setContextOpaqueHandle.invokeExact(contextPtr, opaquePointer);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetContextOpaque", throwable);
        }
    }

    public void addIntrinsicBaseObjects() {
        invokeIntrinsic(nativeApi.addIntrinsicBaseObjectsHandle, "JS_AddIntrinsicBaseObjects");
    }

    public void addIntrinsicDate() {
        invokeIntrinsic(nativeApi.addIntrinsicDateHandle, "JS_AddIntrinsicDate");
    }

    public void addIntrinsicEval() {
        invokeIntrinsic(nativeApi.addIntrinsicEvalHandle, "JS_AddIntrinsicEval");
    }

    public void addIntrinsicRegExpCompiler() {
        ensureOpen();
        requireSupported(nativeApi.addIntrinsicRegExpCompilerHandle, "JS_AddIntrinsicRegExpCompiler");
        try {
            nativeApi.addIntrinsicRegExpCompilerHandle.invokeExact(contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_AddIntrinsicRegExpCompiler", throwable);
        }
    }

    public void addIntrinsicRegExp() {
        invokeIntrinsic(nativeApi.addIntrinsicRegExpHandle, "JS_AddIntrinsicRegExp");
    }

    public void addIntrinsicJSON() {
        invokeIntrinsic(nativeApi.addIntrinsicJSONHandle, "JS_AddIntrinsicJSON");
    }

    public void addIntrinsicProxy() {
        invokeIntrinsic(nativeApi.addIntrinsicProxyHandle, "JS_AddIntrinsicProxy");
    }

    public void addIntrinsicMapSet() {
        invokeIntrinsic(nativeApi.addIntrinsicMapSetHandle, "JS_AddIntrinsicMapSet");
    }

    public void addIntrinsicTypedArrays() {
        invokeIntrinsic(nativeApi.addIntrinsicTypedArraysHandle, "JS_AddIntrinsicTypedArrays");
    }

    public void addIntrinsicPromise() {
        invokeIntrinsic(nativeApi.addIntrinsicPromiseHandle, "JS_AddIntrinsicPromise");
    }

    public void addIntrinsicBigInt() {
        invokeIntrinsic(nativeApi.addIntrinsicBigIntHandle, "JS_AddIntrinsicBigInt");
    }

    public void addIntrinsicWeakRef() {
        invokeIntrinsic(nativeApi.addIntrinsicWeakRefHandle, "JS_AddIntrinsicWeakRef");
    }

    public void addPerformance() {
        invokeIntrinsic(nativeApi.addPerformanceHandle, "JS_AddPerformance");
    }

    public void addIntrinsicDOMException() {
        invokeIntrinsic(nativeApi.addIntrinsicDOMExceptionHandle, "JS_AddIntrinsicDOMException");
    }

    public JsValue getFunctionProto() {
        ensureOpen();
        requireSupported(nativeApi.getFunctionProtoHandle, "JS_GetFunctionProto");
        try {
            MemorySegment result = (MemorySegment) nativeApi.getFunctionProtoHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetFunctionProto", throwable);
        }
    }

    public JsValue loadModule(String basename, String filename) {
        ensureOpen();
        requireSupported(nativeApi.loadModuleHandle, "JS_LoadModule");
        MemorySegment basenameC = nativeApi.arena.allocateFrom(basename);
        MemorySegment filenameC = nativeApi.arena.allocateFrom(filename);
        try {
            MemorySegment result = (MemorySegment) nativeApi.loadModuleHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    basenameC,
                    filenameC);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_LoadModule", throwable);
        }
    }

    public int getScriptOrModuleName(int stackLevels) {
        ensureOpen();
        requireSupported(nativeApi.getScriptOrModuleNameHandle, "JS_GetScriptOrModuleName");
        try {
            return (int) nativeApi.getScriptOrModuleNameHandle.invokeExact(contextPtr, stackLevels);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetScriptOrModuleName", throwable);
        }
    }

    public JsModuleDef newCModule(String name, ModuleInitFunction initFunction) {
        ensureOpen();
        requireSupported(nativeApi.newCModuleHandle, "JS_NewCModule");
        MemorySegment nameC = nativeApi.arena.allocateFrom(name);
        try {
            MemorySegment modulePtr = (MemorySegment) nativeApi.newCModuleHandle.invokeExact(
                    contextPtr,
                    nameC,
                    ensureModuleInitCallbackStub());
            if (modulePtr.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("JS_NewCModule returned null");
            }
            MODULE_INIT_CALLBACKS.put(modulePtr.address(), new ModuleInitRegistration(nativeApi, initFunction));
            return new JsModuleDef(nativeApi, modulePtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewCModule", throwable);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            callbackRegistrations.clear();
            nativeApi.freeContextHandle.invokeExact(contextPtr);
            if (callbackArena != null) {
                callbackArena.close();
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to close JSContext", throwable);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("JsContext is already closed");
        }
    }

    private JsValue throwFormatted(java.lang.invoke.MethodHandle handle, String message, String name) {
        ensureOpen();
        MemorySegment fmt = nativeApi.arena.allocateFrom(message);
        try {
            MemorySegment result = (MemorySegment) handle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    fmt);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call " + name, throwable);
        }
    }

    private void invokeIntrinsic(java.lang.invoke.MethodHandle handle, String name) {
        ensureOpen();
        requireSupported(handle, name);
        try {
            int result = (int) handle.invokeExact(contextPtr);
            if (result < 0) {
                throw new IllegalStateException(name + " failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call " + name, throwable);
        }
    }

    private void requireSupported(java.lang.invoke.MethodHandle handle, String name) {
        if (handle == null) {
            throw new UnsupportedOperationException(name + " is not available in this QuickJS build");
        }
    }

    private MemorySegment allocateEvalOptions(EvalOptions options) {
        MemorySegment optionsSegment = nativeApi.arena.allocate(JS_EVAL_OPTIONS_LAYOUT);
        MemorySegment filename = nativeApi.arena.allocateFrom(options.filename());
        optionsSegment.set(ValueLayout.JAVA_INT, 0, options.version());
        optionsSegment.set(ValueLayout.JAVA_INT, Integer.BYTES, options.evalFlags());
        optionsSegment.set(ValueLayout.ADDRESS, 8, filename);
        optionsSegment.set(ValueLayout.JAVA_INT, 16, options.lineNum());
        return optionsSegment;
    }

    private MemorySegment ensureModuleInitCallbackStub() {
        if (!moduleInitCallbackStub.equals(MemorySegment.NULL)) {
            return moduleInitCallbackStub;
        }
        moduleInitCallbackStub = java.lang.foreign.Linker.nativeLinker().upcallStub(
                MODULE_INIT_DISPATCH_HANDLE,
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                callbackArena);
        return moduleInitCallbackStub;
    }

    MemorySegment createUpcallStub(MethodHandle handle, FunctionDescriptor descriptor) {
        ensureOpen();
        if (callbackArena == null) {
            throw new IllegalStateException("Callback arena is not available for this context");
        }
        return java.lang.foreign.Linker.nativeLinker().upcallStub(handle, descriptor, callbackArena);
    }

    void retainCallbackRegistration(Object registration) {
        ensureOpen();
        if (callbackArena == null) {
            return;
        }
        callbackRegistrations.add(registration);
    }

    @SuppressWarnings("unused")
    private static int moduleInitDispatch(MemorySegment callbackContext, MemorySegment callbackModuleDef) {
        ModuleInitRegistration registration = MODULE_INIT_CALLBACKS.remove(callbackModuleDef.address());
        if (registration == null) {
            return -1;
        }
        JsContext context = new JsContext(registration.nativeApi(), callbackContext, false);
        JsModuleDef moduleDef = new JsModuleDef(registration.nativeApi(), callbackModuleDef);
        return registration.callback().init(context, moduleDef) ? 0 : -1;
    }

    private static MethodHandle bindModuleInitDispatch() {
        try {
            return MethodHandles.lookup().findStatic(
                    JsContext.class,
                    "moduleInitDispatch",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
