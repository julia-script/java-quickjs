package com.github.julia_script.quickjs;

public record EvalOptions(String filename, int flags) {
    public static EvalOptions global(String filename) {
        return new EvalOptions(filename, EvalFlags.TYPE_GLOBAL);
    }
}
