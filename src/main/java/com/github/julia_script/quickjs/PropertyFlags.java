package com.github.julia_script.quickjs;

public final class PropertyFlags {
    public static final int CONFIGURABLE = 1 << 0;
    public static final int WRITABLE = 1 << 1;
    public static final int ENUMERABLE = 1 << 2;
    public static final int C_W_E = CONFIGURABLE | WRITABLE | ENUMERABLE;
    public static final int LENGTH = 1 << 3;
    public static final int HAS_CONFIGURABLE = 1 << 8;
    public static final int HAS_WRITABLE = 1 << 9;
    public static final int HAS_ENUMERABLE = 1 << 10;
    public static final int HAS_GET = 1 << 11;
    public static final int HAS_SET = 1 << 12;
    public static final int HAS_VALUE = 1 << 13;
    public static final int THROW = 1 << 14;
    public static final int THROW_STRICT = 1 << 15;
    public static final int NO_ADD = 1 << 16;
    public static final int NO_EXOTIC = 1 << 17;
    public static final int DEFINE_PROPERTY = 1 << 18;
    public static final int REFLECT_DEFINE_PROPERTY = 1 << 19;

    private PropertyFlags() {
    }
}
