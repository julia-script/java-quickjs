package com.github.julia_script.quickjs;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class QuickJsNative {
    public static final int JS_EVAL_TYPE_GLOBAL = 0;
    public static final MemoryLayout JS_VALUE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("u"),
            ValueLayout.JAVA_LONG.withName("tag"));
    // boolean
    public static final long POINTER_BITS = ValueLayout.ADDRESS.byteSize() * 8;

    public static final boolean IS_NAN_BOXED = POINTER_BITS < 64;

    public final Arena arena;
    public final MethodHandle newRuntimeHandle;
    public final MethodHandle freeRuntimeHandle;
    public final MethodHandle newContextHandle;
    public final MethodHandle freeContextHandle;
    public final MethodHandle evalHandle;
    public final MethodHandle toCStringLen2Handle;
    public final MethodHandle freeCStringHandle;
    public final MethodHandle freeValueHandle;

    final MethodHandle resolveModuleHandle;

    final MethodHandle isArrayHandle;
    final MethodHandle isProxyHandle;

    final MethodHandle isDateHandle;
    final MethodHandle isPromiseHandle;
    final MethodHandle isErrorHandle;
    final MethodHandle isUncatchableErrorHandle;
    final MethodHandle isArrayBufferHandle;
    final MethodHandle isRegExpHandle;
    final MethodHandle isMapHandle;
    final MethodHandle isSetHandle;
    final MethodHandle isWeakRefHandle;
    final MethodHandle isWeakSetHandle;
    final MethodHandle isWeakMapHandle;
    final MethodHandle isDataViewHandle;
    final MethodHandle isFunctionHandle;
    final MethodHandle isConstructorHandle;

    final MethodHandle detachArrayBufferHandle;

    final MethodHandle getArrayBufferHandle;
    final MethodHandle getUint8ArrayHandle;
    final MethodHandle getTypedArrayBufferHandle;

    final MethodHandle getTypedArrayTypeHandle;
    final MethodHandle toBoolHandle;
    final MethodHandle toInt32Handle;
    final MethodHandle toInt64Handle;
    final MethodHandle toIndexHandle;
    final MethodHandle toFloat64Handle;
    final MethodHandle toBigInt64Handle;
    final MethodHandle toBigUint64Handle;
    final MethodHandle toNumberHandle;
    final MethodHandle toStringHandle;
    final MethodHandle toObjectHandle;
    final MethodHandle toPropertyKeyHandle;
    final MethodHandle newAtomHandle;
    final MethodHandle freeAtomHandle;
    final MethodHandle getPropertyHandle;
    final MethodHandle getPropertyStrHandle;
    final MethodHandle getPropertyUint32Handle;
    final MethodHandle getPropertyInt64Handle;
    final MethodHandle setPropertyHandle;
    final MethodHandle setPropertyStrHandle;
    final MethodHandle setPropertyUint32Handle;
    final MethodHandle setPropertyInt64Handle;
    final MethodHandle hasPropertyHandle;
    final MethodHandle deletePropertyHandle;
    final MethodHandle getPrototypeHandle;
    final MethodHandle setPrototypeHandle;
    final MethodHandle setConstructorHandle;
    final MethodHandle setConstructorBitHandle;
    final MethodHandle getLengthHandle;
    final MethodHandle setLengthHandle;
    final MethodHandle isExtensibleHandle;
    final MethodHandle preventExtensionsHandle;
    final MethodHandle sealObjectHandle;
    final MethodHandle freezeObjectHandle;
    final MethodHandle isEqualHandle;
    final MethodHandle isStrictEqualHandle;
    final MethodHandle isSameValueHandle;
    final MethodHandle isSameValueZeroHandle;
    final MethodHandle isInstanceOfHandle;
    final MethodHandle callHandle;
    final MethodHandle callConstructorHandle;
    final MethodHandle callConstructor2Handle;
    final MethodHandle invokeHandle;
    final MethodHandle parseJsonHandle;
    final MethodHandle jsonStringifyHandle;
    final MethodHandle newProxyHandle;
    final MethodHandle getProxyTargetHandle;
    final MethodHandle getProxyHandlerHandle;
    final MethodHandle throwHandle;
    final MethodHandle setUncatchableErrorHandle;
    final MethodHandle clearUncatchableErrorHandle;
    final MethodHandle promiseStateHandle;
    final MethodHandle promiseResultHandle;
    final MethodHandle getClassIdHandle;
    final MethodHandle newAtomLenHandle;
    final MethodHandle newAtomUInt32Handle;
    final MethodHandle dupAtomHandle;
    final MethodHandle atomToValueHandle;
    final MethodHandle atomToStringHandle;
    final MethodHandle valueToAtomHandle;
    final MethodHandle getExceptionHandle;
    final MethodHandle hasExceptionHandle;
    final MethodHandle throwOutOfMemoryHandle;
    final MethodHandle throwTypeErrorHandle;
    final MethodHandle throwSyntaxErrorHandle;
    final MethodHandle throwReferenceErrorHandle;
    final MethodHandle throwRangeErrorHandle;
    final MethodHandle throwInternalErrorHandle;
    final MethodHandle definePropertyHandle;
    final MethodHandle definePropertyValueHandle;
    final MethodHandle definePropertyValueUint32Handle;
    final MethodHandle definePropertyValueStrHandle;
    final MethodHandle definePropertyGetSetHandle;
    final MethodHandle getOwnPropertyNamesHandle;
    final MethodHandle getOwnPropertyHandle;
    final MethodHandle freePropertyEnumHandle;
    final MethodHandle setPropertyFunctionListHandle;
    final MethodHandle getOpaqueHandle;
    final MethodHandle getOpaque2Handle;
    final MethodHandle getAnyOpaqueHandle;
    final MethodHandle setOpaqueHandle;
    final MethodHandle newClassIdHandle;
    final MethodHandle newClassHandle;
    final MethodHandle isRegisteredClassHandle;
    final MethodHandle setClassProtoHandle;
    final MethodHandle getClassProtoHandle;

    public QuickJsNative() {
        this(resolveNativeLibraryPath());
    }

    public QuickJsNative(Path libraryPath) {
        this.arena = Arena.ofConfined();
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath.toAbsolutePath(), arena);
        Linker linker = Linker.nativeLinker();

        this.newRuntimeHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewRuntime"),
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.freeRuntimeHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreeRuntime"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.newContextHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewContext"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.freeContextHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreeContext"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.evalHandle = linker.downcallHandle(
                findRequired(lookup, "JS_Eval"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.toCStringLen2Handle = linker.downcallHandle(
                findRequired(lookup, "JS_ToCStringLen2"),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_BOOLEAN));
        this.freeCStringHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreeCString"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.freeValueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreeValue"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, JS_VALUE_LAYOUT));

        this.resolveModuleHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ResolveModule"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));

        this.isArrayHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsArray"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));

        this.isProxyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsProxy"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));

        this.isDateHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsDate"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isPromiseHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsPromise"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsError"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isUncatchableErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsUncatchableError"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isArrayBufferHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsArrayBuffer"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isRegExpHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsRegExp"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isMapHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsMap"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isSetHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsSet"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isWeakRefHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsWeakRef"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isWeakSetHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsWeakSet"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isWeakMapHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsWeakMap"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isDataViewHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsDataView"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        JS_VALUE_LAYOUT));
        this.isFunctionHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsFunction"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.isConstructorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsConstructor"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));

        this.detachArrayBufferHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DetachArrayBuffer"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, JS_VALUE_LAYOUT));

        this.getArrayBufferHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetArrayBuffer"),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS, // result pointer
                        ValueLayout.ADDRESS, // context
                        ValueLayout.ADDRESS, // size pointer
                        JS_VALUE_LAYOUT // JsValue
                ));
        this.getUint8ArrayHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetUint8Array"),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS, // result pointer
                        ValueLayout.ADDRESS, // context
                        ValueLayout.ADDRESS, // size pointer
                        JS_VALUE_LAYOUT // JsValue
                ));

        this.getTypedArrayBufferHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetTypedArrayBuffer"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));

                        // JS_GetTypedArrayType
        this.getTypedArrayTypeHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetTypedArrayType"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT));

        this.toBoolHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ToBool"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toInt32Handle = linker.downcallHandle(
                findRequired(lookup, "JS_ToInt32"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toInt64Handle = linker.downcallHandle(
                findRequired(lookup, "JS_ToInt64"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toIndexHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ToIndex"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toFloat64Handle = linker.downcallHandle(
                findRequired(lookup, "JS_ToFloat64"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toBigInt64Handle = linker.downcallHandle(
                findRequired(lookup, "JS_ToBigInt64"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toBigUint64Handle = linker.downcallHandle(
                findRequired(lookup, "JS_ToBigUint64"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toNumberHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ToNumber"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toStringHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ToString"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toObjectHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ToObject"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.toPropertyKeyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ToPropertyKey"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.newAtomHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewAtom"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.freeAtomHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreeAtom"),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.getPropertyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetProperty"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.getPropertyStrHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetPropertyStr"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS));
        this.getPropertyUint32Handle = linker.downcallHandle(
                findRequired(lookup, "JS_GetPropertyUint32"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.getPropertyInt64Handle = linker.downcallHandle(
                findRequired(lookup, "JS_GetPropertyInt64"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_LONG));
        this.setPropertyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetProperty"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT));
        this.setPropertyStrHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetPropertyStr"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.setPropertyUint32Handle = linker.downcallHandle(
                findRequired(lookup, "JS_SetPropertyUint32"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT));
        this.setPropertyInt64Handle = linker.downcallHandle(
                findRequired(lookup, "JS_SetPropertyInt64"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_LONG,
                        JS_VALUE_LAYOUT));
        this.hasPropertyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_HasProperty"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.deletePropertyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DeleteProperty"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT));
        this.getPrototypeHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetPrototype"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.setPrototypeHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetPrototype"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.setConstructorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetConstructor"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.setConstructorBitHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetConstructorBit"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_BOOLEAN));
        this.getLengthHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetLength"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS));
        this.setLengthHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetLength"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_LONG));
        this.isExtensibleHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsExtensible"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.preventExtensionsHandle = linker.downcallHandle(
                findRequired(lookup, "JS_PreventExtensions"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.sealObjectHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SealObject"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.freezeObjectHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreezeObject"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.isEqualHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsEqual"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.isStrictEqualHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsStrictEqual"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.isSameValueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsSameValue"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.isSameValueZeroHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsSameValueZero"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.isInstanceOfHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsInstanceOf"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.callHandle = linker.downcallHandle(
                findRequired(lookup, "JS_Call"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        this.callConstructorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_CallConstructor"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        this.callConstructor2Handle = linker.downcallHandle(
                findRequired(lookup, "JS_CallConstructor2"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        this.invokeHandle = linker.downcallHandle(
                findRequired(lookup, "JS_Invoke"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        this.parseJsonHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ParseJSON"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS));
        this.jsonStringifyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_JSONStringify"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.newProxyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewProxy"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT));
        this.getProxyTargetHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetProxyTarget"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.getProxyHandlerHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetProxyHandler"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.throwHandle = linker.downcallHandle(
                findRequired(lookup, "JS_Throw"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.setUncatchableErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetUncatchableError"),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.clearUncatchableErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ClearUncatchableError"),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.promiseStateHandle = linker.downcallHandle(
                findRequired(lookup, "JS_PromiseState"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.promiseResultHandle = linker.downcallHandle(
                findRequired(lookup, "JS_PromiseResult"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.getClassIdHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetClassID"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT));
        this.newAtomLenHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewAtomLen"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG));
        this.newAtomUInt32Handle = linker.downcallHandle(
                findRequired(lookup, "JS_NewAtomUInt32"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.dupAtomHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DupAtom"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.atomToValueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_AtomToValue"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.atomToStringHandle = linker.downcallHandle(
                findRequired(lookup, "JS_AtomToString"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.valueToAtomHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ValueToAtom"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT));
        this.getExceptionHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetException"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS));
        this.hasExceptionHandle = linker.downcallHandle(
                findRequired(lookup, "JS_HasException"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS));
        this.throwOutOfMemoryHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ThrowOutOfMemory"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS));
        this.throwTypeErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ThrowTypeError"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.throwSyntaxErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ThrowSyntaxError"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.throwReferenceErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ThrowReferenceError"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.throwRangeErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ThrowRangeError"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.throwInternalErrorHandle = linker.downcallHandle(
                findRequired(lookup, "JS_ThrowInternalError"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.definePropertyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DefineProperty"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.definePropertyValueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DefinePropertyValue"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.definePropertyValueUint32Handle = linker.downcallHandle(
                findRequired(lookup, "JS_DefinePropertyValueUint32"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.definePropertyValueStrHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DefinePropertyValueStr"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.definePropertyGetSetHandle = linker.downcallHandle(
                findRequired(lookup, "JS_DefinePropertyGetSet"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.getOwnPropertyNamesHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetOwnPropertyNames"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.getOwnPropertyHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetOwnProperty"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.freePropertyEnumHandle = linker.downcallHandle(
                findRequired(lookup, "JS_FreePropertyEnum"),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.setPropertyFunctionListHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetPropertyFunctionList"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.setOpaqueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetOpaque"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS));
        this.getOpaqueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetOpaque"),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.getOpaque2Handle = linker.downcallHandle(
                findRequired(lookup, "JS_GetOpaque2"),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.JAVA_INT));
        this.getAnyOpaqueHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetAnyOpaque"),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS));
        this.newClassIdHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewClassID"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.newClassHandle = linker.downcallHandle(
                findRequired(lookup, "JS_NewClass"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        this.isRegisteredClassHandle = linker.downcallHandle(
                findRequired(lookup, "JS_IsRegisteredClass"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
        this.setClassProtoHandle = linker.downcallHandle(
                findRequired(lookup, "JS_SetClassProto"),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        JS_VALUE_LAYOUT));
        this.getClassProtoHandle = linker.downcallHandle(
                findRequired(lookup, "JS_GetClassProto"),
                FunctionDescriptor.of(
                        JS_VALUE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));
    }

    public void closeArena() {
        arena.close();
    }

    private static MemorySegment findRequired(SymbolLookup lookup, String symbolName) {
        return lookup.find(symbolName)
                .orElseThrow(() -> new IllegalStateException("Missing symbol: " + symbolName));
    }

    private static Path resolveNativeLibraryPath() {
        String target = System.getProperty("quickjs.native.target", "macos-aarch64");
        String fileName;
        if (target.startsWith("macos-")) {
            fileName = "libquickjs.dylib";
        } else if (target.startsWith("linux-")) {
            fileName = "libquickjs.so";
        } else if (target.startsWith("windows-")) {
            fileName = "quickjs.dll";
        } else {
            throw new IllegalArgumentException("Unsupported quickjs.native.target: " + target);
        }

        String resourcePath = "natives/" + target + "/" + fileName;
        Path extracted = tryExtractBundledNative(resourcePath, fileName);
        if (extracted != null) {
            return extracted;
        }
        // Fallback for local development when resources are not yet staged.
        return Path.of("build", "native", target, fileName).toAbsolutePath();
    }

    private static Path tryExtractBundledNative(String resourcePath, String fileName) {
        ClassLoader classLoader = QuickJsNative.class.getClassLoader();
        try (InputStream resource = classLoader.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                return null;
            }
            Path tempDir = Files.createTempDirectory("javaquickjs-native-");
            Path extractedFile = tempDir.resolve(fileName);
            Files.copy(resource, extractedFile, StandardCopyOption.REPLACE_EXISTING);
            extractedFile.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            return extractedFile;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to extract native library resource: " + resourcePath, exception);
        }
    }
}
