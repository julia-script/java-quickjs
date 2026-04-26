package com.github.julia_script.quickjs;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

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

    final QuickJsNative nativeApi;
    final MemorySegment runtimePtr;
    private MemorySegment moduleNormalizeStub = MemorySegment.NULL;
    private MemorySegment moduleLoaderStub = MemorySegment.NULL;
    private ModuleNormalizeFunction moduleNormalizeFunction;
    private ModuleLoaderFunction moduleLoaderFunction;
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

    public void newClass(int classId, MemorySegment classDef) {
        ensureOpen();
        try {
            int ret = (int) nativeApi.newClassHandle.invokeExact(runtimePtr, classId, classDef);
            if (ret != 0) {
                throw new IllegalStateException("JS_NewClass failed");
            }
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
            nativeApi.freeRuntimeHandle.invokeExact(runtimePtr);
        } catch (Throwable throwable) {
            closeError = throwable;
        } finally {
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

    private static MethodHandle bindInstanceDispatch(String methodName, MethodType methodType) {
        try {
            return MethodHandles.lookup().findVirtual(JsRuntime.class, methodName, methodType);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
