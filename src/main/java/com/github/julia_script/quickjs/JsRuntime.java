package com.github.julia_script.quickjs;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class JsRuntime implements AutoCloseable {
    @FunctionalInterface
    public interface ModuleNormalizeFunction {
        /**
         * Returns the normalized module specifier for QuickJS, or {@code null} for the
         * native {@code NULL} return (exception / failure semantics per {@code JSModuleNormalizeFunc}).
         */
        @Nullable String normalize(JsContext context, String moduleBaseName, String moduleName);
    }

    @FunctionalInterface
    public interface ModuleLoaderFunction {
        JsModuleDef load(JsContext context, String moduleName);
    }

    @FunctionalInterface
    public interface InterruptHandler {
        boolean shouldInterrupt(JsRuntime runtime);
    }

    public enum PromiseHookType {
        INIT(0),
        BEFORE(1),
        AFTER(2),
        RESOLVE(3);

        private final int code;

        PromiseHookType(int code) {
            this.code = code;
        }

        static PromiseHookType fromCode(int code) {
            for (PromiseHookType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown JSPromiseHookType: " + code);
        }
    }

    @FunctionalInterface
    public interface PromiseHook {
        void onPromise(
                JsContext context,
                PromiseHookType hookType,
                JsValue promise,
                JsValue parentOrValue);
    }

    @FunctionalInterface
    public interface HostPromiseRejectionTracker {
        void onPromiseRejection(
                JsContext context,
                JsValue promise,
                JsValue reason,
                boolean isHandled);
    }

    @FunctionalInterface
    public interface RuntimeFinalizer {
        void finalizeRuntime(JsRuntime runtime);
    }

    public record MemoryUsage(
            long mallocSize,
            long mallocLimit,
            long memoryUsedSize,
            long mallocCount,
            long memoryUsedCount,
            long atomCount,
            long atomSize,
            long strCount,
            long strSize,
            long objCount,
            long objSize,
            long propCount,
            long propSize,
            long shapeCount,
            long shapeSize,
            long jsFuncCount,
            long jsFuncSize,
            long jsFuncCodeSize,
            long jsFuncPc2lineCount,
            long jsFuncPc2lineSize,
            long cFuncCount,
            long arrayCount,
            long fastArrayCount,
            long fastArrayElements,
            long binaryObjectCount,
            long binaryObjectSize) {
    }

    private static final MemoryLayout JS_MEMORY_USAGE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("malloc_size"),
            ValueLayout.JAVA_LONG.withName("malloc_limit"),
            ValueLayout.JAVA_LONG.withName("memory_used_size"),
            ValueLayout.JAVA_LONG.withName("malloc_count"),
            ValueLayout.JAVA_LONG.withName("memory_used_count"),
            ValueLayout.JAVA_LONG.withName("atom_count"),
            ValueLayout.JAVA_LONG.withName("atom_size"),
            ValueLayout.JAVA_LONG.withName("str_count"),
            ValueLayout.JAVA_LONG.withName("str_size"),
            ValueLayout.JAVA_LONG.withName("obj_count"),
            ValueLayout.JAVA_LONG.withName("obj_size"),
            ValueLayout.JAVA_LONG.withName("prop_count"),
            ValueLayout.JAVA_LONG.withName("prop_size"),
            ValueLayout.JAVA_LONG.withName("shape_count"),
            ValueLayout.JAVA_LONG.withName("shape_size"),
            ValueLayout.JAVA_LONG.withName("js_func_count"),
            ValueLayout.JAVA_LONG.withName("js_func_size"),
            ValueLayout.JAVA_LONG.withName("js_func_code_size"),
            ValueLayout.JAVA_LONG.withName("js_func_pc2line_count"),
            ValueLayout.JAVA_LONG.withName("js_func_pc2line_size"),
            ValueLayout.JAVA_LONG.withName("c_func_count"),
            ValueLayout.JAVA_LONG.withName("array_count"),
            ValueLayout.JAVA_LONG.withName("fast_array_count"),
            ValueLayout.JAVA_LONG.withName("fast_array_elements"),
            ValueLayout.JAVA_LONG.withName("binary_object_count"),
            ValueLayout.JAVA_LONG.withName("binary_object_size"));

    private static final MethodHandle MODULE_NORMALIZE_DISPATCH_HANDLE = bindInstanceDispatch(
            "moduleNormalizeDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class));
    private static final MethodHandle MODULE_LOADER_DISPATCH_HANDLE = bindInstanceDispatch(
            "moduleLoaderDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class));
    private static final MethodHandle INTERRUPT_DISPATCH_HANDLE = bindInstanceDispatch(
            "interruptDispatch",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
    private static final MethodHandle PROMISE_HOOK_DISPATCH_HANDLE = bindInstanceDispatch(
            "promiseHookDispatch",
            MethodType.methodType(
                    void.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class));
    private static final MethodHandle HOST_PROMISE_REJECTION_TRACKER_DISPATCH_HANDLE = bindInstanceDispatch(
            "hostPromiseRejectionTrackerDispatch",
            MethodType.methodType(
                    void.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    boolean.class,
                    MemorySegment.class));
    private static final MethodHandle RUNTIME_FINALIZER_DISPATCH_HANDLE = bindInstanceDispatch(
            "runtimeFinalizerDispatch",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));

    final QuickJsNative nativeApi;
    final MemorySegment runtimePtr;
    /**
     * Arena for native memory that must outlive {@link QuickJsNative#arena} during runtime teardown:
     * {@link ClassDef} struct layout, C strings, and upcall stubs registered with QuickJS must stay valid
     * until after {@code JS_FreeRuntime} returns.
     */
    final Arena classCallbackArena = Arena.ofConfined();
    private MemorySegment moduleNormalizeStub = MemorySegment.NULL;
    private MemorySegment moduleLoaderStub = MemorySegment.NULL;
    private MemorySegment interruptHandlerStub = MemorySegment.NULL;
    private MemorySegment promiseHookStub = MemorySegment.NULL;
    private MemorySegment hostPromiseRejectionTrackerStub = MemorySegment.NULL;
    private MemorySegment runtimeFinalizerStub = MemorySegment.NULL;
    private ModuleNormalizeFunction moduleNormalizeFunction;
    private ModuleLoaderFunction moduleLoaderFunction;
    private InterruptHandler interruptHandler;
    private PromiseHook promiseHook;
    private HostPromiseRejectionTracker hostPromiseRejectionTracker;
    private final List<RuntimeFinalizer> runtimeFinalizers = new ArrayList<>();
    /**
     * Strong references to {@link ClassDef} instances passed to {@link #newClass(int, ClassDef)} so native
     * upcall stubs bound to them cannot outlive the Java receiver due to GC.
     */
    private final List<ClassDef> registeredClassDefs = new ArrayList<>();
    private boolean closed;

    public JsRuntime() {
        this.nativeApi = new QuickJsNative();
        try {
            this.runtimePtr = (MemorySegment) nativeApi.newRuntimeHandle.invokeExact();
        } catch (Throwable throwable) {
            nativeApi.closeArena();
            throw new IllegalStateException("Failed to call JS_NewRuntime", throwable);
        }
        if (runtimePtr.equals(MemorySegment.NULL)) {
            nativeApi.closeArena();
            throw new IllegalStateException("JS_NewRuntime returned null");
        }
    }

    public JsContext newContext() {
        ensureOpen();
        try {
            MemorySegment contextPtr = (MemorySegment) nativeApi.newContextHandle.invokeExact(runtimePtr);
            if (contextPtr.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("JS_NewContext returned null");
            }
            return new JsContext(nativeApi, contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewContext", throwable);
        }
    }

    public JsContext newContextRaw() {
        ensureOpen();
        if (nativeApi.newContextRawHandle == null) {
            throw new UnsupportedOperationException("JS_NewContextRaw is not available in this QuickJS build");
        }
        try {
            MemorySegment contextPtr = (MemorySegment) nativeApi.newContextRawHandle.invokeExact(runtimePtr);
            if (contextPtr.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("JS_NewContextRaw returned null");
            }
            return new JsContext(nativeApi, contextPtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewContextRaw", throwable);
        }
    }

    public int newClassId() {
        ensureOpen();
        MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_INT);
        try {
            int ret = (int) nativeApi.newClassIdHandle.invokeExact(runtimePtr, out);
            if (ret == 0) {
                throw new IllegalStateException("JS_NewClassID returned invalid class id");
            }
            return out.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewClassID", throwable);
        }
    }

    public void newClass(int classId, ClassDef classDef) {
        ensureOpen();
        if (classDef == null) {
            throw new NullPointerException("classDef");
        }
        try {
            int ret = (int) nativeApi.newClassHandle.invokeExact(runtimePtr, classId, classDef.segment());
            if (ret != 0) {
                throw new IllegalStateException("JS_NewClass failed");
            }
            registeredClassDefs.add(classDef);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewClass", throwable);
        }
    }

    public boolean isRegisteredClass(int classId) {
        ensureOpen();
        try {
            return (boolean) nativeApi.isRegisteredClassHandle.invokeExact(runtimePtr, classId);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsRegisteredClass", throwable);
        }
    }

    public void setInfo(String info) {
        ensureOpen();
        requireSupported(nativeApi.setRuntimeInfoHandle, "JS_SetRuntimeInfo");
        MemorySegment infoC = nativeApi.arena.allocateFrom(info);
        try {
            nativeApi.setRuntimeInfoHandle.invokeExact(runtimePtr, infoC);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetRuntimeInfo", throwable);
        }
    }

    public void setMemoryLimit(long limit) {
        ensureOpen();
        requireSupported(nativeApi.setMemoryLimitHandle, "JS_SetMemoryLimit");
        try {
            nativeApi.setMemoryLimitHandle.invokeExact(runtimePtr, limit);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetMemoryLimit", throwable);
        }
    }

    public void setMaxStackSize(long size) {
        ensureOpen();
        requireSupported(nativeApi.setMaxStackSizeHandle, "JS_SetMaxStackSize");
        try {
            nativeApi.setMaxStackSizeHandle.invokeExact(runtimePtr, size);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetMaxStackSize", throwable);
        }
    }

    public void updateStackTop() {
        ensureOpen();
        requireSupported(nativeApi.updateStackTopHandle, "JS_UpdateStackTop");
        try {
            nativeApi.updateStackTopHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_UpdateStackTop", throwable);
        }
    }

    public void setGCThreshold(long threshold) {
        ensureOpen();
        requireSupported(nativeApi.setGCThresholdHandle, "JS_SetGCThreshold");
        try {
            nativeApi.setGCThresholdHandle.invokeExact(runtimePtr, threshold);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetGCThreshold", throwable);
        }
    }

    public long getGCThreshold() {
        ensureOpen();
        requireSupported(nativeApi.getGCThresholdHandle, "JS_GetGCThreshold");
        try {
            return (long) nativeApi.getGCThresholdHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetGCThreshold", throwable);
        }
    }

    public void runGC() {
        ensureOpen();
        requireSupported(nativeApi.runGCHandle, "JS_RunGC");
        try {
            nativeApi.runGCHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_RunGC", throwable);
        }
    }

    public boolean isLiveObject(JsValue value) {
        ensureOpen();
        requireSupported(nativeApi.isLiveObjectHandle, "JS_IsLiveObject");
        try {
            return (int) nativeApi.isLiveObjectHandle.invokeExact(runtimePtr, value.value()) != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsLiveObject", throwable);
        }
    }

    public MemorySegment getOpaque() {
        ensureOpen();
        requireSupported(nativeApi.getRuntimeOpaqueHandle, "JS_GetRuntimeOpaque");
        try {
            return (MemorySegment) nativeApi.getRuntimeOpaqueHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetRuntimeOpaque", throwable);
        }
    }

    public void setOpaque(MemorySegment opaque) {
        ensureOpen();
        requireSupported(nativeApi.setRuntimeOpaqueHandle, "JS_SetRuntimeOpaque");
        try {
            nativeApi.setRuntimeOpaqueHandle.invokeExact(runtimePtr, opaque);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetRuntimeOpaque", throwable);
        }
    }

    public void setDumpFlags(long flags) {
        ensureOpen();
        requireSupported(nativeApi.setDumpFlagsHandle, "JS_SetDumpFlags");
        try {
            nativeApi.setDumpFlagsHandle.invokeExact(runtimePtr, flags);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetDumpFlags", throwable);
        }
    }

    public long getDumpFlags() {
        ensureOpen();
        requireSupported(nativeApi.getDumpFlagsHandle, "JS_GetDumpFlags");
        try {
            return (long) nativeApi.getDumpFlagsHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetDumpFlags", throwable);
        }
    }

    public void setCanBlock(boolean canBlock) {
        ensureOpen();
        requireSupported(nativeApi.setCanBlockHandle, "JS_SetCanBlock");
        try {
            nativeApi.setCanBlockHandle.invokeExact(runtimePtr, canBlock);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetCanBlock", throwable);
        }
    }

    public int getClassName(int classId) {
        ensureOpen();
        requireSupported(nativeApi.getClassNameHandle, "JS_GetClassName");
        try {
            return (int) nativeApi.getClassNameHandle.invokeExact(runtimePtr, classId);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetClassName", throwable);
        }
    }

    public boolean isJobPending() {
        ensureOpen();
        requireSupported(nativeApi.isJobPendingHandle, "JS_IsJobPending");
        try {
            return (int) nativeApi.isJobPendingHandle.invokeExact(runtimePtr) != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsJobPending", throwable);
        }
    }

    public @Nullable JsContext executePendingJob() {
        ensureOpen();
        requireSupported(nativeApi.executePendingJobHandle, "JS_ExecutePendingJob");
        MemorySegment contextOut = nativeApi.arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) nativeApi.executePendingJobHandle.invokeExact(runtimePtr, contextOut);
            if (result < 0) {
                throw new IllegalStateException("JS_ExecutePendingJob failed");
            }
            if (result == 0) {
                return null;
            }
            MemorySegment contextPtr = contextOut.get(ValueLayout.ADDRESS, 0);
            if (contextPtr.equals(MemorySegment.NULL)) {
                return null;
            }
            return new JsContext(nativeApi, contextPtr, true);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ExecutePendingJob", throwable);
        }
    }

    public MemoryUsage computeMemoryUsage() {
        ensureOpen();
        requireSupported(nativeApi.computeMemoryUsageHandle, "JS_ComputeMemoryUsage");
        MemorySegment usage = nativeApi.arena.allocate(JS_MEMORY_USAGE_LAYOUT);
        try {
            nativeApi.computeMemoryUsageHandle.invokeExact(runtimePtr, usage);
            return readMemoryUsage(usage);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ComputeMemoryUsage", throwable);
        }
    }


    /**
     * Installs module normalize and loader callbacks for this runtime.
     * Either callback may be {@code null} to omit that hook (QuickJS allows a null normalize or loader stub).
     */
    public void setModuleLoaderFunc(
            @Nullable ModuleNormalizeFunction moduleNormalize, @Nullable ModuleLoaderFunction moduleLoader) {
        ensureOpen();
        if (nativeApi.setModuleLoaderFuncHandle == null) {
            throw new UnsupportedOperationException("JS_SetModuleLoaderFunc is not available in this QuickJS build");
        }
        if (moduleNormalize != null && nativeApi.jsMallocHandle == null) {
            throw new UnsupportedOperationException(
                    "js_malloc is not available; a custom module normalizer requires js_malloc for the returned specifier");
        }
        moduleNormalizeFunction = moduleNormalize;
        moduleLoaderFunction = moduleLoader;
        if (moduleNormalize == null && moduleLoader == null) {
            invokeSetModuleLoaderFunc(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
            return;
        }

        MemorySegment normalizeStub = moduleNormalize == null
                ? MemorySegment.NULL
                : ensureModuleNormalizeStub();
        MemorySegment loaderStub = moduleLoader == null
                ? MemorySegment.NULL
                : ensureModuleLoaderStub();
        invokeSetModuleLoaderFunc(normalizeStub, loaderStub, MemorySegment.NULL);
    }

    public void setInterruptHandler(@Nullable InterruptHandler handler) {
        ensureOpen();
        requireSupported(nativeApi.setInterruptHandlerHandle, "JS_SetInterruptHandler");
        interruptHandler = handler;
        MemorySegment stub = handler == null ? MemorySegment.NULL : ensureInterruptHandlerStub();
        try {
            nativeApi.setInterruptHandlerHandle.invokeExact(runtimePtr, stub, MemorySegment.NULL);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetInterruptHandler", throwable);
        }
    }

    public void setPromiseHook(@Nullable PromiseHook hook) {
        ensureOpen();
        requireSupported(nativeApi.setPromiseHookHandle, "JS_SetPromiseHook");
        promiseHook = hook;
        MemorySegment stub = hook == null ? MemorySegment.NULL : ensurePromiseHookStub();
        try {
            nativeApi.setPromiseHookHandle.invokeExact(runtimePtr, stub, MemorySegment.NULL);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetPromiseHook", throwable);
        }
    }

    public void setHostPromiseRejectionTracker(@Nullable HostPromiseRejectionTracker tracker) {
        ensureOpen();
        requireSupported(nativeApi.setHostPromiseRejectionTrackerHandle, "JS_SetHostPromiseRejectionTracker");
        hostPromiseRejectionTracker = tracker;
        MemorySegment stub = tracker == null ? MemorySegment.NULL : ensureHostPromiseRejectionTrackerStub();
        try {
            nativeApi.setHostPromiseRejectionTrackerHandle.invokeExact(runtimePtr, stub, MemorySegment.NULL);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetHostPromiseRejectionTracker", throwable);
        }
    }

    public void addFinalizer(RuntimeFinalizer finalizer) {
        ensureOpen();
        requireSupported(nativeApi.addRuntimeFinalizerHandle, "JS_AddRuntimeFinalizer");
        MemorySegment stub = ensureRuntimeFinalizerStub();
        try {
            int result = (int) nativeApi.addRuntimeFinalizerHandle.invokeExact(runtimePtr, stub, MemorySegment.NULL);
            if (result < 0) {
                throw new IllegalStateException("JS_AddRuntimeFinalizer failed");
            }
            runtimeFinalizers.add(finalizer);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_AddRuntimeFinalizer", throwable);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        Throwable closeError = null;
        try {
            moduleNormalizeFunction = null;
            moduleLoaderFunction = null;
            interruptHandler = null;
            promiseHook = null;
            hostPromiseRejectionTracker = null;
            runtimeFinalizers.clear();
            nativeApi.freeRuntimeHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            closeError = throwable;
        } finally {
            classCallbackArena.close();
            registeredClassDefs.clear();
            nativeApi.closeArena();
        }

        if (closeError != null) {
            throw new IllegalStateException("Failed to close JSRuntime", closeError);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("JsRuntime is already closed");
        }
    }

    private void invokeSetModuleLoaderFunc(
            MemorySegment moduleNormalize,
            MemorySegment moduleLoader,
            MemorySegment opaque) {
        try {
            nativeApi.setModuleLoaderFuncHandle.invokeExact(runtimePtr, moduleNormalize, moduleLoader, opaque);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetModuleLoaderFunc", throwable);
        }
    }

    private MemorySegment ensureModuleNormalizeStub() {
        if (!moduleNormalizeStub.equals(MemorySegment.NULL)) {
            return moduleNormalizeStub;
        }
        moduleNormalizeStub = Linker.nativeLinker().upcallStub(
                MODULE_NORMALIZE_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        return moduleNormalizeStub;
    }

    private MemorySegment ensureModuleLoaderStub() {
        if (!moduleLoaderStub.equals(MemorySegment.NULL)) {
            return moduleLoaderStub;
        }
        moduleLoaderStub = Linker.nativeLinker().upcallStub(
                MODULE_LOADER_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        return moduleLoaderStub;
    }

    private MemorySegment ensureInterruptHandlerStub() {
        if (!interruptHandlerStub.equals(MemorySegment.NULL)) {
            return interruptHandlerStub;
        }
        interruptHandlerStub = Linker.nativeLinker().upcallStub(
                INTERRUPT_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        return interruptHandlerStub;
    }

    private MemorySegment ensurePromiseHookStub() {
        if (!promiseHookStub.equals(MemorySegment.NULL)) {
            return promiseHookStub;
        }
        promiseHookStub = Linker.nativeLinker().upcallStub(
                PROMISE_HOOK_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        return promiseHookStub;
    }

    private MemorySegment ensureHostPromiseRejectionTrackerStub() {
        if (!hostPromiseRejectionTrackerStub.equals(MemorySegment.NULL)) {
            return hostPromiseRejectionTrackerStub;
        }
        hostPromiseRejectionTrackerStub = Linker.nativeLinker().upcallStub(
                HOST_PROMISE_REJECTION_TRACKER_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        return hostPromiseRejectionTrackerStub;
    }

    private MemorySegment ensureRuntimeFinalizerStub() {
        if (!runtimeFinalizerStub.equals(MemorySegment.NULL)) {
            return runtimeFinalizerStub;
        }
        runtimeFinalizerStub = Linker.nativeLinker().upcallStub(
                RUNTIME_FINALIZER_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        return runtimeFinalizerStub;
    }

    @SuppressWarnings("unused")
    private MemorySegment moduleNormalizeDispatch(
            MemorySegment callbackContext,
            MemorySegment callbackModuleBaseName,
            MemorySegment callbackModuleName,
            MemorySegment callbackOpaque) {
        if (moduleNormalizeFunction == null) {
            return MemorySegment.NULL;
        }
        JsContext context = new JsContext(nativeApi, callbackContext, true);
        String moduleBaseName = cStringToJava(callbackModuleBaseName);
        String moduleName = cStringToJava(callbackModuleName);
        String normalized = moduleNormalizeFunction.normalize(context, moduleBaseName, moduleName);
        if (normalized == null) {
            return MemorySegment.NULL;
        }
        return mallocUtf8ZString(callbackContext, normalized);
    }

    @SuppressWarnings("unused")
    private MemorySegment moduleLoaderDispatch(
            MemorySegment callbackContext,
            MemorySegment callbackModuleName,
            MemorySegment callbackOpaque) {
        if (moduleLoaderFunction == null) {
            return MemorySegment.NULL;
        }
        JsContext context = new JsContext(nativeApi, callbackContext, true);
        String moduleName = cStringToJava(callbackModuleName);
        JsModuleDef moduleDef = moduleLoaderFunction.load(context, moduleName);
        return moduleDef == null ? MemorySegment.NULL : moduleDef.value();
    }

    @SuppressWarnings("unused")
    private int interruptDispatch(MemorySegment callbackRuntime, MemorySegment callbackOpaque) {
        return interruptHandler != null && interruptHandler.shouldInterrupt(this) ? 1 : 0;
    }

    @SuppressWarnings("unused")
    private void promiseHookDispatch(
            MemorySegment callbackContext,
            int hookType,
            MemorySegment promise,
            MemorySegment parentOrValue,
            MemorySegment callbackOpaque) {
        if (promiseHook == null) {
            return;
        }
        JsContext context = new JsContext(nativeApi, callbackContext, false);
        promiseHook.onPromise(
                context,
                PromiseHookType.fromCode(hookType),
                new JsValue(nativeApi, callbackContext, promise),
                new JsValue(nativeApi, callbackContext, parentOrValue));
    }

    @SuppressWarnings("unused")
    private void hostPromiseRejectionTrackerDispatch(
            MemorySegment callbackContext,
            MemorySegment promise,
            MemorySegment reason,
            boolean isHandled,
            MemorySegment callbackOpaque) {
        if (hostPromiseRejectionTracker == null) {
            return;
        }
        JsContext context = new JsContext(nativeApi, callbackContext, false);
        hostPromiseRejectionTracker.onPromiseRejection(
                context,
                new JsValue(nativeApi, callbackContext, promise),
                new JsValue(nativeApi, callbackContext, reason),
                isHandled);
    }

    @SuppressWarnings("unused")
    private void runtimeFinalizerDispatch(MemorySegment callbackRuntime, MemorySegment callbackOpaque) {
        for (RuntimeFinalizer finalizer : runtimeFinalizers) {
            finalizer.finalizeRuntime(this);
        }
    }

    private static String cStringToJava(MemorySegment ptr) {
        if (ptr.equals(MemorySegment.NULL)) {
            return "";
        }
        return ptr.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /**
     * Allocates a NUL-terminated UTF-8 string with QuickJS {@code js_malloc} so the runtime can take ownership.
     */
    private MemorySegment mallocUtf8ZString(MemorySegment ctx, String text) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        long sizeWithNul = (long) utf8.length + 1L;
        MemorySegment allocated;
        try {
            allocated = (MemorySegment) nativeApi.jsMallocHandle.invokeExact(ctx, sizeWithNul);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call js_malloc", throwable);
        }
        if (allocated.equals(MemorySegment.NULL)) {
            return MemorySegment.NULL;
        }
        MemorySegment writable = allocated.reinterpret(sizeWithNul);
        if (utf8.length > 0) {
            MemorySegment.copy(MemorySegment.ofArray(utf8), 0L, writable, 0L, utf8.length);
        }
        writable.set(ValueLayout.JAVA_BYTE, (long) utf8.length, (byte) 0);
        return allocated;
    }

    private MemoryUsage readMemoryUsage(MemorySegment usage) {
        return new MemoryUsage(
                usage.get(ValueLayout.JAVA_LONG, 0L),
                usage.get(ValueLayout.JAVA_LONG, 8L),
                usage.get(ValueLayout.JAVA_LONG, 16L),
                usage.get(ValueLayout.JAVA_LONG, 24L),
                usage.get(ValueLayout.JAVA_LONG, 32L),
                usage.get(ValueLayout.JAVA_LONG, 40L),
                usage.get(ValueLayout.JAVA_LONG, 48L),
                usage.get(ValueLayout.JAVA_LONG, 56L),
                usage.get(ValueLayout.JAVA_LONG, 64L),
                usage.get(ValueLayout.JAVA_LONG, 72L),
                usage.get(ValueLayout.JAVA_LONG, 80L),
                usage.get(ValueLayout.JAVA_LONG, 88L),
                usage.get(ValueLayout.JAVA_LONG, 96L),
                usage.get(ValueLayout.JAVA_LONG, 104L),
                usage.get(ValueLayout.JAVA_LONG, 112L),
                usage.get(ValueLayout.JAVA_LONG, 120L),
                usage.get(ValueLayout.JAVA_LONG, 128L),
                usage.get(ValueLayout.JAVA_LONG, 136L),
                usage.get(ValueLayout.JAVA_LONG, 144L),
                usage.get(ValueLayout.JAVA_LONG, 152L),
                usage.get(ValueLayout.JAVA_LONG, 160L),
                usage.get(ValueLayout.JAVA_LONG, 168L),
                usage.get(ValueLayout.JAVA_LONG, 176L),
                usage.get(ValueLayout.JAVA_LONG, 184L),
                usage.get(ValueLayout.JAVA_LONG, 192L),
                usage.get(ValueLayout.JAVA_LONG, 200L));
    }

    private void requireSupported(MethodHandle handle, String name) {
        if (handle == null) {
            throw new UnsupportedOperationException(name + " is not available in this QuickJS build");
        }
    }

    private static MethodHandle bindInstanceDispatch(String methodName, MethodType methodType) {
        try {
            return MethodHandles.lookup().findVirtual(JsRuntime.class, methodName, methodType);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
