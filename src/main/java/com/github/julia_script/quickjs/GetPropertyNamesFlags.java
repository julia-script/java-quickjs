package com.github.julia_script.quickjs;

public final class GetPropertyNamesFlags {
    public static final int STRING_MASK = 1 << 0;
    public static final int SYMBOL_MASK = 1 << 1;
    public static final int PRIVATE_MASK = 1 << 2;
    public static final int ENUM_ONLY = 1 << 4;
    public static final int SET_ENUM = 1 << 5;

    public static final int STRINGS = STRING_MASK;
    public static final int SYMBOLS = SYMBOL_MASK;
    public static final int ALL = STRING_MASK | SYMBOL_MASK;
    public static final int ENUM_STRINGS = STRING_MASK | ENUM_ONLY;

    private GetPropertyNamesFlags() {
    }
}
