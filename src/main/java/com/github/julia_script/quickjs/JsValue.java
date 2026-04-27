package com.github.julia_script.quickjs;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class JsValue implements AutoCloseable {
    private static final long JS_VALUE_SIZE = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
    private static final MethodHandle CFUNCTION_MAGIC_DISPATCH_HANDLE = bindDispatch(
            "cFunctionMagicDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    HostFunctionRegistration.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    int.class));
    private static final MethodHandle CFUNCTION_DATA_DISPATCH_HANDLE = bindDispatch(
            "cFunctionDataDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    HostFunctionRegistration.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class));
    private static final MethodHandle CCLOSURE_DISPATCH_HANDLE = bindDispatch(
            "cClosureDispatch",
            MethodType.methodType(
                    MemorySegment.class,
                    HostFunctionRegistration.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class));
    private static final MethodHandle CCLOSURE_FINALIZER_DISPATCH_HANDLE = bindDispatch(
            "cClosureFinalizerDispatch",
            MethodType.methodType(void.class, HostFunctionRegistration.class, MemorySegment.class));
    private final QuickJsNative nativeApi;
    private final MemorySegment contextPtr;
    private final MemorySegment value;
    private boolean closed;

    public final class Tag {
        public static final long BIG_INT = -9;
        public static final long SYMBOL = -8;
        public static final long STRING = -7;
        public static final long STRING_ROPE = -6;
        public static final long MODULE = -3;
        public static final long FUNCTION_BYTECODE = -2;
        public static final long OBJECT = -1;
        public static final long INT = 0;
        public static final long BOOL = 1;
        public static final long NULL = 2;
        public static final long UNDEFINED = 3;
        public static final long UNINITIALIZED = 4;
        public static final long CATCH_OFFSET = 5;
        public static final long EXCEPTION = 6;
        public static final long SHORT_BIG_INT = 7;
        public static final long FLOAT64 = 8;

    }

    public enum TypedArrayType {
        UINT8C(0),
        INT8(1),
        UINT8(2),
        INT16(3),
        UINT16(4),
        INT32(5),
        UINT32(6),
        BIG_INT64(7),
        BIG_UINT64(8),
        FLOAT16(9),
        FLOAT32(10),
        FLOAT64(11);

        private final int value;

        TypedArrayType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Optional<TypedArrayType> fromNative(int nativeCode) {
            for (TypedArrayType type : values()) {
                if (type.value == nativeCode) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Java host callback invoked by QuickJS when a host-backed function is called
     * from JavaScript.
     * <p>
     * Use this for all host/native function entry points created via
     * {@code initHostFunction(...)}.
     * The same callback shape is used for plain functions, magic/cproto variants,
     * data-bound
     * functions, and closure functions. Unused optional inputs are provided as
     * {@code null}
     * or empty arrays as appropriate.
     */
    @FunctionalInterface
    public interface HostFunctionCallback {
        /**
         * Executes a host function call.
         *
         * @param context      active JavaScript context for the call
         * @param thisValue    JavaScript {@code this} value
         * @param args         call arguments (empty when no args are passed)
         * @param magic        optional magic value for magic/cproto variants, otherwise
         *                     {@code null}
         * @param functionData optional captured data values for data variants,
         *                     otherwise empty
         * @param opaque       optional opaque pointer for closure variants, otherwise
         *                     {@link MemorySegment#NULL}
         * @return a JavaScript value result; returning {@code null} maps to JS
         *         {@code undefined}
         */
        JsValue call(
                JsContext context,
                JsValue thisValue,
                JsValue[] args,
                Integer magic,
                JsValue[] functionData,
                MemorySegment opaque);
    }

    /**
     * Finalizer for opaque data used by closure-style host functions.
     * <p>
     * This runs when QuickJS disposes the closure and gives your code a chance to
     * clean up
     * resources associated with the opaque pointer.
     */
    @FunctionalInterface
    public interface HostFunctionFinalizer {
        /**
         * Cleans up closure opaque state.
         *
         * @param opaque opaque pointer originally passed to
         *               {@code initHostFunction(..., opaque, ...)}
         */
        void finalizeOpaque(MemorySegment opaque);
    }

    /**
     * QuickJS host function calling convention/type.
     * <p>
     * Most users should start with {@link #GENERIC}. Use other variants only when
     * you need
     * constructor/getter/setter/iterator semantics or magic-enabled forms.
     */
    public enum HostFunctionType {
        GENERIC(0),
        GENERIC_MAGIC(1),
        CONSTRUCTOR(2),
        CONSTRUCTOR_MAGIC(3),
        CONSTRUCTOR_OR_FUNC(4),
        CONSTRUCTOR_OR_FUNC_MAGIC(5),
        FLOAT_FLOAT(6),
        FLOAT_FLOAT_FLOAT(7),
        GETTER(8),
        SETTER(9),
        GETTER_MAGIC(10),
        SETTER_MAGIC(11),
        ITERATOR_NEXT(12);

        private final int value;

        HostFunctionType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    private record HostFunctionRegistration(
            QuickJsNative nativeApi,
            HostFunctionCallback callback,
            HostFunctionFinalizer finalizer,
            int dataLength) {
    }

    public long getU() {
        return value.get(ValueLayout.JAVA_LONG, 0);
    }

    public long getTag() {
        return value.get(ValueLayout.JAVA_LONG, 8);
    }

    public JsValue(QuickJsNative nativeApi, MemorySegment contextPtr, MemorySegment value) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
        this.value = value;
    }

    public static JsValue newBool(JsContext context, boolean input) {
        MemorySegment result = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT);
        result.set(ValueLayout.JAVA_LONG, 0, input ? 1L : 0L);
        result.set(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG.byteSize(), Tag.BOOL);
        return new JsValue(context.nativeApi, context.contextPtr, result);
    }

    public static JsValue newInt32(JsContext context, int input) {
        MemorySegment result = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT);
        result.set(ValueLayout.JAVA_LONG, 0, input);
        result.set(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG.byteSize(), Tag.INT);
        return new JsValue(context.nativeApi, context.contextPtr, result);
    }

    public static JsValue newInt64(JsContext context, long input) {
        if (input >= Integer.MIN_VALUE && input <= Integer.MAX_VALUE) {
            return newInt32(context, (int) input);
        }
        return newFloat64(context, input);
    }

    public static JsValue newUint32(JsContext context, int input) {
        if (input >= 0) {
            return newInt32(context, input);
        }
        long unsigned = Integer.toUnsignedLong(input);
        return newFloat64(context, unsigned);
    }

    public static JsValue newFloat64(JsContext context, double input) {
        MemorySegment result = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT);
        result.set(ValueLayout.JAVA_LONG, 0, Double.doubleToRawLongBits(input));
        result.set(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG.byteSize(), Tag.FLOAT64);
        return new JsValue(context.nativeApi, context.contextPtr, result);
    }

    public static JsValue newNumber(JsContext context, double input) {
        requireSupported(context.nativeApi.newNumberHandle, "JS_NewNumber");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newNumberHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    input);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewNumber", throwable);
        }
    }

    public static JsValue newBigInt64(JsContext context, long input) {
        requireSupported(context.nativeApi.newBigInt64Handle, "JS_NewBigInt64");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newBigInt64Handle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    input);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewBigInt64", throwable);
        }
    }

    public static JsValue newBigUint64(JsContext context, long input) {
        requireSupported(context.nativeApi.newBigUint64Handle, "JS_NewBigUint64");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newBigUint64Handle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    input);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewBigUint64", throwable);
        }
    }

    public static JsValue newString(JsContext context, String input) {
        return newStringLen(context, input);
    }

    public static JsValue newStringLen(JsContext context, String input) {
        requireSupported(context.nativeApi.newStringLenHandle, "JS_NewStringLen");
        MemorySegment inputC = context.nativeApi.arena.allocateFrom(input);
        long inputLen = input.getBytes(StandardCharsets.UTF_8).length;
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newStringLenHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    inputC,
                    inputLen);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewStringLen", throwable);
        }
    }

    public static JsValue newObject(JsContext context) {
        requireSupported(context.nativeApi.newObjectHandle, "JS_NewObject");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newObjectHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewObject", throwable);
        }
    }

    public static JsValue newObjectProto(JsContext context, JsValue proto) {
        requireSupported(context.nativeApi.newObjectProtoHandle, "JS_NewObjectProto");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newObjectProtoHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    proto.value);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewObjectProto", throwable);
        }
    }

    public static JsValue newObjectClass(JsContext context, int classId) {
        requireSupported(context.nativeApi.newObjectClassHandle, "JS_NewObjectClass");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newObjectClassHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    classId);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewObjectClass", throwable);
        }
    }

    public static JsValue newArray(JsContext context) {
        requireSupported(context.nativeApi.newArrayHandle, "JS_NewArray");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newArrayHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewArray", throwable);
        }
    }

    public static JsValue newArrayFrom(JsContext context, JsValue[] values) {
        JsValue array = newArray(context);
        try {
            for (int i = 0; i < values.length; i++) {
                array.setPropertyUint32(i, values[i]);
            }
            return array;
        } catch (RuntimeException exception) {
            array.close();
            throw exception;
        }
    }

    public static JsValue newDate(JsContext context, double epochMs) {
        requireSupported(context.nativeApi.newDateHandle, "JS_NewDate");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newDateHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    epochMs);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewDate", throwable);
        }
    }

    public static JsValue newSymbol(JsContext context, String description, boolean global) {
        requireSupported(context.nativeApi.newSymbolHandle, "JS_NewSymbol");
        MemorySegment descriptionC = context.nativeApi.arena.allocateFrom(description);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newSymbolHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    descriptionC,
                    global);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewSymbol", throwable);
        }
    }

    public static JsValue newError(JsContext context) {
        requireSupported(context.nativeApi.newErrorHandle, "JS_NewError");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newErrorHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewError", throwable);
        }
    }

    public static JsValue nullValue(JsContext context) {
        MemorySegment result = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT);
        result.set(ValueLayout.JAVA_LONG, 0, 0L);
        result.set(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG.byteSize(), Tag.NULL);
        return new JsValue(context.nativeApi, context.contextPtr, result);
    }

    public static JsValue undefinedValue(JsContext context) {
        MemorySegment result = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT);
        result.set(ValueLayout.JAVA_LONG, 0, 0L);
        result.set(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG.byteSize(), Tag.UNDEFINED);
        return new JsValue(context.nativeApi, context.contextPtr, result);
    }

    public static JsValue uninitializedValue(JsContext context) {
        MemorySegment result = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT);
        result.set(ValueLayout.JAVA_LONG, 0, 0L);
        result.set(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG.byteSize(), Tag.UNINITIALIZED);
        return new JsValue(context.nativeApi, context.contextPtr, result);
    }

    public static JsValue newArrayBufferCopy(JsContext context, byte[] buffer) {
        requireSupported(context.nativeApi.newArrayBufferCopyHandle, "JS_NewArrayBufferCopy");
        MemorySegment bufferSegment = context.nativeApi.arena.allocateFrom(ValueLayout.JAVA_BYTE, buffer);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newArrayBufferCopyHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    bufferSegment,
                    (long) buffer.length);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewArrayBufferCopy", throwable);
        }
    }

    public static JsValue newArrayBuffer(JsContext context, MemorySegment buffer, long length, boolean isShared) {
        requireSupported(context.nativeApi.newArrayBufferHandle, "JS_NewArrayBuffer");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newArrayBufferHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    buffer,
                    length,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    isShared);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewArrayBuffer", throwable);
        }
    }

    public static JsValue newUint8Array(JsContext context, MemorySegment buffer, long length, boolean isShared) {
        requireSupported(context.nativeApi.newUint8ArrayHandle, "JS_NewUint8Array");
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newUint8ArrayHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    buffer,
                    length,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    isShared);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewUint8Array", throwable);
        }
    }

    public static JsValue newUint8ArrayCopy(JsContext context, byte[] buffer) {
        requireSupported(context.nativeApi.newUint8ArrayCopyHandle, "JS_NewUint8ArrayCopy");
        MemorySegment bufferSegment = context.nativeApi.arena.allocateFrom(ValueLayout.JAVA_BYTE, buffer);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newUint8ArrayCopyHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    bufferSegment,
                    (long) buffer.length);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewUint8ArrayCopy", throwable);
        }
    }

    public static JsValue newTypedArray(JsContext context, TypedArrayType type, JsValue[] args) {
        requireSupported(context.nativeApi.newTypedArrayHandle, "JS_NewTypedArray");
        MemorySegment argsSegment = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT, args.length);
        long stride = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
        for (int i = 0; i < args.length; i++) {
            MemorySegment slot = argsSegment.asSlice((long) i * stride, stride);
            MemorySegment.copy(args[i].value(), 0, slot, 0, stride);
        }
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newTypedArrayHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    type.value(),
                    args.length,
                    argsSegment);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewTypedArray", throwable);
        }
    }

    public static JsPromiseCapability newPromiseCapability(JsContext context) {
        requireSupported(context.nativeApi.newPromiseCapabilityHandle, "JS_NewPromiseCapability");
        MemorySegment resolvingFuncs = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT, 2);
        long stride = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
        try {
            MemorySegment promise = (MemorySegment) context.nativeApi.newPromiseCapabilityHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    resolvingFuncs);
            JsValue promiseValue = new JsValue(context.nativeApi, context.contextPtr, promise);
            JsValue resolveValue = new JsValue(context.nativeApi, context.contextPtr,
                    resolvingFuncs.asSlice(0, stride));
            JsValue rejectValue = new JsValue(context.nativeApi, context.contextPtr,
                    resolvingFuncs.asSlice(stride, stride));
            return new JsPromiseCapability(promiseValue, resolveValue, rejectValue);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewPromiseCapability", throwable);
        }
    }

    public static JsValue init(JsContext context, boolean value) {
        return newBool(context, value);
    }

    public static JsValue init(JsContext context, byte value) {
        return newInt32(context, value);
    }

    public static JsValue init(JsContext context, short value) {
        return newInt32(context, value);
    }

    public static JsValue init(JsContext context, int value) {
        return newInt32(context, value);
    }

    public static JsValue init(JsContext context, long value) {
        return newInt64(context, value);
    }

    public static JsValue init(JsContext context, float value) {
        return newFloat64(context, value);
    }

    public static JsValue init(JsContext context, double value) {
        return newFloat64(context, value);
    }

    public static JsValue init(JsContext context, String value) {
        return newStringLen(context, value);
    }

    public static JsValue init(JsContext context, JsValue value) {
        return value.dup();
    }

    public static JsValue initNull(JsContext context) {
        return nullValue(context);
    }

    public static JsValue init(JsContext context, Object value) {
        if (value == null) {
            return initNull(context);
        }
        if (value instanceof JsValue jsValue) {
            return init(context, jsValue);
        }
        if (value instanceof Boolean boolValue) {
            return init(context, boolValue.booleanValue());
        }
        if (value instanceof Byte byteValue) {
            return init(context, byteValue.byteValue());
        }
        if (value instanceof Short shortValue) {
            return init(context, shortValue.shortValue());
        }
        if (value instanceof Integer intValue) {
            return init(context, intValue.intValue());
        }
        if (value instanceof Long longValue) {
            return init(context, longValue.longValue());
        }
        if (value instanceof Float floatValue) {
            return init(context, floatValue.floatValue());
        }
        if (value instanceof Double doubleValue) {
            return init(context, doubleValue.doubleValue());
        }
        if (value instanceof String stringValue) {
            return init(context, stringValue);
        }
        throw new IllegalArgumentException(
                "Unsupported Java value type for JsValue.init: " + value.getClass().getName());
    }

    /**
     * Creates a standard host function using the default generic calling
     * convention.
     *
     * @param context  active JavaScript context that will own the function
     * @param callback Java callback invoked when the JS function is called
     * @param name     function name exposed to JavaScript
     * @param length   expected argument count used as JS function {@code length}
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(JsContext context, HostFunctionCallback callback, String name, int length) {
        return initHostFunction(context, callback, name, length, HostFunctionType.GENERIC, 0);
    }

    /**
     * Creates a host function with explicit QuickJS function type and magic value.
     * <p>
     * Use this overload when you need constructor/getter/setter/iterator behavior
     * or want
     * to pass a compact integer discriminator ({@code magic}) into the callback.
     *
     * @param context  active JavaScript context that will own the function
     * @param callback Java callback invoked when the JS function is called
     * @param name     function name exposed to JavaScript
     * @param length   expected argument count used as JS function {@code length}
     * @param cproto   QuickJS function type/calling convention
     * @param magic    integer value surfaced in callback {@code magic}
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(
            JsContext context,
            HostFunctionCallback callback,
            String name,
            int length,
            HostFunctionType cproto,
            int magic) {
        requireSupported(context.nativeApi.newCFunction2Handle, "JS_NewCFunction2");
        HostFunctionRegistration registration = new HostFunctionRegistration(context.nativeApi, callback, null, 0);
        MethodHandle dispatch = CFUNCTION_MAGIC_DISPATCH_HANDLE.bindTo(registration);
        MemorySegment callbackStub = context.createUpcallStub(
                dispatch,
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        context.retainCallbackRegistration(registration);
        MemorySegment nameC = context.nativeApi.arena.allocateFrom(name);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newCFunction2Handle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    callbackStub,
                    nameC,
                    length,
                    cproto.value(),
                    magic);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewCFunction2", throwable);
        }
    }

    /**
     * Creates a host function with explicit function type/magic and custom
     * prototype object.
     * <p>
     * Use this when you need function behavior like the previous overload but must
     * attach
     * a specific prototype object (for example, constructor customization
     * scenarios).
     *
     * @param context    active JavaScript context that will own the function
     * @param callback   Java callback invoked when the JS function is called
     * @param name       function name exposed to JavaScript
     * @param length     expected argument count used as JS function {@code length}
     * @param cproto     QuickJS function type/calling convention
     * @param magic      integer value surfaced in callback {@code magic}
     * @param protoValue custom prototype value assigned to the function
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(
            JsContext context,
            HostFunctionCallback callback,
            String name,
            int length,
            HostFunctionType cproto,
            int magic,
            JsValue protoValue) {
        requireSupported(context.nativeApi.newCFunction3Handle, "JS_NewCFunction3");
        HostFunctionRegistration registration = new HostFunctionRegistration(context.nativeApi, callback, null, 0);
        MethodHandle dispatch = CFUNCTION_MAGIC_DISPATCH_HANDLE.bindTo(registration);
        MemorySegment callbackStub = context.createUpcallStub(
                dispatch,
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        context.retainCallbackRegistration(registration);
        MemorySegment nameC = context.nativeApi.arena.allocateFrom(name);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newCFunction3Handle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    callbackStub,
                    nameC,
                    length,
                    cproto.value(),
                    magic,
                    protoValue.value());
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewCFunction3", throwable);
        }
    }

    /**
     * Creates a host function that captures data values and makes them available to
     * the callback.
     *
     * @param context  active JavaScript context that will own the function
     * @param callback Java callback invoked when the JS function is called
     * @param length   expected argument count used as JS function {@code length}
     * @param magic    integer value surfaced in callback {@code magic}
     * @param data     captured JavaScript values exposed via callback
     *                 {@code functionData}
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(
            JsContext context,
            HostFunctionCallback callback,
            int length,
            int magic,
            JsValue[] data) {
        requireSupported(context.nativeApi.newCFunctionDataHandle, "JS_NewCFunctionData");
        HostFunctionRegistration registration = new HostFunctionRegistration(context.nativeApi, callback, null,
                data.length);
        MethodHandle dispatch = CFUNCTION_DATA_DISPATCH_HANDLE.bindTo(registration);
        MemorySegment callbackStub = context.createUpcallStub(
                dispatch,
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        context.retainCallbackRegistration(registration);
        MemorySegment dataSegment = packArgs(context, data);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newCFunctionDataHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    callbackStub,
                    length,
                    magic,
                    data.length,
                    dataSegment);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewCFunctionData", throwable);
        }
    }

    /**
     * Creates a named host function with captured data values.
     *
     * @param context  active JavaScript context that will own the function
     * @param callback Java callback invoked when the JS function is called
     * @param name     function name exposed to JavaScript
     * @param length   expected argument count used as JS function {@code length}
     * @param magic    integer value surfaced in callback {@code magic}
     * @param data     captured JavaScript values exposed via callback
     *                 {@code functionData}
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(
            JsContext context,
            HostFunctionCallback callback,
            String name,
            int length,
            int magic,
            JsValue[] data) {
        requireSupported(context.nativeApi.newCFunctionData2Handle, "JS_NewCFunctionData2");
        HostFunctionRegistration registration = new HostFunctionRegistration(context.nativeApi, callback, null,
                data.length);
        MethodHandle dispatch = CFUNCTION_DATA_DISPATCH_HANDLE.bindTo(registration);
        MemorySegment callbackStub = context.createUpcallStub(
                dispatch,
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        context.retainCallbackRegistration(registration);
        MemorySegment nameC = context.nativeApi.arena.allocateFrom(name);
        MemorySegment dataSegment = packArgs(context, data);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newCFunctionData2Handle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    callbackStub,
                    nameC,
                    length,
                    magic,
                    data.length,
                    dataSegment);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewCFunctionData2", throwable);
        }
    }

    /**
     * Creates a closure-style host function with an opaque pointer.
     * <p>
     * Use this when callback state is represented by native/opaque memory and you
     * do not need
     * a custom finalizer.
     *
     * @param context  active JavaScript context that will own the function
     * @param callback Java callback invoked when the JS function is called
     * @param name     function name exposed to JavaScript
     * @param length   expected argument count used as JS function {@code length}
     * @param magic    integer value surfaced in callback {@code magic}
     * @param opaque   user-provided opaque pointer passed back to callback
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(
            JsContext context,
            HostFunctionCallback callback,
            String name,
            int length,
            int magic,
            MemorySegment opaque) {
        return initHostFunction(context, callback, name, length, magic, opaque, null);
    }

    /**
     * Creates a closure-style host function with opaque pointer and optional
     * finalizer.
     * <p>
     * Use this when callback state lives outside the Java object graph and requires
     * explicit
     * cleanup when QuickJS disposes the function object.
     *
     * @param context   active JavaScript context that will own the function
     * @param callback  Java callback invoked when the JS function is called
     * @param name      function name exposed to JavaScript
     * @param length    expected argument count used as JS function {@code length}
     * @param magic     integer value surfaced in callback {@code magic}
     * @param opaque    user-provided opaque pointer passed back to callback
     * @param finalizer optional finalizer for cleaning opaque resources, or
     *                  {@code null}
     * @return created JavaScript function value
     */
    public static JsValue initHostFunction(
            JsContext context,
            HostFunctionCallback callback,
            String name,
            int length,
            int magic,
            MemorySegment opaque,
            HostFunctionFinalizer finalizer) {
        requireSupported(context.nativeApi.newCClosureHandle, "JS_NewCClosure");
        HostFunctionRegistration registration = new HostFunctionRegistration(context.nativeApi, callback, finalizer, 0);
        MethodHandle callbackDispatch = CCLOSURE_DISPATCH_HANDLE.bindTo(registration);
        MemorySegment callbackStub = context.createUpcallStub(
                callbackDispatch,
                FunctionDescriptor.of(
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        QuickJsNative.JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        MemorySegment finalizerStub = MemorySegment.NULL;
        if (finalizer != null) {
            MethodHandle finalizerDispatch = CCLOSURE_FINALIZER_DISPATCH_HANDLE.bindTo(registration);
            finalizerStub = context.createUpcallStub(
                    finalizerDispatch,
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        }
        context.retainCallbackRegistration(registration);
        MemorySegment nameC = context.nativeApi.arena.allocateFrom(name);
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newCClosureHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    callbackStub,
                    nameC,
                    finalizerStub,
                    length,
                    magic,
                    opaque);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewCClosure", throwable);
        }
    }

    public MemorySegment value() {
        return value;
    }

    public boolean isFloat64() {
        return getTag() == Tag.FLOAT64;
    }

    public boolean isInt() {
        return getTag() == Tag.INT;
    }

    public boolean isNumber() {
        var tag = getTag();
        return tag == Tag.FLOAT64 || tag == Tag.INT;
    }

    public boolean isBigInt() {
        var tag = getTag();
        return tag == Tag.BIG_INT || tag == Tag.SHORT_BIG_INT;
    }

    public boolean isBool() {
        return getTag() == Tag.BOOL;
    }

    public boolean isNull() {
        return getTag() == Tag.NULL;
    }

    public boolean isUndefined() {
        return getTag() == Tag.UNDEFINED;
    }

    public boolean isException() {
        return getTag() == Tag.EXCEPTION;
    }

    public boolean isUninitialized() {
        return getTag() == Tag.UNINITIALIZED;
    }

    public boolean isString() {
        var tag = getTag();
        return tag == Tag.STRING || tag == Tag.STRING_ROPE;
    }

    public boolean isSymbol() {
        return getTag() == Tag.SYMBOL;
    }

    public boolean isObject() {
        return getTag() == Tag.OBJECT;
    }

    public boolean isModule() {
        return getTag() == Tag.MODULE;
    }

    public void resolveModule() {
        try {
            var result = (int) nativeApi.resolveModuleHandle.invokeExact(contextPtr, value);
            if (result < 0) {
                throw new IllegalStateException("Failed to resolve module");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ResolveModule", throwable);
        }
    }

    public boolean isArray() {
        try {
            return (boolean) nativeApi.isArrayHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsArray", throwable);
        }
    }

    public boolean isProxy() {
        try {
            return (boolean) nativeApi.isProxyHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsProxy", throwable);
        }
    }

    public boolean isDate() {
        try {
            return (boolean) nativeApi.isDateHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsDate", throwable);
        }
    }

    public boolean isPromise() {
        try {
            return (boolean) nativeApi.isPromiseHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsPromise", throwable);
        }
    }

    public boolean isError() {
        try {
            return (boolean) nativeApi.isErrorHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsError", throwable);
        }
    }

    public boolean isUncatchableError() {
        try {
            return (boolean) nativeApi.isUncatchableErrorHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsUncatchableError", throwable);
        }
    }

    public boolean isArrayBuffer() {
        try {
            return (boolean) nativeApi.isArrayBufferHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsArrayBuffer", throwable);
        }
    }

    public boolean isRegExp() {
        try {
            return (boolean) nativeApi.isRegExpHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsRegExp", throwable);
        }
    }

    public boolean isMap() {
        try {
            return (boolean) nativeApi.isMapHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsMap", throwable);
        }
    }

    public boolean isSet() {
        try {
            return (boolean) nativeApi.isSetHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsSet", throwable);
        }
    }

    public boolean isWeakRef() {
        try {
            return (boolean) nativeApi.isWeakRefHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsWeakRef", throwable);
        }
    }

    public boolean isWeakSet() {
        try {
            return (boolean) nativeApi.isWeakSetHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsWeakSet", throwable);
        }
    }

    public boolean isWeakMap() {
        try {
            return (boolean) nativeApi.isWeakMapHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsWeakMap", throwable);
        }
    }

    public boolean isDataView() {
        try {
            return (boolean) nativeApi.isDataViewHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsDataView", throwable);
        }
    }

    public boolean isFunction() {
        try {
            return (boolean) nativeApi.isFunctionHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsFunction", throwable);
        }
    }

    public boolean isConstructor() {
        try {
            return (boolean) nativeApi.isConstructorHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsConstructor", throwable);
        }
    }

    public void detachArrayBuffer() {
        try {
            nativeApi.detachArrayBufferHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DetachArrayBuffer", throwable);
        }
    }

    public Optional<MemorySegment> getArrayBuffer() {
        try {
            MemorySegment sizePtr = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment resultPtr = (MemorySegment) nativeApi.getArrayBufferHandle.invokeExact(contextPtr, sizePtr,
                    value);
            long size = sizePtr.get(ValueLayout.JAVA_LONG, 0);
            if (resultPtr.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            return Optional.of(resultPtr.reinterpret(size));

        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetArrayBuffer", throwable);
        }
    }

    public Optional<MemorySegment> getUint8Array() {
        try {
            MemorySegment sizePtr = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment resultPtr = (MemorySegment) nativeApi.getUint8ArrayHandle.invokeExact(contextPtr, sizePtr,
                    value);
            if (resultPtr.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            long size = sizePtr.get(ValueLayout.JAVA_LONG, 0);

            return Optional.of(resultPtr.reinterpret(size));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetUint8Array", throwable);
        }
    }

    public Optional<JsBuffer> getTypedArrayBuffer() {
        try {
            MemorySegment offsetPtr = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment lengthPtr = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment bytesPerElementPtr = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment bufferValueSegment = (MemorySegment) nativeApi.getTypedArrayBufferHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    offsetPtr,
                    lengthPtr,
                    bytesPerElementPtr);
            JsValue bufferValue = new JsValue(nativeApi, contextPtr, bufferValueSegment);
            if (bufferValue.isException()) {
                bufferValue.close();
                return Optional.empty();
            }
            long offset = offsetPtr.get(ValueLayout.JAVA_LONG, 0);
            long length = lengthPtr.get(ValueLayout.JAVA_LONG, 0);
            long bytesPerElement = bytesPerElementPtr.get(ValueLayout.JAVA_LONG, 0);
            return Optional.of(new JsBuffer(bufferValue, offset, length, bytesPerElement));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetTypedArrayBuffer", throwable);
        }
    }

    public Optional<TypedArrayType> getTypedArrayType() {
        try {
            int type = (int) nativeApi.getTypedArrayTypeHandle.invokeExact(value);
            if (type < 0) {
                return Optional.empty();
            }
            return TypedArrayType.fromNative(type);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetTypedArrayType", throwable);
        }
    }

    public boolean toBool() {
        try {
            int result = (int) nativeApi.toBoolHandle.invokeExact(contextPtr, value);
            if (result < 0) {
                throw new IllegalStateException("JS_ToBool failed");
            }
            return result != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToBool", throwable);
        }
    }

    public int toInt32() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_INT);
            int ret = (int) nativeApi.toInt32Handle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToInt32 failed");
            }
            return out.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToInt32", throwable);
        }
    }

    public long toUint32() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_INT);
            int ret = (int) nativeApi.toInt32Handle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToUint32 failed");
            }
            return Integer.toUnsignedLong(out.get(ValueLayout.JAVA_INT, 0));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToUint32", throwable);
        }
    }

    public long toInt64() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            int ret = (int) nativeApi.toInt64Handle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToInt64 failed");
            }
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToInt64", throwable);
        }
    }

    public long toIndex() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            int ret = (int) nativeApi.toIndexHandle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToIndex failed");
            }
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToIndex", throwable);
        }
    }

    public double toFloat64() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_DOUBLE);
            int ret = (int) nativeApi.toFloat64Handle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToFloat64 failed");
            }
            return out.get(ValueLayout.JAVA_DOUBLE, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToFloat64", throwable);
        }
    }

    public long toBigInt64() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            int ret = (int) nativeApi.toBigInt64Handle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToBigInt64 failed");
            }
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToBigInt64", throwable);
        }
    }

    public long toBigUint64() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            int ret = (int) nativeApi.toBigUint64Handle.invokeExact(contextPtr, out, value);
            if (ret != 0) {
                throw new IllegalStateException("JS_ToBigUint64 failed");
            }
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToBigUint64", throwable);
        }
    }

    public JsValue toNumber() {
        try {
            MemorySegment converted = (MemorySegment) nativeApi.toNumberHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, converted);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToNumber", throwable);
        }
    }

    public JsValue toStringValue() {
        try {
            MemorySegment converted = (MemorySegment) nativeApi.toStringHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, converted);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToString", throwable);
        }
    }

    public JsValue toObject() {
        try {
            MemorySegment converted = (MemorySegment) nativeApi.toObjectHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, converted);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToObject", throwable);
        }
    }

    public JsValue toPropertyKey() {
        try {
            MemorySegment converted = (MemorySegment) nativeApi.toPropertyKeyHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, converted);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToPropertyKey", throwable);
        }
    }

    public JsModuleDef toModuleDef() {
        try {
            return new JsModuleDef(nativeApi, MemorySegment.ofAddress(this.getU()));

        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetPropertyModuleDef", throwable);
        }
    }

    public JsValue getPropertyAtom(int atom) {
        try {
            MemorySegment result = (MemorySegment) nativeApi.getPropertyHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    atom);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetProperty", throwable);
        }
    }

    public JsValue getProperty(Atom atom) {
        return getPropertyAtom(atom.value());
    }

    public JsValue getProperty(String name) {
        return getPropertyStr(name);
    }

    public JsValue getProperty(int index) {
        return getPropertyUint32(index);
    }

    public JsValue getProperty(long index) {
        return getPropertyInt64(index);
    }

    public JsValue getPropertyStr(String name) {
        try {
            MemorySegment cName = nativeApi.arena.allocateFrom(name);
            MemorySegment result = (MemorySegment) nativeApi.getPropertyStrHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    cName);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetPropertyStr", throwable);
        }
    }

    public JsValue getPropertyUint32(int index) {
        try {
            MemorySegment result = (MemorySegment) nativeApi.getPropertyUint32Handle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    index);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetPropertyUint32", throwable);
        }
    }

    public JsValue getPropertyInt64(long index) {
        try {
            MemorySegment result = (MemorySegment) nativeApi.getPropertyInt64Handle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    index);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetPropertyInt64", throwable);
        }
    }

    public void setPropertyAtom(int atom, JsValue val) {
        try {
            int ret = (int) nativeApi.setPropertyHandle.invokeExact(contextPtr, value, atom, val.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetProperty failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetProperty", throwable);
        }
    }

    public void setProperty(Atom atom, JsValue val) {
        setPropertyAtom(atom.value(), val);
    }

    public void setProperty(String name, JsValue val) {
        setPropertyStr(name, val);
    }

    public void setProperty(int index, JsValue val) {
        setPropertyUint32(index, val);
    }

    public void setProperty(long index, JsValue val) {
        setPropertyInt64(index, val);
    }

    public void setPropertyStr(String name, JsValue val) {
        try {
            MemorySegment cName = nativeApi.arena.allocateFrom(name);
            int ret = (int) nativeApi.setPropertyStrHandle.invokeExact(contextPtr, value, cName, val.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetPropertyStr failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetPropertyStr", throwable);
        }
    }

    public void setPropertyUint32(int index, JsValue val) {
        try {
            int ret = (int) nativeApi.setPropertyUint32Handle.invokeExact(contextPtr, value, index, val.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetPropertyUint32 failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetPropertyUint32", throwable);
        }
    }

    public void setPropertyInt64(long index, JsValue val) {
        try {
            int ret = (int) nativeApi.setPropertyInt64Handle.invokeExact(contextPtr, value, index, val.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetPropertyInt64 failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetPropertyInt64", throwable);
        }
    }

    public boolean hasPropertyStr(String name) {
        try {
            MemorySegment cName = nativeApi.arena.allocateFrom(name);
            int atom = (int) nativeApi.newAtomHandle.invokeExact(contextPtr, cName);
            try {
                int ret = (int) nativeApi.hasPropertyHandle.invokeExact(contextPtr, value, atom);
                if (ret < 0) {
                    throw new IllegalStateException("JS_HasProperty failed");
                }
                return ret != 0;
            } finally {
                nativeApi.freeAtomHandle.invokeExact(contextPtr, atom);
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_HasProperty", throwable);
        }
    }

    public boolean deletePropertyStr(String name) {
        try {
            MemorySegment cName = nativeApi.arena.allocateFrom(name);
            int atom = (int) nativeApi.newAtomHandle.invokeExact(contextPtr, cName);
            try {
                int ret = (int) nativeApi.deletePropertyHandle.invokeExact(contextPtr, value, atom, 0);
                if (ret < 0) {
                    throw new IllegalStateException("JS_DeleteProperty failed");
                }
                return ret != 0;
            } finally {
                nativeApi.freeAtomHandle.invokeExact(contextPtr, atom);
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DeleteProperty", throwable);
        }
    }

    public JsValue getPrototype() {
        try {
            MemorySegment result = (MemorySegment) nativeApi.getPrototypeHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetPrototype", throwable);
        }
    }

    public void setPrototype(JsValue prototype) {
        try {
            int ret = (int) nativeApi.setPrototypeHandle.invokeExact(contextPtr, value, prototype.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetPrototype failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetPrototype", throwable);
        }
    }

    public void setConstructor(JsValue prototype) {
        try {
            int ret = (int) nativeApi.setConstructorHandle.invokeExact(contextPtr, value, prototype.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetConstructor failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetConstructor", throwable);
        }
    }

    public boolean setConstructorBit(boolean constructor) {
        try {
            return (boolean) nativeApi.setConstructorBitHandle.invokeExact(contextPtr, value, constructor);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetConstructorBit", throwable);
        }
    }

    public long getLength() {
        try {
            MemorySegment out = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
            int ret = (int) nativeApi.getLengthHandle.invokeExact(contextPtr, value, out);
            if (ret < 0) {
                throw new IllegalStateException("JS_GetLength failed");
            }
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetLength", throwable);
        }
    }

    public void setLength(long length) {
        try {
            int ret = (int) nativeApi.setLengthHandle.invokeExact(contextPtr, value, length);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetLength failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetLength", throwable);
        }
    }

    public boolean isExtensible() {
        try {
            int ret = (int) nativeApi.isExtensibleHandle.invokeExact(contextPtr, value);
            if (ret < 0) {
                throw new IllegalStateException("JS_IsExtensible failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsExtensible", throwable);
        }
    }

    public void preventExtensions() {
        try {
            int ret = (int) nativeApi.preventExtensionsHandle.invokeExact(contextPtr, value);
            if (ret < 0) {
                throw new IllegalStateException("JS_PreventExtensions failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_PreventExtensions", throwable);
        }
    }

    public void seal() {
        try {
            int ret = (int) nativeApi.sealObjectHandle.invokeExact(contextPtr, value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SealObject failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SealObject", throwable);
        }
    }

    public void freeze() {
        try {
            int ret = (int) nativeApi.freezeObjectHandle.invokeExact(contextPtr, value);
            if (ret < 0) {
                throw new IllegalStateException("JS_FreezeObject failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_FreezeObject", throwable);
        }
    }

    public boolean isEqual(JsValue other) {
        try {
            int ret = (int) nativeApi.isEqualHandle.invokeExact(contextPtr, value, other.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_IsEqual failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsEqual", throwable);
        }
    }

    public boolean isStrictEqual(JsValue other) {
        try {
            return (boolean) nativeApi.isStrictEqualHandle.invokeExact(contextPtr, value, other.value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsStrictEqual", throwable);
        }
    }

    public boolean isSameValue(JsValue other) {
        try {
            return (boolean) nativeApi.isSameValueHandle.invokeExact(contextPtr, value, other.value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsSameValue", throwable);
        }
    }

    public boolean isSameValueZero(JsValue other) {
        try {
            return (boolean) nativeApi.isSameValueZeroHandle.invokeExact(contextPtr, value, other.value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsSameValueZero", throwable);
        }
    }

    public boolean isInstanceOf(JsValue obj) {
        try {
            int ret = (int) nativeApi.isInstanceOfHandle.invokeExact(contextPtr, value, obj.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_IsInstanceOf failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsInstanceOf", throwable);
        }
    }

    public JsValue call(JsValue thisValue, JsValue[] args) {
        try {
            MemorySegment argsSegment = packArgs(args);
            MemorySegment result = (MemorySegment) nativeApi.callHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    thisValue.value,
                    args.length,
                    argsSegment);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_Call", throwable);
        }
    }

    public JsValue callConstructor(JsValue[] args) {
        try {
            MemorySegment argsSegment = packArgs(args);
            MemorySegment result = (MemorySegment) nativeApi.callConstructorHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    args.length,
                    argsSegment);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_CallConstructor", throwable);
        }
    }

    public JsValue callConstructor2(JsValue newTarget, JsValue[] args) {
        try {
            MemorySegment argsSegment = packArgs(args);
            MemorySegment result = (MemorySegment) nativeApi.callConstructor2Handle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    newTarget.value,
                    args.length,
                    argsSegment);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_CallConstructor2", throwable);
        }
    }

    public JsValue invoke(int methodAtom, JsValue[] args) {
        try {
            MemorySegment argsSegment = packArgs(args);
            MemorySegment result = (MemorySegment) nativeApi.invokeHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    methodAtom,
                    args.length,
                    argsSegment);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_Invoke", throwable);
        }
    }

    public JsValue jsonStringify(JsValue replacer, JsValue space) {
        try {
            MemorySegment result = (MemorySegment) nativeApi.jsonStringifyHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value,
                    replacer.value,
                    space.value);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_JSONStringify", throwable);
        }
    }

    public boolean defineProperty(int atom, JsValue val, JsValue getter, JsValue setter, int flags) {
        try {
            int ret = (int) nativeApi.definePropertyHandle.invokeExact(
                    contextPtr,
                    value,
                    atom,
                    val.value,
                    getter.value,
                    setter.value,
                    flags);
            if (ret < 0) {
                throw new IllegalStateException("JS_DefineProperty failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DefineProperty", throwable);
        }
    }

    public boolean definePropertyValue(int atom, JsValue val, int flags) {
        try {
            int ret = (int) nativeApi.definePropertyValueHandle.invokeExact(contextPtr, value, atom, val.value, flags);
            if (ret < 0) {
                throw new IllegalStateException("JS_DefinePropertyValue failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DefinePropertyValue", throwable);
        }
    }

    public boolean definePropertyValueUint32(int index, JsValue val, int flags) {
        try {
            int ret = (int) nativeApi.definePropertyValueUint32Handle.invokeExact(contextPtr, value, index, val.value,
                    flags);
            if (ret < 0) {
                throw new IllegalStateException("JS_DefinePropertyValueUint32 failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DefinePropertyValueUint32", throwable);
        }
    }

    public boolean definePropertyValueStr(String name, JsValue val, int flags) {
        try {
            MemorySegment cName = nativeApi.arena.allocateFrom(name);
            int ret = (int) nativeApi.definePropertyValueStrHandle.invokeExact(contextPtr, value, cName, val.value,
                    flags);
            if (ret < 0) {
                throw new IllegalStateException("JS_DefinePropertyValueStr failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DefinePropertyValueStr", throwable);
        }
    }

    public boolean definePropertyGetSet(int atom, JsValue getter, JsValue setter, int flags) {
        try {
            int ret = (int) nativeApi.definePropertyGetSetHandle.invokeExact(
                    contextPtr,
                    value,
                    atom,
                    getter.value,
                    setter.value,
                    flags);
            if (ret < 0) {
                throw new IllegalStateException("JS_DefinePropertyGetSet failed");
            }
            return ret != 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DefinePropertyGetSet", throwable);
        }
    }

    public OwnPropertyNames getOwnPropertyNames(int flags) {
        try {
            MemorySegment outTab = nativeApi.arena.allocate(ValueLayout.ADDRESS);
            MemorySegment outLen = nativeApi.arena.allocate(ValueLayout.JAVA_INT);
            int ret = (int) nativeApi.getOwnPropertyNamesHandle.invokeExact(
                    contextPtr,
                    outTab,
                    outLen,
                    value,
                    flags);
            if (ret < 0) {
                throw new IllegalStateException("JS_GetOwnPropertyNames failed");
            }
            MemorySegment entries = outTab.get(ValueLayout.ADDRESS, 0);
            int len = outLen.get(ValueLayout.JAVA_INT, 0);
            return new OwnPropertyNames(nativeApi, contextPtr, entries, len);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetOwnPropertyNames", throwable);
        }
    }

    public Optional<PropertyDescriptor> getOwnProperty(int atom) {
        try {
            MemorySegment desc = nativeApi.arena.allocate(56);
            int ret = (int) nativeApi.getOwnPropertyHandle.invokeExact(contextPtr, desc, value, atom);
            if (ret < 0) {
                throw new IllegalStateException("JS_GetOwnProperty failed");
            }
            if (ret == 0) {
                return Optional.empty();
            }
            int flags = desc.get(ValueLayout.JAVA_INT, 0);
            JsValue propValue = new JsValue(nativeApi, contextPtr, desc.asSlice(8, JS_VALUE_SIZE));
            JsValue getter = new JsValue(nativeApi, contextPtr, desc.asSlice(8 + JS_VALUE_SIZE, JS_VALUE_SIZE));
            JsValue setter = new JsValue(nativeApi, contextPtr, desc.asSlice(8 + JS_VALUE_SIZE * 2, JS_VALUE_SIZE));
            return Optional.of(new PropertyDescriptor(flags, propValue, getter, setter));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetOwnProperty", throwable);
        }
    }

    public void setPropertyFunctionList(MemorySegment functionList, int len) {
        try {
            int ret = (int) nativeApi.setPropertyFunctionListHandle.invokeExact(contextPtr, value, functionList, len);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetPropertyFunctionList failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetPropertyFunctionList", throwable);
        }
    }

    public void setPropertyFunctionList(CFunctionList functionList) {
        setPropertyFunctionList(functionList.entries(), functionList.length());
    }

    public JsValue getProxyTarget() {
        try {
            MemorySegment result = (MemorySegment) nativeApi.getProxyTargetHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetProxyTarget", throwable);
        }
    }

    public JsValue getProxyHandler() {
        try {
            MemorySegment result = (MemorySegment) nativeApi.getProxyHandlerHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetProxyHandler", throwable);
        }
    }

    public JsValue throwValue() {
        try {
            MemorySegment result = (MemorySegment) nativeApi.throwHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_Throw", throwable);
        }
    }

    public void setUncatchableError() {
        try {
            nativeApi.setUncatchableErrorHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetUncatchableError", throwable);
        }
    }

    public void clearUncatchableError() {
        try {
            nativeApi.clearUncatchableErrorHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ClearUncatchableError", throwable);
        }
    }

    public PromiseState promiseState() {
        try {
            int raw = (int) nativeApi.promiseStateHandle.invokeExact(contextPtr, value);
            return PromiseState.fromNative(raw);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_PromiseState", throwable);
        }
    }

    public JsValue promiseResult() {
        try {
            MemorySegment result = (MemorySegment) nativeApi.promiseResultHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_PromiseResult", throwable);
        }
    }

    public int getClassId() {
        try {
            return (int) nativeApi.getClassIdHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetClassID", throwable);
        }
    }

    public boolean setOpaque(MemorySegment opaquePtr) {
        try {
            int ret = (int) nativeApi.setOpaqueHandle.invokeExact(value, opaquePtr);
            return ret == 0;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetOpaque", throwable);
        }
    }

    public Optional<MemorySegment> getOpaque(int classId) {
        try {
            MemorySegment ptr = (MemorySegment) nativeApi.getOpaqueHandle.invokeExact(value, classId);
            if (ptr.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            return Optional.of(ptr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetOpaque", throwable);
        }
    }

    public Optional<MemorySegment> getOpaque(JsContext context, int classId) {
        try {
            MemorySegment ptr = (MemorySegment) nativeApi.getOpaque2Handle.invokeExact(context.contextPtr, value,
                    classId);
            if (ptr.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            return Optional.of(ptr);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetOpaque2", throwable);
        }
    }

    public AnyOpaque getAnyOpaque() {
        try {
            MemorySegment classIdOut = nativeApi.arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment ptr = (MemorySegment) nativeApi.getAnyOpaqueHandle.invokeExact(value, classIdOut);
            int classId = classIdOut.get(ValueLayout.JAVA_INT, 0);
            if (ptr.equals(MemorySegment.NULL)) {
                return new AnyOpaque(Optional.empty(), classId);
            }
            return new AnyOpaque(Optional.of(ptr), classId);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_GetAnyOpaque", throwable);
        }
    }

    public static JsValue parseJson(JsContext context, String json, String filename) {
        try {
            MemorySegment jsonC = context.nativeApi.arena.allocateFrom(json);
            MemorySegment filenameC = context.nativeApi.arena.allocateFrom(filename);
            MemorySegment result = (MemorySegment) context.nativeApi.parseJsonHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    jsonC,
                    (long) json.getBytes(StandardCharsets.UTF_8).length,
                    filenameC);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ParseJSON", throwable);
        }
    }

    public static JsValue newProxy(JsContext context, JsValue target, JsValue handler) {
        try {
            MemorySegment result = (MemorySegment) context.nativeApi.newProxyHandle.invokeExact(
                    (SegmentAllocator) context.nativeApi.arena,
                    context.contextPtr,
                    target.value,
                    handler.value);
            return new JsValue(context.nativeApi, context.contextPtr, result);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_NewProxy", throwable);
        }
    }

    private MemorySegment packArgs(JsValue[] args) {
        MemorySegment segment = nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT, args.length);
        long stride = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
        for (int i = 0; i < args.length; i++) {
            MemorySegment slot = segment.asSlice((long) i * stride, stride);
            MemorySegment.copy(args[i].value, 0, slot, 0, stride);
        }
        return segment;
    }

    private static MemorySegment packArgs(JsContext context, JsValue[] args) {
        MemorySegment segment = context.nativeApi.arena.allocate(QuickJsNative.JS_VALUE_LAYOUT, args.length);
        long stride = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
        for (int i = 0; i < args.length; i++) {
            MemorySegment slot = segment.asSlice((long) i * stride, stride);
            MemorySegment.copy(args[i].value(), 0, slot, 0, stride);
        }
        return segment;
    }

    private static JsValue[] unpackArgs(QuickJsNative nativeApi, MemorySegment contextPtr, int argc,
            MemorySegment argvPtr) {
        if (argc <= 0 || argvPtr.equals(MemorySegment.NULL)) {
            return new JsValue[0];
        }
        JsValue[] args = new JsValue[argc];
        long stride = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
        MemorySegment argsView = argvPtr.reinterpret((long) argc * stride);
        for (int i = 0; i < argc; i++) {
            MemorySegment arg = argsView.asSlice((long) i * stride, stride);
            args[i] = new JsValue(nativeApi, contextPtr, arg);
        }
        return args;
    }

    @SuppressWarnings("unused")
    private static MemorySegment cFunctionMagicDispatch(
            HostFunctionRegistration registration,
            MemorySegment callbackContext,
            MemorySegment callbackThis,
            int argc,
            MemorySegment callbackArgv,
            int magic) {
        return invokeHostFunctionCallback(
                registration,
                callbackContext,
                callbackThis,
                argc,
                callbackArgv,
                magic,
                MemorySegment.NULL,
                MemorySegment.NULL);
    }

    @SuppressWarnings("unused")
    private static MemorySegment cFunctionDataDispatch(
            HostFunctionRegistration registration,
            MemorySegment callbackContext,
            MemorySegment callbackThis,
            int argc,
            MemorySegment callbackArgv,
            int magic,
            MemorySegment callbackFuncData) {
        return invokeHostFunctionCallback(
                registration,
                callbackContext,
                callbackThis,
                argc,
                callbackArgv,
                magic,
                callbackFuncData,
                MemorySegment.NULL);
    }

    @SuppressWarnings("unused")
    private static MemorySegment cClosureDispatch(
            HostFunctionRegistration registration,
            MemorySegment callbackContext,
            MemorySegment callbackThis,
            int argc,
            MemorySegment callbackArgv,
            int magic,
            MemorySegment callbackOpaque) {
        return invokeHostFunctionCallback(
                registration,
                callbackContext,
                callbackThis,
                argc,
                callbackArgv,
                magic,
                MemorySegment.NULL,
                callbackOpaque);
    }

    @SuppressWarnings("unused")
    private static void cClosureFinalizerDispatch(HostFunctionRegistration registration, MemorySegment callbackOpaque) {
        if (registration.finalizer() != null) {
            registration.finalizer().finalizeOpaque(callbackOpaque);
        }
    }

    private static MemorySegment invokeHostFunctionCallback(
            HostFunctionRegistration registration,
            MemorySegment callbackContext,
            MemorySegment callbackThis,
            int argc,
            MemorySegment callbackArgv,
            Integer magic,
            MemorySegment callbackFuncData,
            MemorySegment callbackOpaque) {
        JsContext context = new JsContext(registration.nativeApi(), callbackContext, false);
        JsValue thisValue = new JsValue(registration.nativeApi(), callbackContext, callbackThis);
        JsValue[] args = unpackArgs(registration.nativeApi(), callbackContext, argc, callbackArgv);
        JsValue[] functionData = unpackArgs(
                registration.nativeApi(),
                callbackContext,
                registration.dataLength(),
                callbackFuncData);
        try {
            JsValue result = registration.callback().call(
                    context,
                    thisValue,
                    args,
                    magic,
                    functionData,
                    callbackOpaque);
            if (result == null) {
                return undefinedValue(context).value();
            }
            return result.value();
        } catch (Throwable throwable) {
            return context.throwInternalError("Java host function callback failed: " + throwable.getMessage()).value();
        }
    }

    public Optional<JsCString> toCStringLen() {
        ensureOpen();
        MemorySegment textLen = nativeApi.arena.allocate(ValueLayout.JAVA_LONG);
        MemorySegment textPtr;
        try {
            textPtr = (MemorySegment) nativeApi.toCStringLen2Handle.invokeExact(
                    contextPtr,
                    textLen,
                    value,
                    false);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToCStringLen2", throwable);
        }

        if (textPtr.equals(MemorySegment.NULL)) {
            return Optional.empty();
        }

        long len = textLen.get(ValueLayout.JAVA_LONG, 0);
        return Optional.of(new JsCString(textPtr, len));
    }

    public Optional<MemorySegment> toCString() {
        ensureOpen();
        MemorySegment textPtr;
        try {
            textPtr = (MemorySegment) nativeApi.toCStringLen2Handle.invokeExact(
                    contextPtr,
                    MemorySegment.NULL,
                    value,
                    false);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_ToCString", throwable);
        }

        if (textPtr.equals(MemorySegment.NULL)) {
            return Optional.empty();
        }
        return Optional.of(textPtr);
    }

    public JsValue dup() {
        requireSupported(nativeApi.dupValueHandle, "JS_DupValue");
        ensureOpen();
        try {
            MemorySegment duplicated = (MemorySegment) nativeApi.dupValueHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    contextPtr,
                    value);
            return new JsValue(nativeApi, contextPtr, duplicated);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DupValue", throwable);
        }
    }

    public JsValue dupRT(JsRuntime runtime) {
        requireSupported(nativeApi.dupValueRTHandle, "JS_DupValueRT");
        ensureOpen();
        try {
            MemorySegment duplicated = (MemorySegment) nativeApi.dupValueRTHandle.invokeExact(
                    (SegmentAllocator) nativeApi.arena,
                    runtime.runtimePtr,
                    value);
            return new JsValue(nativeApi, contextPtr, duplicated);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_DupValueRT", throwable);
        }
    }

    public void deinitRT(JsRuntime runtime) {
        requireSupported(nativeApi.freeValueRTHandle, "JS_FreeValueRT");
        if (closed) {
            return;
        }
        closed = true;
        try {
            nativeApi.freeValueRTHandle.invokeExact(runtime.runtimePtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_FreeValueRT", throwable);
        }
    }

    public String toJavaString() {
        Optional<JsCString> maybeCString = toCStringLen();
        if (maybeCString.isEmpty()) {
            throw new IllegalStateException("JS_ToCStringLen returned null");
        }

        JsCString cString = maybeCString.get();
        try {
            return new String(
                    cString.ptr().reinterpret(cString.len()).toArray(ValueLayout.JAVA_BYTE),
                    StandardCharsets.UTF_8);
        } finally {
            try {
                nativeApi.freeCStringHandle.invokeExact(contextPtr, cString.ptr());
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to call JS_FreeCString", throwable);
            }
        }
    }

    public Optional<String> toUtf8Slice() {
        Optional<JsCString> maybeCString = toCStringLen();
        if (maybeCString.isEmpty()) {
            return Optional.empty();
        }
        JsCString cString = maybeCString.get();
        try {
            return Optional.of(new String(
                    cString.ptr().reinterpret(cString.len()).toArray(ValueLayout.JAVA_BYTE),
                    StandardCharsets.UTF_8));
        } finally {
            try {
                nativeApi.freeCStringHandle.invokeExact(contextPtr, cString.ptr());
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to call JS_FreeCString", throwable);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            nativeApi.freeValueHandle.invokeExact(contextPtr, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_FreeValue", throwable);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("JsValue is already closed");
        }
    }

    private static void requireSupported(java.lang.invoke.MethodHandle handle, String name) {
        if (handle == null) {
            throw new UnsupportedOperationException(name + " is not available in this QuickJS build");
        }
    }

    private static MethodHandle bindDispatch(String methodName, MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(JsValue.class, methodName, type);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
