package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.charset.StandardCharsets;

public final class JsContext implements AutoCloseable {
    final QuickJsNative nativeApi;
    final MemorySegment contextPtr;
    private boolean closed;

    public JsContext(QuickJsNative nativeApi, MemorySegment contextPtr) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
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
        return eval(input, options.filename(), options.flags());
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            nativeApi.freeContextHandle.invokeExact(contextPtr);
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
}
