package com.github.julia_script.quickjs;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class JsValue implements AutoCloseable {
    private static final long JS_VALUE_SIZE = QuickJsNative.JS_VALUE_LAYOUT.byteSize();
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

    public long getU() {
        return value.get(ValueLayout.JAVA_LONG, 0);
    }

    public long getTag() {
        return value.get(ValueLayout.JAVA_LONG, 8);
    }

    JsValue(QuickJsNative nativeApi, MemorySegment contextPtr, MemorySegment value) {
        this.nativeApi = nativeApi;
        this.contextPtr = contextPtr;
        this.value = value;
    }

    MemorySegment value() {
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
            return (boolean) nativeApi.isFunctionHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsFunction", throwable);
        }
    }

    public boolean isConstructor() {
        try {
            return (boolean) nativeApi.isConstructorHandle.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_IsConstructor", throwable);
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

    public JsValue getProperty(int atom) {
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

    public void setProperty(int atom, JsValue val) {
        try {
            int ret = (int) nativeApi.setPropertyHandle.invokeExact(contextPtr, value, atom, val.value);
            if (ret < 0) {
                throw new IllegalStateException("JS_SetProperty failed");
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to call JS_SetProperty", throwable);
        }
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
            int ret = (int) nativeApi.definePropertyValueUint32Handle.invokeExact(contextPtr, value, index, val.value, flags);
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
            int ret = (int) nativeApi.definePropertyValueStrHandle.invokeExact(contextPtr, value, cName, val.value, flags);
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

    public Optional<MemorySegment> getOpaque2(JsContext context, int classId) {
        try {
            MemorySegment ptr = (MemorySegment) nativeApi.getOpaque2Handle.invokeExact(context.contextPtr, value, classId);
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
}
