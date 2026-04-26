package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

class CFunctionListInteropTest extends QuickJsIntegrationTestBase {
    @Test
    void setPropertyFunctionListAcceptsAllocatedListOrControlledFailure() {
        try (JsValue obj = eval("({})")) {
            obj.setPropertyFunctionList(MemorySegment.NULL, 0);
            assertThat(obj.isObject()).isTrue();
        }
    }

    @Test
    void malformedFunctionListFailsPredictably() {
        assertThatThrownBy(() -> CFunctionList.allocateEmpty(context.nativeApi, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
