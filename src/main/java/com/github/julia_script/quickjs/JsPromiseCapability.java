package com.github.julia_script.quickjs;

public record JsPromiseCapability(JsValue promise, JsValue resolve, JsValue reject) implements AutoCloseable {
    @Override
    public void close() {
        reject.close();
        resolve.close();
        promise.close();
    }
}
