from rust_types import TYPE_TO_REGEX
from rust_types import RustType

# noinspection PyUnresolvedReferences
import lldb_formatters.lldb_providers
from lldb_formatters.lldb_providers import *


# noinspection DuplicatedCode
def __lldb_init_module(debugger, _dict):
    def register_providers(rust_type, summary=None, synth=None):
        regex = TYPE_TO_REGEX[rust_type].pattern

        if summary:
            func_name = summary.__name__
            debugger.HandleCommand(
                'type summary add -F lldb_formatters.lldb_providers.{} -e -x -h "{}" --category Rust'.format(func_name,
                                                                                                             regex)
            )
        if synth:
            class_name = synth.__name__
            debugger.HandleCommand(
                'type synthetic add -l lldb_formatters.lldb_providers.{} -x "{}" --category Rust'.format(class_name,
                                                                                                         regex)
            )

    register_providers(RustType.STR, summary=StdStrSummaryProvider)
    register_providers(RustType.MSVC_STR, summary=StdStrSummaryProvider)
    register_providers(RustType.MSVC_STR_DOLLAR, summary=StdStrSummaryProvider)

    register_providers(RustType.SLICE, summary=SizeSummaryProvider, synth=StdSliceSyntheticProvider)
    register_providers(RustType.MSVC_SLICE, summary=SizeSummaryProvider, synth=StdSliceSyntheticProvider)
    register_providers(RustType.MSVC_SLICE2, summary=SizeSummaryProvider, synth=StdSliceSyntheticProvider)

    register_providers(RustType.STRING, summary=StdStringSummaryProvider)
    register_providers(RustType.OS_STRING, summary=StdOsStringSummaryProvider)
    register_providers(RustType.OS_STR, summary=StdOsStrPathSummaryProvider)
    register_providers(RustType.PATH_BUF, summary=StdPathBufSummaryProvider)
    register_providers(RustType.PATH, summary=StdOsStrPathSummaryProvider)
    register_providers(RustType.CSTRING, summary=StdCStringSummaryProvider)
    register_providers(RustType.CSTR, summary=StdCStringSummaryProvider)

    register_providers(RustType.VEC, summary=SizeSummaryProvider, synth=StdVecSyntheticProvider)
    register_providers(RustType.VEC_DEQUE, summary=SizeSummaryProvider, synth=StdVecDequeSyntheticProvider)
    register_providers(RustType.HASH_MAP, summary=SizeSummaryProvider, synth=StdHashMapSyntheticProvider)
    register_providers(RustType.HASH_SET, summary=SizeSummaryProvider, synth=StdHashSetSyntheticProvider)

    register_providers(RustType.RC, summary=StdRcSummaryProvider, synth=StdRcSyntheticProvider)
    register_providers(RustType.RC_WEAK, summary=StdRcSummaryProvider, synth=StdRcSyntheticProvider)
    register_providers(RustType.ARC, summary=StdRcSummaryProvider, synth=StdArcSyntheticProvider)
    register_providers(RustType.ARC_WEAK, summary=StdRcSummaryProvider, synth=StdArcSyntheticProvider)

    register_providers(RustType.CELL, summary=None, synth=StdCellSyntheticProvider)
    register_providers(RustType.REF, summary=StdRefSummaryProvider, synth=StdRefSyntheticProvider)
    register_providers(RustType.REF_MUT, summary=StdRefSummaryProvider, synth=StdRefSyntheticProvider)
    register_providers(RustType.REF_CELL, summary=StdRefSummaryProvider, synth=StdRefCellSyntheticProvider)

    register_providers(RustType.NONZERO_NUMBER, summary=StdNonZeroNumberSummaryProvider)

    register_providers(RustType.RANGE, summary=StdRangeSummaryProvider)
    register_providers(RustType.RANGE_FROM, summary=StdRangeFromSummaryProvider)
    register_providers(RustType.RANGE_INCLUSIVE, summary=StdRangeInclusiveSummaryProvider)
    register_providers(RustType.RANGE_TO, summary=StdRangeToSummaryProvider)
    register_providers(RustType.RANGE_TO_INCLUSIVE, summary=StdRangeToInclusiveSummaryProvider)

    debugger.HandleCommand('type category enable Rust')
