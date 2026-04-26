package com.github.julia_script.quickjs;

public enum CFunctionDefType {
    CFUNC(0),
    CFUNC_MAGIC(1),
    GETTER(2),
    SETTER(3),
    GETSET(4),
    ITERATOR_NEXT(5),
    ALIAS(6),
    STRING(7),
    OBJECT(8),
    OBJECT_ALIAS(9),
    INT32(10),
    INT64(11),
    DOUBLE(12),
    UNDEFINED(13),
    CGETSET(14),
    FLAGS(15);

    private final int code;

    CFunctionDefType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
