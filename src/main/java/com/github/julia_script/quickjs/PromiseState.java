package com.github.julia_script.quickjs;

public enum PromiseState {
    NOT_A_PROMISE(-1),
    PENDING(0),
    FULFILLED(1),
    REJECTED(2);

    private final int nativeValue;

    PromiseState(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int nativeValue() {
        return nativeValue;
    }

    public static PromiseState fromNative(int nativeValue) {
        for (PromiseState value : values()) {
            if (value.nativeValue == nativeValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown promise state: " + nativeValue);
    }
}
