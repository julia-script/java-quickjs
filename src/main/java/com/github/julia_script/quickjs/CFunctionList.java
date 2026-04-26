package com.github.julia_script.quickjs;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class CFunctionList {
    private static final MemoryLayout ENTRY_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("name"),
            ValueLayout.JAVA_BYTE.withName("prop_flags"),
            ValueLayout.JAVA_BYTE.withName("def_type"),
            ValueLayout.JAVA_SHORT.withName("magic"),
            MemoryLayout.paddingLayout(32));

    private final MemorySegment entries;
    private final int length;

    public CFunctionList(MemorySegment entries, int length) {
        this.entries = entries;
        this.length = length;
    }

    public static CFunctionList allocateEmpty(QuickJsNative nativeApi, int length) {
        MemorySegment segment = nativeApi.arena.allocate(ENTRY_LAYOUT, length);
        return new CFunctionList(segment, length);
    }

    public MemorySegment entries() {
        return entries;
    }

    public int length() {
        return length;
    }
}
