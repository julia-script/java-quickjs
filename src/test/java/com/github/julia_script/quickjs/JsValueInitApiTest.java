package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
        try (JsValue boolValue = JsValue.newBool(context, true);
                JsValue intValue = JsValue.newInt32(context, 42);
                JsValue longValue = JsValue.newInt64(context, 9000L);
                JsValue uintValue = JsValue.newUint32(context, -1);
                JsValue floatValue = JsValue.newFloat64(context, 3.5)) {
            assertThat(boolValue.toBool()).isTrue();
            assertThat(intValue.toInt32()).isEqualTo(42);
            assertThat(longValue.toInt64()).isEqualTo(9000L);
            assertThat(uintValue.toFloat64()).isEqualTo(4294967295d);
            assertThat(floatValue.toFloat64()).isEqualTo(3.5);
        }
    }

    @Test
    void newNumberWorksWhenAvailable() {
        if (context.nativeApi.newNumberHandle == null) {
            return;
        }
        try (JsValue num = JsValue.newNumber(context, 12.25)) {
            assertThat(num.toFloat64()).isEqualTo(12.25);
        }
    }

    @Test
    void stringObjectAndArrayConstructorsWork() {
        if (context.nativeApi.newStringLenHandle == null
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

    @Test
    void functionAndConstructorChecksWork() {
        String fnSrc = "(function f() {})";
        String clsSrc = "(class C {})";
        String objSrc = "({})";
        try (JsValue fn = context.eval(fnSrc, fnSrc.length(), "<test>", QuickJsNative.JS_EVAL_TYPE_GLOBAL);
                JsValue cls = context.eval(clsSrc, clsSrc.length(), "<test>", QuickJsNative.JS_EVAL_TYPE_GLOBAL);
                JsValue plain = context.eval(objSrc, objSrc.length(), "<test>", QuickJsNative.JS_EVAL_TYPE_GLOBAL)) {
            assertThat(fn.isFunction()).isTrue();
            assertThat(fn.isConstructor()).isTrue();
            assertThat(cls.isFunction()).isTrue();
            assertThat(cls.isConstructor()).isTrue();
            assertThat(plain.isFunction()).isFalse();
            assertThat(plain.isConstructor()).isFalse();
        }
    }

    @Test
    void arrayBufferAndUint8ArrayConstructorsWork() {
        if (context.nativeApi.newArrayBufferHandle == null
                || context.nativeApi.newUint8ArrayHandle == null
                || context.nativeApi.newUint8ArrayCopyHandle == null) {
            return;
        }
        MemorySegment external = context.nativeApi.arena.allocateFrom(ValueLayout.JAVA_BYTE, new byte[] { 9, 8, 7, 6 });
        try (JsValue arrayBuffer = JsValue.newArrayBuffer(context, external, 4, false);
                JsValue uint8Array = JsValue.newUint8Array(context, external, 4, false);
                JsValue copied = JsValue.newUint8ArrayCopy(context, new byte[] { 1, 2, 3 })) {
            assertThat(arrayBuffer.isArrayBuffer()).isTrue();
            assertThat(uint8Array.getTypedArrayType()).hasValue(JsValue.TypedArrayType.UINT8);
            assertThat(copied.getTypedArrayType()).hasValue(JsValue.TypedArrayType.UINT8);
        }
    }

    @Test
    void detachArrayBufferWorks() {
        if (context.nativeApi.newArrayBufferCopyHandle == null) {
            return;
        }
        try (JsValue arrayBuffer = JsValue.newArrayBufferCopy(context, new byte[] { 1, 2, 3, 4 })) {
            assertThat(arrayBuffer.getArrayBuffer()).isPresent();
            arrayBuffer.detachArrayBuffer();
            assertThat(arrayBuffer.getArrayBuffer()).isEmpty();
        }
    }

    @Test
    void dupAndDupRtWork() {
        if (context.nativeApi.dupValueHandle == null || context.nativeApi.dupValueRTHandle == null) {
            return;
        }
        JsValue original = JsValue.newString(context, "dup-check");
        try (JsValue dup = original.dup();
                JsValue dupRt = original.dupRT(runtime)) {
            original.close();
            assertThat(dup.toJavaString()).isEqualTo("dup-check");
            assertThat(dupRt.toJavaString()).isEqualTo("dup-check");
        }
    }

    @Test
    void deinitRtWorks() {
        if (context.nativeApi.freeValueRTHandle == null) {
            return;
        }
        JsValue value = JsValue.newString(context, "rt-free");
        value.deinitRT(runtime);
    }

    @Test
    void toUtf8SliceWorksWithAndWithoutEmbeddedNull() {
        if (context.nativeApi.newStringLenHandle == null) {
            return;
        }
        try (JsValue normal = JsValue.newStringLen(context, "hello");
                JsValue withNull = JsValue.newStringLen(context, "foo\u0000bar")) {
            assertThat(normal.toUtf8Slice()).hasValue("hello");
            assertThat(withNull.toUtf8Slice()).hasValue("foo\u0000bar");
        }
    }

    @Test
    void genericInitConvenienceRoutesTypes() {
        try (JsValue fromNull = JsValue.initNull(context);
                JsValue fromBool = JsValue.init(context, true);
                JsValue fromInt = JsValue.init(context, 7);
                JsValue fromLong = JsValue.init(context, 8L);
                JsValue fromDouble = JsValue.init(context, 9.5d);
                JsValue fromString = JsValue.init(context, "hello");
                JsValue base = JsValue.newInt32(context, 33);
                JsValue fromJsValue = JsValue.init(context, base)) {
            assertThat(fromNull.isNull()).isTrue();
            assertThat(fromBool.toBool()).isTrue();
            assertThat(fromInt.toInt32()).isEqualTo(7);
            assertThat(fromLong.toInt64()).isEqualTo(8L);
            assertThat(fromDouble.toFloat64()).isEqualTo(9.5d);
            assertThat(fromString.toJavaString()).isEqualTo("hello");
            base.close();
            assertThat(fromJsValue.toInt32()).isEqualTo(33);
        }

        assertThatThrownBy(() -> JsValue.init(context, new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Java value type");
    }
}
