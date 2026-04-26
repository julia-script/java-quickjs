package com.github.julia_script.quickjs;

public record EvalOptions(int version, int evalFlags, String filename, int lineNum) {
    public static final int VERSION_1 = 1;

    public EvalOptions {
        if (filename == null) {
            throw new IllegalArgumentException("filename must not be null");
        }
        if (lineNum <= 0) {
            throw new IllegalArgumentException("lineNum must be >= 1");
        }
    }

    public static EvalOptions global(String filename) {
        return new EvalOptions(VERSION_1, EvalFlags.TYPE_GLOBAL, filename, 1);
    }

    public static EvalOptions module(String filename) {
        return new EvalOptions(VERSION_1, EvalFlags.TYPE_MODULE, filename, 1);
    }

    public static EvalOptions direct(String filename) {
        return new EvalOptions(VERSION_1, EvalFlags.TYPE_DIRECT, filename, 1);
    }

    public static EvalOptions indirect(String filename) {
        return new EvalOptions(VERSION_1, EvalFlags.TYPE_INDIRECT, filename, 1);
    }

    public EvalOptions withLineNum(int newLineNum) {
        return new EvalOptions(version, evalFlags, filename, newLineNum);
    }
}
