package com.github.julia_script.quickjs;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Definition for a custom JavaScript class ({@code JSClassDef}).
 */
public final class ClassDef {
    private static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("class_name"),
            ValueLayout.ADDRESS.withName("finalizer"),
            ValueLayout.ADDRESS.withName("gc_mark"),
            ValueLayout.ADDRESS.withName("call"),
            ValueLayout.ADDRESS.withName("exotic"));

    private static final long CLASS_NAME_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("class_name"));
    private static final long FINALIZER_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("finalizer"));
    private static final long GC_MARK_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("gc_mark"));
    private static final long CALL_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("call"));
    private static final long EXOTIC_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("exotic"));

    private static final MemoryLayout EXOTIC_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("get_own_property"),
            ValueLayout.ADDRESS.withName("get_own_property_names"),
            ValueLayout.ADDRESS.withName("delete_property"),
            ValueLayout.ADDRESS.withName("define_own_property"),
            ValueLayout.ADDRESS.withName("has_property"),
            ValueLayout.ADDRESS.withName("get_property"),
            ValueLayout.ADDRESS.withName("set_property"));
    private static final long GET_OWN_PROPERTY_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("get_own_property"));
    private static final long GET_OWN_PROPERTY_NAMES_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("get_own_property_names"));
    private static final long DELETE_PROPERTY_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("delete_property"));
    private static final long DEFINE_OWN_PROPERTY_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("define_own_property"));
    private static final long HAS_PROPERTY_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("has_property"));
    private static final long GET_PROPERTY_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("get_property"));
    private static final long SET_PROPERTY_OFFSET =
            EXOTIC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("set_property"));

    private static final MethodHandle FINALIZER_DISPATCH_HANDLE = bindDispatch(
            "finalizerDispatch",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
    private static final MethodHandle GC_MARK_DISPATCH_HANDLE = bindDispatch(
            "gcMarkDispatch",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
    private static final MethodHandle CALL_DISPATCH_HANDLE = bindDispatch(
            "callDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    int.class));
    private static final MethodHandle GET_OWN_PROPERTY_DISPATCH_HANDLE = bindDispatch(
            "getOwnPropertyDispatch",
            MethodType.methodType(
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class));
    private static final MethodHandle GET_OWN_PROPERTY_NAMES_DISPATCH_HANDLE = bindDispatch(
            "getOwnPropertyNamesDispatch",
            MethodType.methodType(
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class));
    private static final MethodHandle DELETE_PROPERTY_DISPATCH_HANDLE = bindDispatch(
            "deletePropertyDispatch",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, int.class));
    private static final MethodHandle DEFINE_OWN_PROPERTY_DISPATCH_HANDLE = bindDispatch(
            "defineOwnPropertyDispatch",
            MethodType.methodType(
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class));
    private static final MethodHandle HAS_PROPERTY_DISPATCH_HANDLE = bindDispatch(
            "hasPropertyDispatch",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, int.class));
    private static final MethodHandle GET_PROPERTY_DISPATCH_HANDLE = bindDispatch(
            "getPropertyDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class));
    private static final MethodHandle SET_PROPERTY_DISPATCH_HANDLE = bindDispatch(
            "setPropertyDispatch",
            MethodType.methodType(
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class));

    private final QuickJsNative nativeApi;
    private final JsRuntime runtime;
    private final MemorySegment segment;
    private final List<Object> callbackRegistrations = new ArrayList<>();
    private MemorySegment exoticSegment = MemorySegment.NULL;
    private ClassFinalizer classFinalizer;
    private ClassGcMark classGcMark;
    private ClassCall classCall;
    private ExoticMethods exoticMethods;

    @FunctionalInterface
    public interface ClassFinalizer {
        void onFinalize(JsRuntime runtime, JsValue value);
    }

    @FunctionalInterface
    public interface ClassGcMark {
        void onMark(JsRuntime runtime, JsValue value, MemorySegment markFunc);
    }

    @FunctionalInterface
    public interface ClassCall {
        JsValue onCall(JsContext context, JsValue funcObj, JsValue thisValue, JsValue[] args, int flags);
    }

    public static final class ExoticMethods {
        @FunctionalInterface
        public interface GetOwnProperty {
            int apply(JsContext context, MemorySegment descriptor, JsValue object, int propertyAtom);
        }

        @FunctionalInterface
        public interface GetOwnPropertyNames {
            int apply(JsContext context, MemorySegment ptab, MemorySegment plen, JsValue object);
        }

        @FunctionalInterface
        public interface DeleteProperty {
            int apply(JsContext context, JsValue object, int propertyAtom);
        }

        @FunctionalInterface
        public interface DefineOwnProperty {
            int apply(
                    JsContext context,
                    JsValue object,
                    int propertyAtom,
                    JsValue value,
                    JsValue getter,
                    JsValue setter,
                    int flags);
        }

        @FunctionalInterface
        public interface HasProperty {
            int apply(JsContext context, JsValue object, int atom);
        }

        @FunctionalInterface
        public interface GetProperty {
            JsValue apply(JsContext context, JsValue object, int atom, JsValue receiver);
        }

        @FunctionalInterface
        public interface SetProperty {
            int apply(JsContext context, JsValue object, int atom, JsValue value, JsValue receiver, int flags);
        }

        private final GetOwnProperty getOwnProperty;
        private final GetOwnPropertyNames getOwnPropertyNames;
        private final DeleteProperty deleteProperty;
        private final DefineOwnProperty defineOwnProperty;
        private final HasProperty hasProperty;
        private final GetProperty getProperty;
        private final SetProperty setProperty;

        public ExoticMethods(
                GetOwnProperty getOwnProperty,
                GetOwnPropertyNames getOwnPropertyNames,
                DeleteProperty deleteProperty,
                DefineOwnProperty defineOwnProperty,
                HasProperty hasProperty,
                GetProperty getProperty,
                SetProperty setProperty) {
            this.getOwnProperty = getOwnProperty;
            this.getOwnPropertyNames = getOwnPropertyNames;
            this.deleteProperty = deleteProperty;
            this.defineOwnProperty = defineOwnProperty;
            this.hasProperty = hasProperty;
            this.getProperty = getProperty;
            this.setProperty = setProperty;
        }
    }

    private ClassDef(QuickJsNative nativeApi, JsRuntime runtime, MemorySegment segment) {
        this.nativeApi = nativeApi;
        this.runtime = runtime;
        this.segment = segment;
    }

    public static ClassDef allocate(QuickJsNative nativeApi) {
        Objects.requireNonNull(nativeApi, "nativeApi");
        return new ClassDef(nativeApi, null, nativeApi.arena.allocate(LAYOUT));
    }

    public static ClassDef allocate(JsRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return new ClassDef(runtime.nativeApi, runtime, runtime.nativeApi.arena.allocate(LAYOUT));
    }

    public MemorySegment className() {
        return segment.get(ValueLayout.ADDRESS, CLASS_NAME_OFFSET);
    }

    public void setClassName(MemorySegment className) {
        Objects.requireNonNull(className, "className");
        segment.set(ValueLayout.ADDRESS, CLASS_NAME_OFFSET, className);
    }

    public void setClassName(String className) {
        Objects.requireNonNull(className, "className");
        setClassName(nativeApi.arena.allocateFrom(className));
    }

    public MemorySegment finalizer() {
        return segment.get(ValueLayout.ADDRESS, FINALIZER_OFFSET);
    }

    public void setFinalizer(MemorySegment finalizer) {
        Objects.requireNonNull(finalizer, "finalizer");
        this.classFinalizer = null;
        segment.set(ValueLayout.ADDRESS, FINALIZER_OFFSET, finalizer);
    }

    public void setFinalizer(ClassFinalizer finalizer) {
        this.classFinalizer = Objects.requireNonNull(finalizer, "finalizer");
        segment.set(ValueLayout.ADDRESS, FINALIZER_OFFSET, createFinalizerStub());
    }

    public MemorySegment gcMark() {
        return segment.get(ValueLayout.ADDRESS, GC_MARK_OFFSET);
    }

    public void setGcMark(MemorySegment gcMark) {
        Objects.requireNonNull(gcMark, "gcMark");
        this.classGcMark = null;
        segment.set(ValueLayout.ADDRESS, GC_MARK_OFFSET, gcMark);
    }

    public void setGcMark(ClassGcMark gcMark) {
        this.classGcMark = Objects.requireNonNull(gcMark, "gcMark");
        segment.set(ValueLayout.ADDRESS, GC_MARK_OFFSET, createGcMarkStub());
    }

    public MemorySegment call() {
        return segment.get(ValueLayout.ADDRESS, CALL_OFFSET);
    }

    public void setCall(MemorySegment call) {
        Objects.requireNonNull(call, "call");
        this.classCall = null;
        segment.set(ValueLayout.ADDRESS, CALL_OFFSET, call);
    }

    public void setCall(ClassCall call) {
        this.classCall = Objects.requireNonNull(call, "call");
        segment.set(ValueLayout.ADDRESS, CALL_OFFSET, createCallStub());
    }

    public MemorySegment exotic() {
        return segment.get(ValueLayout.ADDRESS, EXOTIC_OFFSET);
    }

    public void setExotic(MemorySegment exotic) {
        Objects.requireNonNull(exotic, "exotic");
        this.exoticMethods = null;
        this.exoticSegment = MemorySegment.NULL;
        segment.set(ValueLayout.ADDRESS, EXOTIC_OFFSET, exotic);
    }

    public void setExotic(ExoticMethods exoticMethods) {
        this.exoticMethods = Objects.requireNonNull(exoticMethods, "exoticMethods");
        this.exoticSegment = nativeApi.arena.allocate(EXOTIC_LAYOUT);
        exoticSegment.set(
                ValueLayout.ADDRESS,
                GET_OWN_PROPERTY_OFFSET,
                exoticMethods.getOwnProperty == null ? MemorySegment.NULL : createGetOwnPropertyStub());
        exoticSegment.set(
                ValueLayout.ADDRESS,
                GET_OWN_PROPERTY_NAMES_OFFSET,
                exoticMethods.getOwnPropertyNames == null ? MemorySegment.NULL : createGetOwnPropertyNamesStub());
        exoticSegment.set(
                ValueLayout.ADDRESS,
                DELETE_PROPERTY_OFFSET,
                exoticMethods.deleteProperty == null ? MemorySegment.NULL : createDeletePropertyStub());
        exoticSegment.set(
                ValueLayout.ADDRESS,
                DEFINE_OWN_PROPERTY_OFFSET,
                exoticMethods.defineOwnProperty == null ? MemorySegment.NULL : createDefineOwnPropertyStub());
        exoticSegment.set(
                ValueLayout.ADDRESS,
                HAS_PROPERTY_OFFSET,
                exoticMethods.hasProperty == null ? MemorySegment.NULL : createHasPropertyStub());
        exoticSegment.set(
                ValueLayout.ADDRESS,
                GET_PROPERTY_OFFSET,
                exoticMethods.getProperty == null ? MemorySegment.NULL : createGetPropertyStub());
        exoticSegment.set(
                ValueLayout.ADDRESS,
                SET_PROPERTY_OFFSET,
                exoticMethods.setProperty == null ? MemorySegment.NULL : createSetPropertyStub());
        segment.set(ValueLayout.ADDRESS, EXOTIC_OFFSET, exoticSegment);
    }

    MemorySegment segment() {
        return segment;
    }

    private MemorySegment createFinalizerStub() {
        MemorySegment stub = Linker.nativeLinker().upcallStub(
                FINALIZER_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT),
                nativeApi.arena);
        callbackRegistrations.add(classFinalizer);
        return stub;
    }

    private MemorySegment createGcMarkStub() {
        MemorySegment stub = Linker.nativeLinker().upcallStub(
                GC_MARK_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS),
                nativeApi.arena);
        callbackRegistrations.add(classGcMark);
        return stub;
    }

    private MemorySegment createCallStub() {
        MemorySegment stub = Linker.nativeLinker().upcallStub(
                CALL_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT),
                nativeApi.arena);
        callbackRegistrations.add(classCall);
        return stub;
    }

    private MemorySegment createGetOwnPropertyStub() {
        return Linker.nativeLinker().upcallStub(
                GET_OWN_PROPERTY_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT),
                nativeApi.arena);
    }

    private MemorySegment createGetOwnPropertyNamesStub() {
        return Linker.nativeLinker().upcallStub(
                GET_OWN_PROPERTY_NAMES_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT),
                nativeApi.arena);
    }

    private MemorySegment createDeletePropertyStub() {
        return Linker.nativeLinker().upcallStub(
                DELETE_PROPERTY_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT),
                nativeApi.arena);
    }

    private MemorySegment createDefineOwnPropertyStub() {
        return Linker.nativeLinker().upcallStub(
                DEFINE_OWN_PROPERTY_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT),
                nativeApi.arena);
    }

    private MemorySegment createHasPropertyStub() {
        return Linker.nativeLinker().upcallStub(
                HAS_PROPERTY_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT),
                nativeApi.arena);
    }

    private MemorySegment createGetPropertyStub() {
        return Linker.nativeLinker().upcallStub(
                GET_PROPERTY_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        QuickJsNative.JS_VALUE_LAYOUT),
                nativeApi.arena);
    }

    private MemorySegment createSetPropertyStub() {
        return Linker.nativeLinker().upcallStub(
                SET_PROPERTY_DISPATCH_HANDLE.bindTo(this),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT),
                nativeApi.arena);
    }

    @SuppressWarnings("unused")
    private void finalizerDispatch(MemorySegment callbackRuntime, MemorySegment callbackValue) {
        if (classFinalizer == null) {
            return;
        }
        classFinalizer.onFinalize(resolveRuntime(callbackRuntime), new JsValue(nativeApi, MemorySegment.NULL, callbackValue));
    }

    @SuppressWarnings("unused")
    private void gcMarkDispatch(MemorySegment callbackRuntime, MemorySegment callbackValue, MemorySegment markFunc) {
        if (classGcMark == null) {
            return;
        }
        classGcMark.onMark(resolveRuntime(callbackRuntime), new JsValue(nativeApi, MemorySegment.NULL, callbackValue), markFunc);
    }

    @SuppressWarnings("unused")
    private MemorySegment callDispatch(
            MemorySegment callbackContext,
            MemorySegment funcObj,
            MemorySegment thisValue,
            int argc,
            MemorySegment argv,
            int flags) {
        if (classCall == null) {
            throw new IllegalStateException("Class call callback is not configured");
        }
        JsContext context = new JsContext(nativeApi, callbackContext, false);
        JsValue[] args = toJsArgs(argc, argv, callbackContext);
        JsValue result = classCall.onCall(
                context,
                new JsValue(nativeApi, callbackContext, funcObj),
                new JsValue(nativeApi, callbackContext, thisValue),
                args,
                flags);
        if (result == null) {
            throw new IllegalStateException("Class call callback returned null");
        }
        return result.value();
    }

    @SuppressWarnings("unused")
    private int getOwnPropertyDispatch(
            MemorySegment callbackContext, MemorySegment descriptor, MemorySegment object, int propertyAtom) {
        if (exoticMethods == null || exoticMethods.getOwnProperty == null) {
            return 0;
        }
        return exoticMethods.getOwnProperty.apply(
                new JsContext(nativeApi, callbackContext, false),
                descriptor,
                new JsValue(nativeApi, callbackContext, object),
                propertyAtom);
    }

    @SuppressWarnings("unused")
    private int getOwnPropertyNamesDispatch(
            MemorySegment callbackContext, MemorySegment ptab, MemorySegment plen, MemorySegment object) {
        if (exoticMethods == null || exoticMethods.getOwnPropertyNames == null) {
            return 0;
        }
        return exoticMethods.getOwnPropertyNames.apply(
                new JsContext(nativeApi, callbackContext, false),
                ptab,
                plen,
                new JsValue(nativeApi, callbackContext, object));
    }

    @SuppressWarnings("unused")
    private int deletePropertyDispatch(MemorySegment callbackContext, MemorySegment object, int propertyAtom) {
        if (exoticMethods == null || exoticMethods.deleteProperty == null) {
            return 0;
        }
        return exoticMethods.deleteProperty.apply(
                new JsContext(nativeApi, callbackContext, false),
                new JsValue(nativeApi, callbackContext, object),
                propertyAtom);
    }

    @SuppressWarnings("unused")
    private int defineOwnPropertyDispatch(
            MemorySegment callbackContext,
            MemorySegment object,
            int propertyAtom,
            MemorySegment value,
            MemorySegment getter,
            MemorySegment setter,
            int flags) {
        if (exoticMethods == null || exoticMethods.defineOwnProperty == null) {
            return 0;
        }
        return exoticMethods.defineOwnProperty.apply(
                new JsContext(nativeApi, callbackContext, false),
                new JsValue(nativeApi, callbackContext, object),
                propertyAtom,
                new JsValue(nativeApi, callbackContext, value),
                new JsValue(nativeApi, callbackContext, getter),
                new JsValue(nativeApi, callbackContext, setter),
                flags);
    }

    @SuppressWarnings("unused")
    private int hasPropertyDispatch(MemorySegment callbackContext, MemorySegment object, int atom) {
        if (exoticMethods == null || exoticMethods.hasProperty == null) {
            return 0;
        }
        return exoticMethods.hasProperty.apply(
                new JsContext(nativeApi, callbackContext, false),
                new JsValue(nativeApi, callbackContext, object),
                atom);
    }

    @SuppressWarnings("unused")
    private MemorySegment getPropertyDispatch(
            MemorySegment callbackContext, MemorySegment object, int atom, MemorySegment receiver) {
        if (exoticMethods == null || exoticMethods.getProperty == null) {
            throw new IllegalStateException("Exotic get_property callback is not configured");
        }
        JsValue result = exoticMethods.getProperty.apply(
                new JsContext(nativeApi, callbackContext, false),
                new JsValue(nativeApi, callbackContext, object),
                atom,
                new JsValue(nativeApi, callbackContext, receiver));
        if (result == null) {
            throw new IllegalStateException("Exotic get_property callback returned null");
        }
        return result.value();
    }

    @SuppressWarnings("unused")
    private int setPropertyDispatch(
            MemorySegment callbackContext,
            MemorySegment object,
            int atom,
            MemorySegment value,
            MemorySegment receiver,
            int flags) {
        if (exoticMethods == null || exoticMethods.setProperty == null) {
            return 0;
        }
        return exoticMethods.setProperty.apply(
                new JsContext(nativeApi, callbackContext, false),
                new JsValue(nativeApi, callbackContext, object),
                atom,
                new JsValue(nativeApi, callbackContext, value),
                new JsValue(nativeApi, callbackContext, receiver),
                flags);
    }

    private JsRuntime resolveRuntime(MemorySegment callbackRuntime) {
        if (runtime != null && runtime.runtimePtr.address() == callbackRuntime.address()) {
            return runtime;
        }
        throw new IllegalStateException("ClassDef callback runtime did not match the owning runtime");
    }

    private MemorySegment[] unpackRawArgs(int argc, MemorySegment argv) {
        if (argc <= 0) {
            return new MemorySegment[0];
        }
        MemorySegment[] args = new MemorySegment[argc];
        long stride = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
        MemorySegment argsView = argv.reinterpret((long) argc * stride);
        for (int i = 0; i < argc; i++) {
            args[i] = argsView.asSlice((long) i * stride, stride);
        }
        return args;
    }

    private JsValue[] toJsArgs(int argc, MemorySegment argv, MemorySegment callbackContext) {
        MemorySegment[] rawArgs = unpackRawArgs(argc, argv);
        JsValue[] args = new JsValue[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = new JsValue(nativeApi, callbackContext, rawArgs[i]);
        }
        return args;
    }

    private static MethodHandle bindDispatch(String methodName, MethodType methodType) {
        try {
            return MethodHandles.lookup().findVirtual(ClassDef.class, methodName, methodType);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
