package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link JsValue#invoke(int, JsValue[])} ({@code JS_Invoke}).
 */
class JsValueInvokeApiTest extends QuickJsIntegrationTestBase {
    @Test
    void invokeCallsMethodWithArguments() {
        try (JsValue obj = eval("({ add(a, b) { return a + b; } })");
                Atom method = Atom.ofString(context, "add");
                JsValue x = eval("10");
                JsValue y = eval("32");
                JsValue result = obj.invoke(method.value(), new JsValue[] {x, y})) {
            assertThat(result.toInt32()).isEqualTo(42);
        }
    }

    @Test
    void invokeCallsMethodWithNoArguments() {
        try (JsValue obj = eval("({ answer() { return 99; } })");
                Atom method = Atom.ofString(context, "answer");
                JsValue result = obj.invoke(method.value(), new JsValue[0])) {
            assertThat(result.toInt32()).isEqualTo(99);
        }
    }

    @Test
    void invokeBindsThisToReceiver() {
        try (JsValue obj = eval("({ x: 7, sum(y) { return this.x + y; } })");
                Atom method = Atom.ofString(context, "sum");
                JsValue y = eval("5");
                JsValue result = obj.invoke(method.value(), new JsValue[] {y})) {
            assertThat(result.toInt32()).isEqualTo(12);
        }
    }

    @Test
    void invokeWorksWithAtomFromStringLen() {
        try (JsValue obj = eval("({ 'weird-name'(n) { return n * 2; } })");
                Atom method = Atom.ofStringLen(context, "weird-name");
                JsValue n = eval("21");
                JsValue result = obj.invoke(method.value(), new JsValue[] {n})) {
            assertThat(result.toInt32()).isEqualTo(42);
        }
    }
}
