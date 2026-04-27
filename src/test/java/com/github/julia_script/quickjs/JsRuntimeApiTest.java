package com.github.julia_script.quickjs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsRuntimeApiTest {
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
    void runtimeConfigAndGcApisWorkWhenAvailable() {
        if (runtime.nativeApi.setRuntimeInfoHandle != null) {
            runtime.setInfo("runtime-api-test");
        }
        if (runtime.nativeApi.setMemoryLimitHandle != null) {
            runtime.setMemoryLimit(16L * 1024L * 1024L);
        }
        if (runtime.nativeApi.setMaxStackSizeHandle != null) {
            runtime.setMaxStackSize(2L * 1024L * 1024L);
        }
        if (runtime.nativeApi.updateStackTopHandle != null) {
            runtime.updateStackTop();
        }
        if (runtime.nativeApi.setGCThresholdHandle != null && runtime.nativeApi.getGCThresholdHandle != null) {
            runtime.setGCThreshold(1024L);
            assertThat(runtime.getGCThreshold()).isGreaterThanOrEqualTo(0L);
        }
        if (runtime.nativeApi.runGCHandle != null) {
            runtime.runGC();
        }
        if (runtime.nativeApi.setRuntimeOpaqueHandle != null && runtime.nativeApi.getRuntimeOpaqueHandle != null) {
            runtime.setOpaque(MemorySegment.NULL);
            assertThat(runtime.getOpaque()).isEqualTo(MemorySegment.NULL);
        }
        if (runtime.nativeApi.computeMemoryUsageHandle != null) {
            JsRuntime.MemoryUsage usage = runtime.computeMemoryUsage();
            assertThat(usage.memoryUsedSize()).isGreaterThanOrEqualTo(0L);
            assertThat(usage.mallocLimit()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    void executePendingJobWorksForPromiseMicrotask() {
        if (runtime.nativeApi.isJobPendingHandle == null || runtime.nativeApi.executePendingJobHandle == null) {
            return;
        }

        String source = "Promise.resolve(41).then(v => { globalThis.jobResult = v + 1; });";
        try (JsValue ignored = context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<job-test>",
                QuickJsNative.JS_EVAL_TYPE_GLOBAL)) {
            assertThat(ignored.isException()).isFalse();
        }

        int guard = 0;
        while (runtime.isJobPending() && guard < 1000) {
            runtime.executePendingJob();
            guard++;
        }

        try (JsValue global = context.getGlobalObject();
                JsValue jobResult = global.getPropertyStr("jobResult")) {
            assertThat(jobResult.toInt32()).isEqualTo(42);
        }
    }

    @Test
    void promiseHooksAndRejectionTrackerAreInvoked() {
        if (runtime.nativeApi.setPromiseHookHandle == null
                || runtime.nativeApi.setHostPromiseRejectionTrackerHandle == null
                || runtime.nativeApi.executePendingJobHandle == null
                || runtime.nativeApi.isJobPendingHandle == null) {
            return;
        }

        AtomicInteger promiseHookCalls = new AtomicInteger();
        AtomicBoolean rejectionTrackerCalled = new AtomicBoolean(false);
        runtime.setPromiseHook((ctx, type, promise, parentOrValue) -> promiseHookCalls.incrementAndGet());
        runtime.setHostPromiseRejectionTracker((ctx, promise, reason, isHandled) -> rejectionTrackerCalled.set(true));

        String source = "Promise.resolve(1).then(v => v + 1); Promise.reject(new Error('boom'));";
        try (JsValue ignored = context.eval(
                source,
                source.getBytes(StandardCharsets.UTF_8).length,
                "<hook-test>",
                QuickJsNative.JS_EVAL_TYPE_GLOBAL)) {
            assertThat(ignored.isException()).isFalse();
        }

        int guard = 0;
        while (runtime.isJobPending() && guard < 1000) {
            runtime.executePendingJob();
            guard++;
        }

        assertThat(promiseHookCalls.get()).isGreaterThan(0);
        assertThat(rejectionTrackerCalled.get()).isTrue();
    }
}
