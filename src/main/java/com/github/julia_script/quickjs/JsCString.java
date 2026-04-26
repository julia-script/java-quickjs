package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;

public record JsCString(MemorySegment ptr, long len) {
}
