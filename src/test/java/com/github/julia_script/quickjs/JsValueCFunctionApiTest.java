package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsValueCFunctionApiTest {
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

    private JsValue eval(String source) {
        return context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<test>",
                QuickJsNative.JS_EVAL_TYPE_GLOBAL);
    }

    @Test
    void initHostFunctionBasicWorks() {
        if (context.nativeApi.newCFunction2Handle == null) {
            return;
        }
        try (JsValue global = context.getGlobalObject()) {
            JsValue add = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, opaque) -> {
                        int left = args.length > 0 ? args[0].toInt32() : 0;
                        int right = args.length > 1 ? args[1].toInt32() : 0;
                        return JsValue.newInt32(ctx, left + right);
                    },
                    "add",
                    2);
            global.setPropertyStr("add", add);
            try (JsValue result = eval("add(2, 3)")) {
                assertThat(result.toInt32()).isEqualTo(5);
            }
        }
    }

    @Test
    void initHostFunctionMagicWorks() {
        if (context.nativeApi.newCFunction2Handle == null) {
            return;
        }
        try (JsValue global = context.getGlobalObject()) {
            JsValue addMagic = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, opaque) -> {
                        int base = args.length > 0 ? args[0].toInt32() : 0;
                        int delta = magic == null ? 0 : magic;
                        return JsValue.newInt32(ctx, base + delta);
                    },
                    "addMagic",
                    1,
                    JsValue.HostFunctionType.GENERIC_MAGIC,
                    7);
            global.setPropertyStr("addMagic", addMagic);
            try (JsValue result = eval("addMagic(10)")) {
                assertThat(result.toInt32()).isEqualTo(17);
            }
        }
    }

    @Test
    void initHostFunctionDataAndData2Work() {
        if (context.nativeApi.newCFunctionDataHandle == null || context.nativeApi.newCFunctionData2Handle == null) {
            return;
        }
        try (JsValue global = context.getGlobalObject();
                JsValue factor = JsValue.newInt32(context, 6)) {
            JsValue mul = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, opaque) -> {
                        int input = args.length > 0 ? args[0].toInt32() : 0;
                        int multiplier = functionData.length > 0 ? functionData[0].toInt32() : 1;
                        return JsValue.newInt32(ctx, input * multiplier);
                    },
                    1,
                    0,
                    new JsValue[] { factor });
            global.setPropertyStr("mulData", mul);

            JsValue plusNamed = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, opaque) -> {
                        int input = args.length > 0 ? args[0].toInt32() : 0;
                        int offset = functionData.length > 0 ? functionData[0].toInt32() : 0;
                        return JsValue.newInt32(ctx, input + offset);
                    },
                    "plusData",
                    1,
                    0,
                    new JsValue[] { factor });
            global.setPropertyStr("plusData", plusNamed);

            try (JsValue mulResult = eval("mulData(7)");
                    JsValue plusResult = eval("plusData(1)")) {
                assertThat(mulResult.toInt32()).isEqualTo(42);
                assertThat(plusResult.toInt32()).isEqualTo(7);
            }
        }
    }

    @Test
    void initHostFunctionWithCustomProtoWorks() {
        if (context.nativeApi.newCFunction3Handle == null) {
            return;
        }
        try (JsValue global = context.getGlobalObject();
                JsValue proto = eval("({ marker: 123 })")) {
            JsValue fn = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, opaque) -> JsValue.newInt32(ctx, 1),
                    "protoFn",
                    0,
                    JsValue.HostFunctionType.GENERIC,
                    0,
                    proto);
            global.setPropertyStr("protoFn", fn);
            try (JsValue marker = eval("Object.getPrototypeOf(protoFn).marker")) {
                assertThat(marker.toInt32()).isEqualTo(123);
            }
        }
    }

    @Test
    void initHostFunctionClosureAndErrorPathWork() {
        if (context.nativeApi.newCClosureHandle == null) {
            return;
        }
        try (JsValue global = context.getGlobalObject()) {
            MemorySegment opaque = context.nativeApi.arena.allocate(8);
            opaque.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, 5L);
            JsValue closure = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, callbackOpaque) -> {
                        long value = callbackOpaque.reinterpret(8).get(java.lang.foreign.ValueLayout.JAVA_LONG, 0);
                        return JsValue.newInt64(ctx, value);
                    },
                    "closureVal",
                    0,
                    0,
                    opaque);
            global.setPropertyStr("closureVal", closure);

            JsValue throwsFn = JsValue.initHostFunction(
                    context,
                    (ctx, thisValue, args, magic, functionData, callbackOpaque) -> {
                        throw new IllegalStateException("boom");
                    },
                    "throwsFn",
                    0);
            global.setPropertyStr("throwsFn", throwsFn);

            try (JsValue closureResult = eval("closureVal()");
                    JsValue errorResult = eval("(() => { try { throwsFn(); return true; } catch (e) { return String(e).includes('boom'); } })()")) {
                assertThat(closureResult.toInt64()).isEqualTo(5L);
                assertThat(errorResult.toBool()).isTrue();
            }
        }
    }
}
