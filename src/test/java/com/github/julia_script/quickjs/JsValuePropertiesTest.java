package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsValuePropertiesTest extends QuickJsIntegrationTestBase {
    @Test
    void getSetAndDeletePropertyByStringWorks() {
        try (JsValue obj = eval("({ a: 1 })")) {
            try (JsValue value = obj.getPropertyStr("a")) {
                assertThat(value.toInt32()).isEqualTo(1);
            }

            JsValue newValue = eval("99");
            obj.setPropertyStr("b", newValue);

            assertThat(obj.hasPropertyStr("b")).isTrue();
            try (JsValue b = obj.getPropertyStr("b")) {
                assertThat(b.toInt32()).isEqualTo(99);
            }
            assertThat(obj.deletePropertyStr("b")).isTrue();
            assertThat(obj.hasPropertyStr("b")).isFalse();
        }
    }

    @Test
    void definePropertyAndDescriptorApisWork() {
        try (JsValue obj = eval("({})")) {
            JsValue val = eval("'x'");
            boolean defined = obj.definePropertyValueStr(
                    "k",
                    val,
                    PropertyFlags.CONFIGURABLE | PropertyFlags.ENUMERABLE | PropertyFlags.WRITABLE);
            assertThat(defined).isTrue();

            try (Atom atom = Atom.ofString(context, "k")) {
                try (JsValue fetched = obj.getProperty(atom.value())) {
                    assertThat(fetched.toJavaString()).isEqualTo("x");
                }

                try (PropertyDescriptor descriptor = obj.getOwnProperty(atom.value()).orElseThrow()) {
                    assertThat(descriptor.flags()).isNotZero();
                    assertThat(descriptor.value().toJavaString()).isEqualTo("x");
                }
            }
        }
    }

    @Test
    void ownPropertyNamesAndObjectShapeMethodsWork() {
        try (JsValue obj = eval("({ a: 1, b: 2 })")) {
            assertThatThrownBy(() -> obj.getOwnPropertyNames(GetPropertyNamesFlags.STRINGS))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(obj.getLength()).isGreaterThanOrEqualTo(0L);
            assertThat(obj.isExtensible()).isTrue();
            obj.preventExtensions();
            assertThat(obj.isExtensible()).isFalse();
        }
    }
}
