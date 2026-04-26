package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class QuickJsIntegrationTestBase {
    protected JsRuntime runtime;
    protected JsContext context;

    @BeforeEach
    void setUpRuntime() {
        runtime = new JsRuntime();
        context = runtime.newContext();
    }

    @AfterEach
    void tearDownRuntime() {
        if (context != null) {
            context.close();
        }
        if (runtime != null) {
            runtime.close();
        }
    }

    protected JsValue eval(String source) {
        return context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<test>",
                QuickJsNative.JS_EVAL_TYPE_GLOBAL);
    }

    protected void assertNonException(JsValue value) {
        assertThat(value.isException()).isFalse();
    }
}
