from lldb import SBType
from lldb import eTypeClassStruct, eTypeClassUnion, eTypeClassPointer

from lldb_providers import *
from rust_types import RustType, classify_struct, classify_union, classify_pointer


def classify_rust_type(type):
    # type: (SBType) -> RustType
    type_class = type.GetTypeClass()
    if type_class == eTypeClassStruct:
        return classify_struct(type.name, type.fields)
    if type_class == eTypeClassUnion:
        return classify_union(type.fields)
    if type_class == eTypeClassPointer:
        return classify_pointer(type.name)

    return RustType.OTHER


def summary_lookup(valobj, dict):
    # type: (SBValue, dict) -> str
    """Returns the summary provider for the given value"""

    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STD_STRING:
        return StdStringSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_OS_STRING:
        return StdOsStringSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_PATH_BUF:
        return StdOsStringSummaryProvider(valobj.GetChildAtIndex(0), dict)
    if rust_type == RustType.STD_STR:
        return StdStrSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_OS_STR:
        return StdFFIStrSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_PATH:
        return StdFFIStrSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_CSTRING:
        return StdFFIStrSummaryProvider(valobj, dict, is_null_terminated=True)
    if rust_type == RustType.STD_CSTR:
        return StdFFIStrSummaryProvider(valobj, dict, is_null_terminated=True)

    if rust_type == RustType.STD_VEC:
        return SizeSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_VEC_DEQUE:
        return SizeSummaryProvider(valobj, dict)

    if rust_type == RustType.STD_HASH_MAP:
        return SizeSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_HASH_SET:
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

    if rust_type == RustType.STD_NONZERO_NUMBER:
        return StdNonZeroNumberSummaryProvider(valobj, dict)

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

    if rust_type == RustType.STD_HASH_MAP:
        return StdHashMapSyntheticProvider(valobj, dict)
    if rust_type == RustType.STD_HASH_SET:
        return StdHashMapSyntheticProvider(valobj, dict, show_values=False)
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

    return DefaultSyntheticProvider(valobj, dict)
