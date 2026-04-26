package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class JsRuntime implements AutoCloseable {
    final QuickJsNative nativeApi;
    final MemorySegment runtimePtr;
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        Throwable closeError = null;
        try {
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
}
