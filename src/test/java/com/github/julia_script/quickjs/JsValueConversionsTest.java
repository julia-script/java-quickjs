package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

class JsValueConversionsTest extends QuickJsIntegrationTestBase {
    @Test
    void scalarConversionsWork() {
        try (JsValue boolValue = eval("true");
                JsValue intValue = eval("123");
                JsValue floatValue = eval("123.5");
                JsValue bigIntValue = eval("123n")) {
            assertThat(boolValue.toBool()).isTrue();
            assertThat(intValue.toInt32()).isEqualTo(123);
            assertThat(intValue.toUint32()).isEqualTo(123L);
            assertThat(intValue.toInt64()).isEqualTo(123L);
            assertThat(intValue.toIndex()).isEqualTo(123L);
            assertThat(floatValue.toFloat64()).isEqualTo(123.5d);
            assertThat(bigIntValue.toBigInt64()).isEqualTo(123L);
            assertThat(bigIntValue.toBigUint64()).isEqualTo(123L);
        }
    }

    @Test
    void valueConversionsReturnOwnedValues() {
        try (JsValue original = eval("'42'")) {
            try (JsValue asNumber = original.toNumber();
                    JsValue asString = original.toStringValue();
                    JsValue asObject = original.toObject();
                    JsValue asPropertyKey = original.toPropertyKey()) {
                assertThat(asNumber.toInt32()).isEqualTo(42);
                assertThat(asString.toJavaString()).isEqualTo("42");
                assertThat(asObject.isObject()).isTrue();
                assertThat(asPropertyKey.isString()).isTrue();
            }
        }
    }

    @Test
    void typedArrayHelpersExposeBufferMetadata() {
        try (JsValue typed = eval("new Uint8Array([1, 2, 3, 4])")) {
            assertThat(typed.getTypedArrayType()).contains(JsValue.TypedArrayType.UINT8);
            assertThat(typed.getUint8Array()).isPresent();

            try (JsBuffer buffer = typed.getTypedArrayBuffer().orElseThrow()) {
                assertThat(buffer.byteOffset()).isEqualTo(0);
                assertThat(buffer.byteLength()).isEqualTo(4);
                assertThat(buffer.bytesPerElement()).isEqualTo(1);
            }
        }
    }

    @Test
    void cStringApisRequireExplicitFree() {
        try (JsValue value = eval("'hello world'")) {
            JsCString cString = value.toCStringLen().orElseThrow();
            try {
                String roundTrip = new String(
                        cString.ptr().reinterpret(cString.len()).toArray(ValueLayout.JAVA_BYTE));
                assertThat(roundTrip).isEqualTo("hello world");
            } finally {
                context.freeCString(cString.ptr());
            }

            MemorySegment nullTerminated = value.toCString().orElseThrow();
            try {
                assertThat(nullTerminated.reinterpret(256).getString(0)).isEqualTo("hello world");
            } finally {
                context.freeCString(nullTerminated);
            }
        }
    }
}
