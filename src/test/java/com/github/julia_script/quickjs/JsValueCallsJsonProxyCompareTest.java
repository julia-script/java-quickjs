package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsValueCallsJsonProxyCompareTest extends QuickJsIntegrationTestBase {
    @Test
    void callAndInvokeWorkWithArguments() {
        try (JsValue fn = eval("(function(a,b){ return a+b; })");
                JsValue a = eval("2");
                JsValue b = eval("5");
                JsValue undefThis = eval("undefined");
                JsValue callResult = fn.call(undefThis, new JsValue[] {a, b})) {
            assertThat(callResult.toInt32()).isEqualTo(7);
        }

        try (JsValue obj = eval("({ sum(a,b){ return a+b; } })");
                Atom atom = Atom.ofString(context, "sum");
                JsValue a = eval("1");
                JsValue b = eval("4");
                JsValue invokeResult = obj.invoke(atom.value(), new JsValue[] {a, b})) {
            assertThat(invokeResult.toInt32()).isEqualTo(5);
        }
    }

    @Test
    void constructorCallsAndComparisonsWork() {
        try (JsValue ctor = eval("(function MyCtor(){ this.v = 42; })");
                JsValue instance = ctor.callConstructor(new JsValue[0]);
                JsValue prop = instance.getPropertyStr("v")) {
            assertThat(prop.toInt32()).isEqualTo(42);
            assertThat(instance.isInstanceOf(ctor)).isTrue();
        }

        try (JsValue left = eval("1");
                JsValue right = eval("'1'")) {
            assertThat(left.isEqual(right)).isTrue();
            assertThat(left.isStrictEqual(right)).isFalse();
            assertThat(left.isSameValueZero(left)).isTrue();
        }
    }

    @Test
    void jsonRoundTripAndProxyHelpersWork() {
        try (JsValue parsed = JsValue.parseJson(context, "{\"k\":123}", "<json>");
                JsValue key = parsed.getPropertyStr("k")) {
            assertThat(key.toInt32()).isEqualTo(123);
            try (JsValue replacer = eval("undefined");
                    JsValue space = eval("0");
                    JsValue json = parsed.jsonStringify(replacer, space)) {
                assertThat(json.toJavaString()).contains("\"k\":123");
            }
        }

        try (JsValue target = eval("({ a: 1 })");
                JsValue handler = eval("({})");
                JsValue proxy = JsValue.newProxy(context, target, handler);
                JsValue proxyTarget = proxy.getProxyTarget();
                JsValue proxyHandler = proxy.getProxyHandler();
                JsValue a = proxyTarget.getPropertyStr("a")) {
            assertThat(proxy.isProxy()).isTrue();
            assertThat(proxyTarget.isObject()).isTrue();
            assertThat(proxyHandler.isObject()).isTrue();
            assertThat(a.toInt32()).isEqualTo(1);
        }
    }
}
