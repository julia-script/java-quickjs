package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsModuleDefApiTest {
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
    void initAndAddExportWork() {
        if (!hasModuleApi()) {
            return;
        }
        JsModuleDef module = context.newCModule("test_module", (initContext, moduleDef) -> {
            JsValue value = initContext.eval(
                    "42",
                    2,
                    "<module-init>",
                    QuickJsNative.JS_EVAL_TYPE_GLOBAL);
            return moduleDef.setExport(initContext, "value", value);
        });
        assertThat(module.addExport(context, "value")).isTrue();
    }

    @Test
    void getNameReturnsModuleName() {
        if (!hasModuleApi()) {
            return;
        }
        JsModuleDef module = context.newCModule("my_module", (initContext, moduleDef) -> true);
        assertThat(module.addExport(context, "dummy")).isTrue();

        try (Atom nameAtom = module.getName(context);
                JsValue nameValue = nameAtom.toStringValue()) {
            assertThat(nameValue.toJavaString()).isEqualTo("my_module");
        }
    }

    @Test
    void fullImportViaLoaderWorks() {
        if (!hasModuleApi() || runtime.nativeApi.setModuleLoaderFuncHandle == null) {
            return;
        }
        runtime.setModuleLoaderFunc(null, (loadContext, moduleName) -> {
            if (!moduleName.equals("test_module")) {
                return null;
            }
            JsModuleDef module = loadContext.newCModule(moduleName, (initContext, moduleDef) -> {
                JsValue value = initContext.eval(
                        "123",
                        3,
                        "<module-init>",
                        QuickJsNative.JS_EVAL_TYPE_GLOBAL);
                return moduleDef.setExport(initContext, "value", value);
            });
            if (!module.addExport(loadContext, "value")) {
                return null;
            }
            return module;
        });

        String source = "import { value } from \"test_module\";\n"
                + "globalThis.testResult = value;";
        try (JsValue result = context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<test>",
                EvalFlags.TYPE_MODULE)) {
            assertThat(result.isException()).isFalse();
        }

        try (JsValue global = context.getGlobalObject();
                JsValue testResult = global.getPropertyStr("testResult")) {
            assertThat(testResult.toInt32()).isEqualTo(123);
        }
    }

    @Test
    void getImportMetaAndNamespaceWork() {
        if (!hasModuleApi() || runtime.nativeApi.setModuleLoaderFuncHandle == null) {
            return;
        }
        AtomicReference<JsModuleDef> capturedModule = new AtomicReference<>();
        runtime.setModuleLoaderFunc(null, (loadContext, moduleName) -> {
            if (!moduleName.equals("ns_test")) {
                return null;
            }
            JsModuleDef module = loadContext.newCModule(moduleName, (initContext, moduleDef) -> {
                JsValue foo = initContext.eval("10", 2, "<module-init>", QuickJsNative.JS_EVAL_TYPE_GLOBAL);
                JsValue bar = initContext.eval("20", 2, "<module-init>", QuickJsNative.JS_EVAL_TYPE_GLOBAL);
                return moduleDef.setExport(initContext, "foo", foo)
                        && moduleDef.setExport(initContext, "bar", bar);
            });
            if (!module.addExport(loadContext, "foo") || !module.addExport(loadContext, "bar")) {
                return null;
            }
            capturedModule.set(module);
            return module;
        });

        String source = "import * as ns from \"ns_test\";\n"
                + "globalThis.nsResult = ns.foo + ns.bar;";
        try (JsValue result = context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<test>",
                EvalFlags.TYPE_MODULE)) {
            assertThat(result.isException()).isFalse();
        }

        JsModuleDef module = capturedModule.get();
        assertThat(module).isNotNull();
        try (JsValue meta = module.getImportMeta(context);
                JsValue namespace = module.getNamespace(context);
                JsValue foo = namespace.getPropertyStr("foo");
                JsValue global = context.getGlobalObject();
                JsValue nsResult = global.getPropertyStr("nsResult")) {
            assertThat(meta.isObject()).isTrue();
            assertThat(namespace.isObject()).isTrue();
            assertThat(foo.toInt32()).isEqualTo(10);
            assertThat(nsResult.toInt32()).isEqualTo(30);
        }
    }

    private boolean hasModuleApi() {
        return context.nativeApi.newCModuleHandle != null
                && context.nativeApi.addModuleExportHandle != null
                && context.nativeApi.setModuleExportHandle != null
                && context.nativeApi.getImportMetaHandle != null
                && context.nativeApi.getModuleNameHandle != null
                && context.nativeApi.getModuleNamespaceHandle != null;
    }
}
