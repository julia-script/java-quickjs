package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsEvalOptionsApiTest {
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
    void evalOptionsValidationWorks() {
        assertThatThrownBy(() -> new EvalOptions(EvalOptions.VERSION_1, EvalFlags.TYPE_GLOBAL, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");
        assertThatThrownBy(() -> new EvalOptions(EvalOptions.VERSION_1, EvalFlags.TYPE_GLOBAL, "<test>", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineNum");
    }

    @Test
    void evalWithOptionsUsesFilenameAndLineNumber() {
        if (context.nativeApi.eval2Handle == null) {
            return;
        }
        EvalOptions options = EvalOptions.global("<line-num>").withLineNum(17);
        try (JsValue result = context.eval(
                "(() => { try { throw new Error('boom'); } catch (e) { return String(e.stack); } })()",
                options)) {
            String stack = result.toJavaString();
            assertThat(stack).contains("<line-num>");
            assertThat(stack).contains(":17");
        }
    }

    @Test
    void evalThisBindsThisObject() {
        try (JsValue thisObj = eval("({ value: 33 })");
                JsValue result = context.evalThis("this.value + 9", thisObj, EvalOptions.global("<this>"))) {
            assertThat(result.toInt32()).isEqualTo(42);
        }
    }

    @Test
    void evalThisWithOptionsUsesLineAndFilenameWhenAvailable() {
        if (context.nativeApi.evalThis2Handle == null) {
            return;
        }
        try (JsValue thisObj = eval("({})");
                JsValue result = context.evalThis(
                        "(() => { try { throw new Error('boom'); } catch (e) { return String(e.stack); } })()",
                        thisObj,
                        EvalOptions.global("<this-line>").withLineNum(23))) {
            String stack = result.toJavaString();
            assertThat(stack).contains("<this-line>");
            assertThat(stack).contains(":23");
        }
    }
}
