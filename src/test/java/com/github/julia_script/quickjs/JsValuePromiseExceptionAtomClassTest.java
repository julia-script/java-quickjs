package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.MemorySegment;
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
        classDef.setFinalizer((callbackRuntime, value) -> {});
        classDef.setGcMark((callbackRuntime, value, markFunc) -> {});
        classDef.setCall((callbackContext, funcObj, thisValue, args, flags) -> JsValue.undefinedValue(callbackContext));
        classDef.setExotic(new ClassDef.ExoticMethods(
                null,
                null,
                null,
                null,
                (callbackContext, object, atom) -> 0,
                (callbackContext, object, atom, receiver) -> JsValue.undefinedValue(callbackContext),
                null));

        assertThat(classDef.finalizer()).isNotEqualTo(MemorySegment.NULL);
        assertThat(classDef.gcMark()).isNotEqualTo(MemorySegment.NULL);
        assertThat(classDef.call()).isNotEqualTo(MemorySegment.NULL);
        assertThat(classDef.exotic()).isNotEqualTo(MemorySegment.NULL);
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
            int left = args[0].toInt32();
            int right = args[1].toInt32();
            return JsValue.newInt32(callbackContext, left + right);
        });
        runtime.newClass(classId, classDef);

        try (JsValue functionProto = context.getFunctionProto();
                JsValue callableObject = JsValue.newObjectClass(context, classId);
                JsValue global = context.getGlobalObject()) {
            context.setClassProto(classId, functionProto);
            global.setProperty("callableFromJava", callableObject);
            try (JsValue result = eval("callableFromJava(20, 22)")) {
                assertThat(result.toInt32()).isEqualTo(42);
                assertThat(callCount.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void exoticPropertyCallbacksAreInvokedFromJs() {
        int classId = runtime.newClassId();
        AtomicInteger hasPropertyCount = new AtomicInteger();
        AtomicInteger getPropertyCount = new AtomicInteger();

        ClassDef classDef = ClassDef.allocate(runtime);
        classDef.setClassName("ExoticFromJava");
        classDef.setExotic(new ClassDef.ExoticMethods(
                null,
                null,
                null,
                null,
                (callbackContext, object, atom) -> {
                    hasPropertyCount.incrementAndGet();
                    return 1;
                },
                (callbackContext, object, atom, receiver) -> {
                    getPropertyCount.incrementAndGet();
                    return JsValue.newInt32(callbackContext, 7);
                },
                null));
        runtime.newClass(classId, classDef);

        try (JsValue exoticObject = JsValue.newObjectClass(context, classId);
                JsValue global = context.getGlobalObject()) {
            global.setProperty("exoticFromJava", exoticObject);
            try (JsValue hasResult = eval("'answer' in exoticFromJava");
                    JsValue getResult = eval("exoticFromJava.answer")) {
                assertThat(hasResult.toBool()).isTrue();
                assertThat(getResult.toInt32()).isEqualTo(7);
                assertThat(hasPropertyCount.get()).isGreaterThan(0);
                assertThat(getPropertyCount.get()).isGreaterThan(0);
            }
        }
    }
}
