package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsValueInitApiTest {
    private JsRuntime runtime;
    private JsContext context;

    @BeforeEach
    void setUp() {
        runtime = new JsRuntime();
        context = runtime.newContext();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void primitiveConstructorsWork() {
        if (context.nativeApi.newBoolHandle == null
                || context.nativeApi.newInt32Handle == null
                || context.nativeApi.newInt64Handle == null
                || context.nativeApi.newFloat64Handle == null) {
            return;
        }
        try (JsValue boolValue = JsValue.newBool(context, true);
                JsValue intValue = JsValue.newInt32(context, 42);
                JsValue longValue = JsValue.newInt64(context, 9000L);
                JsValue floatValue = JsValue.newFloat64(context, 3.5)) {
            assertThat(boolValue.toBool()).isTrue();
            assertThat(intValue.toInt32()).isEqualTo(42);
            assertThat(longValue.toInt64()).isEqualTo(9000L);
            assertThat(floatValue.toFloat64()).isEqualTo(3.5);
        }
    }

    @Test
    void stringObjectAndArrayConstructorsWork() {
        if (context.nativeApi.newStringHandle == null
                || context.nativeApi.newObjectHandle == null
                || context.nativeApi.newArrayHandle == null) {
            return;
        }
        try (JsValue str = JsValue.newString(context, "hello");
                JsValue obj = JsValue.newObject(context);
                JsValue arr = JsValue.newArray(context);
                JsValue one = JsValue.newInt32(context, 1);
                JsValue two = JsValue.newInt32(context, 2);
                JsValue from = JsValue.newArrayFrom(context, new JsValue[] { one, two })) {
            assertThat(str.toJavaString()).isEqualTo("hello");
            assertThat(obj.isObject()).isTrue();
            assertThat(arr.isArray()).isTrue();
            try (JsValue first = from.getPropertyUint32(0);
                    JsValue second = from.getPropertyUint32(1)) {
                assertThat(first.toInt32()).isEqualTo(1);
                assertThat(second.toInt32()).isEqualTo(2);
            }
        }
    }

    @Test
    void symbolDateAndErrorConstructorsWork() {
        if (context.nativeApi.newSymbolHandle == null
                || context.nativeApi.newDateHandle == null
                || context.nativeApi.newErrorHandle == null) {
            return;
        }
        try (JsValue sym = JsValue.newSymbol(context, "mySymbol", false);
                JsValue date = JsValue.newDate(context, 1_700_000_000_000.0);
                JsValue err = JsValue.newError(context)) {
            assertThat(sym.isSymbol()).isTrue();
            assertThat(date.isDate()).isTrue();
            assertThat(err.isError()).isTrue();
        }
    }

    @Test
    void arrayBufferTypedArrayAndPromiseCapabilityWork() {
        if (context.nativeApi.newArrayBufferCopyHandle == null
                || context.nativeApi.newTypedArrayHandle == null
                || context.nativeApi.newPromiseCapabilityHandle == null
                || context.nativeApi.newInt32Handle == null) {
            return;
        }
        try (JsValue arrayBuffer = JsValue.newArrayBufferCopy(context, new byte[] { 1, 2, 3, 4 });
                JsValue length = JsValue.newInt32(context, 4);
                JsValue typedArray = JsValue.newTypedArray(context, JsValue.TypedArrayType.UINT8, new JsValue[] { length });
                JsPromiseCapability promiseCapability = JsValue.newPromiseCapability(context)) {
            assertThat(arrayBuffer.isArrayBuffer()).isTrue();
            assertThat(typedArray.getTypedArrayType()).hasValue(JsValue.TypedArrayType.UINT8);
            assertThat(promiseCapability.promise().isPromise()).isTrue();
            assertThat(promiseCapability.resolve().isFunction()).isTrue();
            assertThat(promiseCapability.reject().isFunction()).isTrue();
        }
    }
}
