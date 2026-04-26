package com.github.julia_script.quickjs;

public final class EvalFlags {
    public static final int TYPE_GLOBAL = 0 << 0;
    public static final int TYPE_MODULE = 1 << 0;
    public static final int TYPE_DIRECT = 2 << 0;
    public static final int TYPE_INDIRECT = 3 << 0;

    public static final int FLAG_STRICT = 1 << 3;
    public static final int FLAG_STRIP = 1 << 4;
    public static final int FLAG_COMPILE_ONLY = 1 << 5;
    public static final int FLAG_BACKTRACE_BARRIER = 1 << 6;

    private EvalFlags() {
    }
}
