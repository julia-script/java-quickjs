package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.charset.StandardCharsets;

public final class Atom implements AutoCloseable {
    private final QuickJsNative nativeApi;
    private final MemorySegment contextPtr;
    private int value;
    private boolean closed;

    public Atom(QuickJsNative nativeApi, MemorySegment contextPtr, int value) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
        this.value = value;
    }

    public static Atom ofString(JsContext context, String name) {
        MemorySegment cName = context.nativeApi.arena.allocateFrom(name);
        try {
            int atomValue = (int) context.nativeApi.newAtomHandle.invokeExact(context.contextPtr, cName);
            return new Atom(context.nativeApi, context.contextPtr, atomValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewAtom", throwable);
        }
    }

    public static Atom ofStringLen(JsContext context, String name) {
        MemorySegment cName = context.nativeApi.arena.allocateFrom(name);
        long len = name.getBytes(StandardCharsets.UTF_8).length;
        try {
            int atomValue = (int) context.nativeApi.newAtomLenHandle.invokeExact(context.contextPtr, cName, len);
            return new Atom(context.nativeApi, context.contextPtr, atomValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewAtomLen", throwable);
        }
    }

    public static Atom ofUint32(JsContext context, int number) {
        try {
            int atomValue = (int) context.nativeApi.newAtomUInt32Handle.invokeExact(context.contextPtr, number);
            return new Atom(context.nativeApi, context.contextPtr, atomValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewAtomUInt32", throwable);
        }
    }

    /**
     * Creates an owned atom from an existing native atom id.
     *
     * <p>The returned {@link Atom} duplicates {@code atomValue} via {@code JS_DupAtom}, so callers
     * can safely close it without depending on the original owner's lifetime.
     */
    public static Atom ofValue(JsContext context, int atomValue) {
        try {
            int dupValue = (int) context.nativeApi.dupAtomHandle.invokeExact(context.contextPtr, atomValue);
            return new Atom(context.nativeApi, context.contextPtr, dupValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DupAtom", throwable);
        }
    }

    public Atom dup() {
        ensureOpen();
        try {
            int dupValue = (int) nativeApi.dupAtomHandle.invokeExact(contextPtr, value);
            return new Atom(nativeApi, contextPtr, dupValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DupAtom", throwable);
        }
    }

    public JsValue toValue() {
        ensureOpen();
        try {
            MemorySegment val = (MemorySegment) nativeApi.atomToValueHandle.invokeExact((SegmentAllocator) nativeApi.arena, contextPtr, value);
            return new JsValue(nativeApi, contextPtr, val);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_AtomToValue", throwable);
        }
    }

    public JsValue toStringValue() {
        ensureOpen();
        try {
            MemorySegment val = (MemorySegment) nativeApi.atomToStringHandle.invokeExact((SegmentAllocator) nativeApi.arena, contextPtr, value);
            return new JsValue(nativeApi, contextPtr, val);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_AtomToString", throwable);
        }
    }

    public int value() {
        ensureOpen();
        return value;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            nativeApi.freeAtomHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_FreeAtom", throwable);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Atom is already closed");
        }
    }
}
