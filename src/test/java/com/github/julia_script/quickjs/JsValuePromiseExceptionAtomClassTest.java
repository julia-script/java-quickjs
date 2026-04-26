package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsValuePromiseExceptionAtomClassTest extends QuickJsIntegrationTestBase {
    @Test
    void promiseApisWorkForResolvedPromise() {
        try (JsValue promise = eval("Promise.resolve(9)")) {
            assertThat(promise.isPromise()).isTrue();
            assertThat(promise.promiseState()).isEqualTo(PromiseState.FULFILLED);
            try (JsValue result = promise.promiseResult()) {
                assertThat(result.toInt32()).isEqualTo(9);
            }
        }
    }

    @Test
    void contextExceptionAndThrowHelpersWork() {
        try (JsValue err = context.throwTypeError("bad input")) {
            assertThat(err.isException()).isTrue();
        }
        assertThat(context.hasException()).isTrue();
        try (JsValue exception = context.getException()) {
            assertThat(exception.isException()).isFalse();
            exception.setUncatchableError();
            exception.clearUncatchableError();
        }
    }

    @Test
    void atomLifecycleConversionsWork() {
        try (Atom atom = Atom.ofString(context, "abc");
                Atom dup = atom.dup();
                JsValue atomValue = atom.toValue();
                JsValue atomString = dup.toStringValue()) {
            assertThat(atom.value()).isGreaterThan(0);
            assertThat(atomValue.isString()).isTrue();
            assertThat(atomString.toJavaString()).isEqualTo("abc");
        }
    }

    @Test
    void classAndOpaqueApisBasicRoundTrip() {
        int classId = runtime.newClassId();
        assertThat(classId).isGreaterThan(0);
        assertThat(runtime.isRegisteredClass(classId)).isFalse();
    }
}
