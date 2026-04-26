package com.github.julia_script.quickjs;

public enum PropertyType {
    NORMAL(0 << 4),
    GETSET(1 << 4),
    VARREF(2 << 4),
    AUTOINIT(3 << 4);

    private final int bits;

    PropertyType(int bits) {
        this.bits = bits;
    }

    public int bits() {
        return bits;
    }
}
