package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
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
        requireModuleApi();
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
        requireModuleApi();
        JsModuleDef module = context.newCModule("my_module", (initContext, moduleDef) -> true);
        assertThat(module.addExport(context, "dummy")).isTrue();

        try (Atom nameAtom = module.getName(context);
                JsValue nameValue = nameAtom.toStringValue()) {
            assertThat(nameValue.toJavaString()).isEqualTo("my_module");
        }
    }

    @Test
    void fullImportViaLoaderWorks() {
        requireModuleApi();
        assertThat(runtime.nativeApi.setModuleLoaderFuncHandle)
                .as("JS_SetModuleLoaderFunc binding")
                .isNotNull();
        runtime.setModuleLoaderFunc(null, (loadContext, moduleName) -> {
            if (!moduleName.equals("test_module")) {
                return null;
            }
            JsModuleDef module = loadContext.newCModule(moduleName, (initContext, moduleDef) -> {
                JsValue value = JsValue.newInt32(initContext, 123);
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
    void moduleNormalizeRewritesSpecifierBeforeLoader() {
        requireModuleApi();
        assertThat(runtime.nativeApi.setModuleLoaderFuncHandle)
                .as("JS_SetModuleLoaderFunc binding")
                .isNotNull();
        assertThat(runtime.nativeApi.jsMallocHandle).as("js_malloc binding").isNotNull();
        runtime.setModuleLoaderFunc(
                (c, base, name) -> "alias_module".equals(name) ? "test_module" : name,
                (loadContext, moduleName) -> {
                    if (!moduleName.equals("test_module")) {
                        return null;
                    }
                    JsModuleDef module = loadContext.newCModule(moduleName, (initContext, moduleDef) -> {
                        JsValue value = JsValue.newInt32(initContext, 456);
                        return moduleDef.setExport(initContext, "value", value);
                    });
                    if (!module.addExport(loadContext, "value")) {
                        return null;
                    }
                    return module;
                });

        String source = "import { value } from \"alias_module\";\n"
                + "globalThis.aliasNormResult = value;";
        try (JsValue result = context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<test>",
                EvalFlags.TYPE_MODULE)) {
            assertThat(result.isException()).isFalse();
        }

        try (JsValue global = context.getGlobalObject();
                JsValue aliasNormResult = global.getPropertyStr("aliasNormResult")) {
            assertThat(aliasNormResult.toInt32()).isEqualTo(456);
        }
    }

    @Test
    void getImportMetaAndNamespaceWork() {
        requireModuleApi();
        assertThat(runtime.nativeApi.setModuleLoaderFuncHandle)
                .as("JS_SetModuleLoaderFunc binding")
                .isNotNull();
        AtomicReference<JsModuleDef> capturedModule = new AtomicReference<>();
        runtime.setModuleLoaderFunc(null, (loadContext, moduleName) -> {
            if (!moduleName.equals("ns_test")) {
                return null;
            }
            JsModuleDef module = loadContext.newCModule(moduleName, (initContext, moduleDef) -> {
                JsValue foo = JsValue.newInt32(initContext, 10);
                JsValue bar = JsValue.newInt32(initContext, 20);
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

    @Test
    void loaderCanCompileModuleAndConvertWithToModuleDef() {
        requireModuleApi();
        assertThat(runtime.nativeApi.setModuleLoaderFuncHandle)
                .as("JS_SetModuleLoaderFunc binding")
                .isNotNull();

        AtomicInteger loaderCalls = new AtomicInteger();
        runtime.setModuleLoaderFunc(null, (loadContext, moduleName) -> {
            loaderCalls.incrementAndGet();
            assertThat(moduleName).isEqualTo("b");

            String code = "export function f(x){ return x + 1; }";
            int compileOnlyModuleFlags = EvalFlags.TYPE_MODULE | EvalFlags.FLAG_COMPILE_ONLY;
            try (JsValue compiled = loadContext.eval(
                    code,
                    code.getBytes(StandardCharsets.UTF_8).length,
                    "b",
                    compileOnlyModuleFlags)) {
                assertThat(compiled.isException()).isFalse();
                return compiled.toModuleDef();
            }
        });

        String source = "import { f } from \"b\";\n"
                + "globalThis.toModuleDefResult = f(41);";
        try (JsValue result = context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<test>",
                EvalFlags.TYPE_MODULE)) {
            assertThat(result.isException()).isFalse();
        }

        assertThat(loaderCalls.get()).isEqualTo(1);
        try (JsValue global = context.getGlobalObject();
                JsValue moduleResult = global.getProperty("toModuleDefResult")) {
            assertThat(moduleResult.toInt32()).isEqualTo(42);
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

    private void requireModuleApi() {
        assertThat(hasModuleApi())
                .as(
                        "QuickJS module API symbols must be linked (newCModule, addModuleExport, setModuleExport,"
                                + " getImportMeta, getModuleName, getModuleNamespace)")
                .isTrue();
    }
}
