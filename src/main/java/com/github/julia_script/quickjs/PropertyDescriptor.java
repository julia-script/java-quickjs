package com.github.julia_script.quickjs;

public record PropertyDescriptor(int flags, JsValue value, JsValue getter, JsValue setter) implements AutoCloseable {
    @Override
    public void close() {
        value.close();
        getter.close();
        setter.close();
    }
}
