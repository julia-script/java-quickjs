package com.github.julia_script.quickjs;

public final class JsBuffer implements AutoCloseable {
    private final JsValue bufferValue;
    private final long byteOffset;
    private final long byteLength;
    private final long bytesPerElement;
    private boolean closed;

    JsBuffer(JsValue bufferValue, long byteOffset, long byteLength, long bytesPerElement) {
        this.bufferValue = bufferValue;
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
        this.bytesPerElement = bytesPerElement;
    }

    public JsValue bufferValue() {
        return bufferValue;
    }

    public long byteOffset() {
        return byteOffset;
    }

    public long byteLength() {
        return byteLength;
    }

    public long bytesPerElement() {
        return bytesPerElement;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bufferValue.close();
    }
}
