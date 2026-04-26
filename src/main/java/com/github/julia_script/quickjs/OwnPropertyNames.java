package com.github.julia_script.quickjs;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public final class OwnPropertyNames implements AutoCloseable {
    private static final MemoryLayout ENTRY_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_BOOLEAN.withName("is_enumerable"),
            MemoryLayout.paddingLayout(24),
            ValueLayout.JAVA_INT.withName("atom"));

    private final QuickJsNative nativeApi;
    private final MemorySegment contextPtr;
    private final MemorySegment entries;
    private final int length;
    private boolean closed;

    OwnPropertyNames(QuickJsNative nativeApi, MemorySegment contextPtr, MemorySegment entries, int length) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
        this.entries = entries;
        this.length = length;
    }

    public int length() {
        ensureOpen();
        return length;
    }

    public List<PropertyEnum> toList() {
        ensureOpen();
        List<PropertyEnum> list = new ArrayList<>(length);
        long stride = ENTRY_LAYOUT.byteSize();
        for (int i = 0; i < length; i++) {
            MemorySegment entry = entries.asSlice((long) i * stride, stride);
            boolean enumerable = entry.get(ValueLayout.JAVA_BOOLEAN, 0);
            int atomValue = entry.get(ValueLayout.JAVA_INT, 4);
            Atom tempAtom = new Atom(nativeApi, contextPtr, atomValue);
            Atom ownedAtom = tempAtom.dup();
            tempAtom.close();
            list.add(new PropertyEnum(enumerable, ownedAtom));
        }
        return list;
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
}
