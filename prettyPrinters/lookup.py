import lldb
import re

from providers import *


class RustType:
    OTHER = 0
    STD_VEC = 1
    STD_STRING = 2
    STD_STR = 3


STD_VEC_REGEX = re.compile(r"^(alloc::([a-zA-Z]+::)+)Vec<.+>$")
STD_STRING_REGEX = re.compile(r"^(alloc::([a-zA-Z]+::)+)String$")
STD_STR_REGEX = re.compile(r"^&str$")


def classify_rust_type(type):
    # type: (SBType) -> int
    type_class = type.GetTypeClass()

    if type_class == lldb.eTypeClassStruct:
        name = type.GetName()
        if re.match(STD_VEC_REGEX, name):
            return RustType.STD_VEC
        if re.match(STD_STRING_REGEX, name):
            return RustType.STD_STRING
        if re.match(STD_STR_REGEX, name):
            return RustType.STD_STR

    return RustType.OTHER


def summary_lookup(valobj, dict):
    # type: (SBValue, dict) -> str
    """Returns the summary provider for the given value"""
    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STD_VEC:
        return SizeSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_STRING:
        return StdStringSummaryProvider(valobj, dict)
    if rust_type == RustType.STD_STR:
        return StdStrSummaryProvider(valobj, dict)

    return ""


def synthetic_lookup(valobj, dict):
    # type: (SBValue, dict) -> object
    """Returns the synthetic provider for the given value"""
    rust_type = classify_rust_type(valobj.GetType())

    if rust_type == RustType.STD_VEC:
        return StdVecSyntheticProvider(valobj, dict)

    return DefaultSynthteticProvider(valobj, dict)
