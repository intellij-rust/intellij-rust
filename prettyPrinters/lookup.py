import re

import lldb

from providers import *


class RustType:
    OTHER = "Other"
    STRUCT = "Struct"
    TUPLE = "Tuple"
    CSTYLE_VARIANT = "CStyleVariant"
    TUPLE_VARIANT = "TupleVariant"
    STRUCT_VARIANT = "StructVariant"
    EMPTY = "Empty"
    SINGLETON_ENUM = "SingletonEnum"
    REGULAR_ENUM = "RegularEnum"
    COMPRESSED_ENUM = "CompressedEnum"
    REGULAR_UNION = "RegularUnion"

    STD_STRING = "StdString"
    STD_OS_STRING = "StdOsString"
    STD_STR = "StdStr"
    STD_VEC = "StdVec"
    STD_VEC_DEQUE = "StdVecDeque"
    STD_RC = "StdRc"
    STD_ARC = "StdArc"
    STD_CELL = "StdCell"
    STD_REF = "StdRef"
    STD_REF_MUT = "StdRefMut"
    STD_REF_CELL = "StdRefCell"


STD_STRING_REGEX = re.compile(r"^(alloc::(\w+::)+)String$")
STD_STR_REGEX = re.compile(r"^&str$")
STD_OS_STRING_REGEX = re.compile(r"^(std::ffi::(\w+::)+)OsString$")
STD_VEC_REGEX = re.compile(r"^(alloc::(\w+::)+)Vec<.+>$")
STD_VEC_DEQUE_REGEX = re.compile(r"^(alloc::(\w+::)+)VecDeque<.+>$")
STD_RC_REGEX = re.compile(r"^(alloc::(\w+::)+)Rc<.+>$")
STD_ARC_REGEX = re.compile(r"^(alloc::(\w+::)+)Arc<.+>$")
STD_CELL_REGEX = re.compile(r"^(core::(\w+::)+)Cell<.+>$")
STD_REF_REGEX = re.compile(r"^(core::(\w+::)+)Ref<.+>$")
STD_REF_MUT_REGEX = re.compile(r"^(core::(\w+::)+)RefMut<.+>$")
STD_REF_CELL_REGEX = re.compile(r"^(core::(\w+::)+)RefCell<.+>$")

TUPLE_ITEM_REGEX = re.compile(r"__\d+$")

ENCODED_ENUM_PREFIX = "RUST$ENCODED$ENUM$"
ENUM_DISR_FIELD_NAME = "RUST$ENUM$DISR"


def is_tuple_fields(fields):
    # type: (list) -> bool
    return all(re.match(TUPLE_ITEM_REGEX, str(field.name)) for field in fields)


def classify_rust_type(type):
    # type: (SBType) -> str
    type_class = type.GetTypeClass()
    fields = type.fields

    if type_class == lldb.eTypeClassStruct:
        if len(fields) == 0:
            return RustType.EMPTY

        name = type.GetName()
        if re.match(STD_STRING_REGEX, name):
            return RustType.STD_STRING
        if re.match(STD_OS_STRING_REGEX, name):
            return RustType.STD_OS_STRING
        if re.match(STD_STR_REGEX, name):
            return RustType.STD_STR

        if re.match(STD_VEC_REGEX, name):
            return RustType.STD_VEC
        if re.match(STD_VEC_DEQUE_REGEX, name):
            return RustType.STD_VEC_DEQUE

        if re.match(STD_RC_REGEX, name):
            return RustType.STD_RC
        if re.match(STD_ARC_REGEX, name):
            return RustType.STD_ARC

        if re.match(STD_CELL_REGEX, name):
            return RustType.STD_CELL
        if re.match(STD_REF_REGEX, name):
            return RustType.STD_REF
        if re.match(STD_REF_MUT_REGEX, name):
            return RustType.STD_REF_MUT
        if re.match(STD_REF_CELL_REGEX, name):
            return RustType.STD_REF_CELL

        if fields[0].name == ENUM_DISR_FIELD_NAME:
            if len(fields) == 1:
                return RustType.CSTYLE_VARIANT
            if is_tuple_fields(fields[1:]):
                return RustType.TUPLE_VARIANT
            else:
                return RustType.STRUCT_VARIANT

        if is_tuple_fields(fields):
            return RustType.TUPLE

        else:
            return RustType.STRUCT

    if type_class == lldb.eTypeClassUnion:
        if len(fields) == 0:
            return RustType.EMPTY

        first_variant_name = fields[0].name
        if first_variant_name is None:
            if len(fields) == 1:
                return RustType.SINGLETON_ENUM
            else:
                return RustType.REGULAR_ENUM
        elif first_variant_name.startswith(ENCODED_ENUM_PREFIX):
            assert len(fields) == 1
            return RustType.COMPRESSED_ENUM
        else:
            return RustType.REGULAR_UNION

    return RustType.OTHER


def summary_lookup(valobj, dict):
    # type: (SBValue, dict) -> str
    """Returns the summary provider for the given value"""
    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STD_STRING:
        return StdStringSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_OS_STRING:
        return StdOsStringSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_STR:
        return StdStrSummaryProvider(valobj, dict)

    if rust_type == RustType.STD_VEC:
        return SizeSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_VEC_DEQUE:
        return SizeSummaryProvider(valobj, dict)

    if rust_type == RustType.STD_RC:
        return StdRcSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_ARC:
        return StdRcSummaryProvider(valobj, dict)

    if rust_type == RustType.STD_REF:
        return StdRefSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_REF_MUT:
        return StdRefSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_REF_CELL:
        return StdRefSummaryProvider(valobj, dict)

    return ""


def synthetic_lookup(valobj, dict):
    # type: (SBValue, dict) -> object
    """Returns the synthetic provider for the given value"""
    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STRUCT:
        return StructSyntheticProvider(valobj, dict)
    if rust_type == RustType.STRUCT_VARIANT:
        return StructSyntheticProvider(valobj, dict, is_variant=True)
    if rust_type == RustType.TUPLE:
        return TupleSyntheticProvider(valobj, dict)
    if rust_type == RustType.TUPLE_VARIANT:
        return TupleSyntheticProvider(valobj, dict, is_variant=True)
    if rust_type == RustType.EMPTY:
        return EmptySyntheticProvider(valobj, dict)
    if rust_type == RustType.REGULAR_ENUM:
        discriminant = valobj.GetChildAtIndex(0).GetChildAtIndex(0).GetValueAsUnsigned()
        return synthetic_lookup(valobj.GetChildAtIndex(discriminant), dict)
    if rust_type == RustType.SINGLETON_ENUM:
        return synthetic_lookup(valobj.GetChildAtIndex(0), dict)

    if rust_type == RustType.STD_VEC:
        return StdVecSyntheticProvider(valobj, dict)
    if rust_type == RustType.STD_VEC_DEQUE:
        return StdVecDequeSyntheticProvider(valobj, dict)

    if rust_type == RustType.STD_RC:
        return StdRcSyntheticProvider(valobj, dict)
    if rust_type == RustType.STD_ARC:
        return StdRcSyntheticProvider(valobj, dict, is_atomic=True)

    if rust_type == RustType.STD_CELL:
        return StdCellSyntheticProvider(valobj, dict)
    if rust_type == RustType.STD_REF:
        return StdRefSyntheticProvider(valobj, dict)
    if rust_type == RustType.STD_REF_MUT:
        return StdRefSyntheticProvider(valobj, dict)
    if rust_type == RustType.STD_REF_CELL:
        return StdRefSyntheticProvider(valobj, dict, is_cell=True)

    return DefaultSynthteticProvider(valobj, dict)
