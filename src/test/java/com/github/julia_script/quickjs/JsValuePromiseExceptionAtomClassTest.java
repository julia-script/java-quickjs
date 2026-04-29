package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
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

        ClassDef classDef = ClassDef.allocate(runtime);
        classDef.setClassName("TestClassDef");
        runtime.newClass(classId, classDef);

        assertThat(runtime.isRegisteredClass(classId)).isTrue();
    }

    @Test
    void classCallCallbackIsInvokedFromJs() {
        int classId = runtime.newClassId();
        AtomicInteger callCount = new AtomicInteger();

        ClassDef classDef = ClassDef.allocate(runtime);
        classDef.setClassName("CallableFromJava");
        classDef.setCall((callbackContext, funcObj, thisValue, args, flags) -> {
            callCount.incrementAndGet();
            return JsValue.undefinedValue(callbackContext);
        });
        runtime.newClass(classId, classDef);

        try (JsValue functionProto = context.getFunctionProto();
                JsValue callableObject = JsValue.newObjectClass(context, classId);
                JsValue global = context.getGlobalObject()) {
            context.setClassProto(classId, functionProto);
            global.setProperty("callableFromJava", callableObject);
            try (JsValue result = eval("typeof callableFromJava(20, 22) === 'undefined'")) {
                assertThat(result.toBool()).isTrue();
                assertThat(callCount.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void exoticPropertyCallbacksAreInvokedFromJs() {
        int classId = runtime.newClassId();
        AtomicInteger getOwnPropertyCount = new AtomicInteger();

        try (Atom answer = Atom.ofString(context, "answer"); Atom answerHeld = answer.dup()) {
            final int answerAtomId = answerHeld.value();
            ClassDef classDef = ClassDef.allocate(runtime);
            classDef.setClassName("ExoticFromJava");
            classDef.setExotic(new ClassDef.ExoticMethods(
                    (callbackContext, descriptor, object, atom) -> {
                        getOwnPropertyCount.incrementAndGet();
                        if (atom != answerAtomId) {
                            return 0;
                        }
                        if (!descriptor.equals(java.lang.foreign.MemorySegment.NULL)) {
                            try (JsValue value = JsValue.undefinedValue(callbackContext)) {
                                ClassDef.writeDataPropertyDescriptor(callbackContext, descriptor, value);
                            }
                        }
                        return 1;
                    },
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
            runtime.newClass(classId, classDef);

            try (JsValue exoticObject = JsValue.newObjectClass(context, classId);
                    JsValue global = context.getGlobalObject()) {
                global.setProperty("exoticFromJava", exoticObject);
                try (JsValue hasResult = eval("'answer' in exoticFromJava");
                        JsValue getResult = eval("exoticFromJava.answer")) {
                    assertThat(hasResult.toBool()).isTrue();
                    assertThat(getOwnPropertyCount.get()).isGreaterThan(0);
                    assertThat(getResult.isUndefined()).isTrue();
                }
            }
        }
    }
}
