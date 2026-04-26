package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsContextApiTest {
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
    void getRuntimeAndGlobalObjectWork() {
        if (context.nativeApi.getRuntimeHandle == null || context.nativeApi.getGlobalObjectHandle == null) {
            return;
        }
        assertThat(context.getRuntimePtr()).isEqualTo(runtime.runtimePtr);
        try (JsValue global = context.getGlobalObject()) {
            assertThat(global.isObject()).isTrue();
        }
    }

    @Test
    void intrinsicAndFunctionProtoHelpersWorkWhenAvailable() {
        if (context.nativeApi.addIntrinsicJSONHandle == null || context.nativeApi.getFunctionProtoHandle == null) {
            return;
        }
        context.addIntrinsicJSON();
        try (JsValue parsed = eval("JSON.parse('{\"a\":7}').a");
                JsValue fnProto = context.getFunctionProto()) {
            assertThat(parsed.toInt32()).isEqualTo(7);
            assertThat(fnProto.isObject()).isTrue();
        }
    }
}
