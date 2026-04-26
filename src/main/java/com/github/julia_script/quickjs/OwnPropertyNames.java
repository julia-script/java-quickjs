package com.github.julia_script.quickjs;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class OwnPropertyNames implements AutoCloseable, Iterable<PropertyEnum> {
    private static final MemoryLayout ENTRY_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_BOOLEAN.withName("is_enumerable"),
            MemoryLayout.paddingLayout(3),
            ValueLayout.JAVA_INT.withName("atom"));

    private final QuickJsNative nativeApi;
    private final MemorySegment contextPtr;
    private final MemorySegment entries;
    private final int length;
    private boolean closed;

    public OwnPropertyNames(QuickJsNative nativeApi, MemorySegment contextPtr, MemorySegment entries, int length) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
        this.length = length;
        if (entries.equals(MemorySegment.NULL)) {
            this.entries = MemorySegment.NULL;
        } else {
            long bytes = (long) length * ENTRY_LAYOUT.byteSize();
            this.entries = entries.reinterpret(bytes);
        }
    }

    public int length() {
        ensureOpen();
        return length;
    }

    @Override
    public Iterator<PropertyEnum> iterator() {
        return new PropertyEnumIterator();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            nativeApi.freePropertyEnumHandle.invokeExact(contextPtr, entries, length);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_FreePropertyEnum", throwable);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("OwnPropertyNames is already closed");
        }
    }

    private final class PropertyEnumIterator implements Iterator<PropertyEnum> {
        private final long stride = ENTRY_LAYOUT.byteSize();
        private int index;

        @Override
        public boolean hasNext() {
            ensureOpen();
            return index < length;
        }

        @Override
        public PropertyEnum next() {
            ensureOpen();
            if (index >= length) {
                throw new NoSuchElementException();
            }
            MemorySegment entry = entries.asSlice((long) index * stride, stride);
            boolean enumerable = entry.get(ValueLayout.JAVA_BOOLEAN, 0);
            int atomValue = entry.get(ValueLayout.JAVA_INT, 4);
            int dupValue;
            try {
                dupValue = (int) nativeApi.dupAtomHandle.invokeExact(contextPtr, atomValue);
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to call JS_DupAtom", throwable);
            }
            index++;
            return new PropertyEnum(enumerable, new Atom(nativeApi, contextPtr, dupValue));
        }
    }
}
