package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

public record AnyOpaque(Optional<MemorySegment> pointer, int classId) {
}
