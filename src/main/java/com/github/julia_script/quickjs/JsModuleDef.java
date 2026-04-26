package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

public final class JsModuleDef {
    private final QuickJsNative nativeApi;
    private final MemorySegment modulePtr;

    JsModuleDef(QuickJsNative nativeApi, MemorySegment modulePtr) {
        this.nativeApi = nativeApi;
        this.modulePtr = modulePtr;
    }

    public MemorySegment value() {
        return modulePtr;
    }

    public boolean addExport(JsContext context, String name) {
        requireSupported(nativeApi.addModuleExportHandle, "JS_AddModuleExport");
        MemorySegment nameC = context.nativeApi.arena.allocateFrom(name);
        try {
            int result = (int) nativeApi.addModuleExportHandle.invokeExact(context.contextPtr, modulePtr, nameC);
            return result == 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_AddModuleExport", throwable);
        }
    }

    public boolean setExport(JsContext context, String name, JsValue value) {
        requireSupported(nativeApi.setModuleExportHandle, "JS_SetModuleExport");
        MemorySegment nameC = context.nativeApi.arena.allocateFrom(name);
        try {
            int result = (int) nativeApi.setModuleExportHandle.invokeExact(
                    context.contextPtr,
                    modulePtr,
                    nameC,
                    value.value());
            return result == 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetModuleExport", throwable);
        }
    }

    public JsValue getImportMeta(JsContext context) {
        requireSupported(nativeApi.getImportMetaHandle, "JS_GetImportMeta");
        try {
            MemorySegment result = (MemorySegment) nativeApi.getImportMetaHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    modulePtr);
            return new JsValue(nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetImportMeta", throwable);
        }
    }

    public Atom getName(JsContext context) {
        requireSupported(nativeApi.getModuleNameHandle, "JS_GetModuleName");
        try {
            int atomValue = (int) nativeApi.getModuleNameHandle.invokeExact(context.contextPtr, modulePtr);
            return new Atom(nativeApi, context.contextPtr, atomValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetModuleName", throwable);
        }
    }

    public JsValue getNamespace(JsContext context) {
        requireSupported(nativeApi.getModuleNamespaceHandle, "JS_GetModuleNamespace");
        try {
            MemorySegment result = (MemorySegment) nativeApi.getModuleNamespaceHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    modulePtr);
            return new JsValue(nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetModuleNamespace", throwable);
        }
    }

    private void requireSupported(java.lang.invoke.MethodHandle handle, String name) {
        if (handle == null) {
            throw new UnsupportedOperationException(name + " is not available in this QuickJS build");
        }
    }
}
