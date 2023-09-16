import sys

from lldb import LLDB_INVALID_ADDRESS
from lldb import SBValue, SBData, SBDebugger, SBError
from lldb import eBasicTypeLong, eBasicTypeUnsignedLong, eBasicTypeUnsignedChar, eBasicTypeChar

#################################################################################################################
# This file contains two kinds of pretty-printers: summary and synthetic.
#
# Important classes from LLDB module:
#   SBValue: the value of a variable, a register, or an expression
#   SBType:  the data type; each SBValue has a corresponding SBType
#
# Summary provider is a function with the type `(SBValue, dict) -> str`.
#   The first parameter is the object encapsulating the actual variable being displayed;
#   The second parameter is an internal support parameter used by LLDB, and you should not touch it.
#
# Synthetic children is the way to provide a children-based user-friendly representation of the object's value.
# Synthetic provider is a class that implements the following interface:
#
#     class SyntheticChildrenProvider:
#         def __init__(self, SBValue, dict)
#         def num_children(self)
#         def get_child_index(self, str)
#         def get_child_at_index(self, int)
#         def update(self)
#         def has_children(self)
#         def get_value(self)
#
#
# You can find more information and examples here:
#   1. https://lldb.llvm.org/varformats.html
#   2. https://lldb.llvm.org/python-reference.html
#   3. https://lldb.llvm.org/python_reference/lldb.formatters.cpp.libcxx-pysrc.html
#   4. https://github.com/llvm-mirror/lldb/tree/master/examples/summaries/cocoa
################################################################################################################

PY3 = sys.version_info[0] == 3
if PY3:
    from typing import Optional, List


def unwrap_unique_or_non_null(unique_or_nonnull):
    # type: (SBValue) -> SBValue
    """
    rust 1.33.0: struct Unique<T: ?Sized> { pointer: *const T, ... }
    rust 1.62.0: struct Unique<T: ?Sized> { pointer: NonNull<T>, ... }
    struct NonNull<T> { pointer: *const T }
    """
    ptr = unique_or_nonnull.GetChildMemberWithName("pointer")
    inner_ptr = ptr.GetChildMemberWithName("pointer")
    if inner_ptr.IsValid():
        return inner_ptr
    else:
        return ptr


def get_template_params(type_name):
    # type: (str) -> List[str]
    params = []
    level = 0
    start = 0
    for i, c in enumerate(type_name):
        if c == '<':
            level += 1
            if level == 1:
                start = i + 1
        elif c == '>':
            level -= 1
            if level == 0:
                params.append(type_name[start:i].strip())
        elif c == ',' and level == 1:
            params.append(type_name[start:i].strip())
            start = i + 1
    return params


def get_max_string_summary_length(debugger):
    # type: (SBDebugger) -> int
    debugger_name = debugger.GetInstanceName()
    max_len = SBDebugger.GetInternalVariableValue("target.max-string-summary-length", debugger_name)
    return int(max_len.GetStringAtIndex(0))


def read_raw_string(data_ptr, length):
    # type: (SBValue, int) -> str
    if data_ptr is None or length == 0:
        return '""'

    max_string_summary_length = get_max_string_summary_length(data_ptr.GetTarget().GetDebugger())
    length_to_read = min(length, max_string_summary_length)

    process = data_ptr.GetProcess()
    start = data_ptr.GetValueAsUnsigned()
    error = SBError()
    data = process.ReadMemory(start, length_to_read, error)
    data = data.decode(encoding='UTF-8') if PY3 else data

    return '"%s"' % data


def get_vec_data_ptr(valobj):
    # type: (SBValue) -> SBValue
    return unwrap_unique_or_non_null(valobj.GetChildMemberWithName("buf").GetChildMemberWithName("ptr"))


def get_vec_length(valobj):
    # type: (SBValue) -> int
    return valobj.GetChildMemberWithName("len").GetValueAsUnsigned()


def extract_data_and_len_from_ffi_string(valobj):
    # type: (SBValue) -> tuple[Optional[SBValue], int]
    process = valobj.GetProcess()
    error = SBError()
    slice_ptr = valobj.GetLoadAddress()
    if slice_ptr == LLDB_INVALID_ADDRESS:
        return None, 0
    char_ptr_type = valobj.GetTarget().GetBasicType(eBasicTypeChar).GetPointerType()
    data_ptr = valobj.CreateValueFromAddress('start', slice_ptr, char_ptr_type)
    length = process.ReadPointerFromMemory(slice_ptr + process.GetAddressByteSize(), error)
    return data_ptr, length


class ValueBuilder:
    def __init__(self, valobj):
        # type: (SBValue) -> None
        self.valobj = valobj
        process = valobj.GetProcess()
        self.endianness = process.GetByteOrder()
        self.pointer_size = process.GetAddressByteSize()

    def from_int(self, name, value):
        # type: (str, int) -> SBValue
        type = self.valobj.GetTarget().GetBasicType(eBasicTypeLong)
        data = SBData.CreateDataFromSInt64Array(self.endianness, self.pointer_size, [value])
        return self.valobj.CreateValueFromData(name, data, type)

    def from_uint(self, name, value):
        # type: (str, int) -> SBValue
        type = self.valobj.GetTarget().GetBasicType(eBasicTypeUnsignedLong)
        data = SBData.CreateDataFromUInt64Array(self.endianness, self.pointer_size, [value])
        return self.valobj.CreateValueFromData(name, data, type)


def SizeSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    return 'size=' + str(valobj.GetNumChildren())


def StdStringSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    vec_non_synth = valobj.GetChildAtIndex(0).GetNonSyntheticValue()
    data_ptr = get_vec_data_ptr(vec_non_synth)
    length = get_vec_length(vec_non_synth)
    return read_raw_string(data_ptr, length)


def StdOsStringSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    buf = valobj.GetChildAtIndex(0).GetChildAtIndex(0)
    is_windows = "Wtf8Buf" in buf.type.name
    vec = buf.GetChildAtIndex(0) if is_windows else buf
    vec_non_synth = vec.GetNonSyntheticValue()
    data_ptr = get_vec_data_ptr(vec_non_synth)
    length = get_vec_length(vec_non_synth)
    return read_raw_string(data_ptr, length)


def StdPathBufSummaryProvider(valobj, _dict):
    return StdOsStringSummaryProvider(valobj.GetChildAtIndex(0), _dict)


def StdStrSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    data_ptr = valobj.GetChildMemberWithName("data_ptr")
    length = valobj.GetChildMemberWithName("length").GetValueAsUnsigned()
    return read_raw_string(data_ptr, length)


def StdOsStrPathSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    data_ptr, length = extract_data_and_len_from_ffi_string(valobj)
    return read_raw_string(data_ptr, length)


def StdCStringSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    data_ptr, length = extract_data_and_len_from_ffi_string(valobj)
    return read_raw_string(data_ptr, length - 1)


class ArrayLikeSyntheticProviderBase:
    def __init__(self, valobj, _dict):
        # type: (SBValue, dict) -> None
        self.valobj = valobj
        self.update()

    def get_data_ptr(self):
        # type: () -> SBValue
        pass

    def get_length(self):
        # type: () -> int
        pass

    def num_children(self):
        # type: () -> int
        return self.length

    def get_child_index(self, name):
        # type: (str) -> int
        index = name.lstrip('[').rstrip(']')
        if index.isdigit():
            return int(index)
        else:
            return -1

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        offset = index * self.element_type_size
        return self.data_ptr.CreateChildAtOffset("[%s]" % index, offset, self.element_type)

    def update(self):
        # type: () -> None
        self.data_ptr = self.get_data_ptr()
        self.length = self.get_length()
        self.element_type = self.data_ptr.GetType().GetPointeeType()
        self.element_type_size = self.element_type.GetByteSize()

    def has_children(self):
        # type: () -> bool
        return True


class StdSliceSyntheticProvider(ArrayLikeSyntheticProviderBase):
    def get_data_ptr(self):
        # type: () -> SBValue
        return self.valobj.GetChildMemberWithName("data_ptr")

    def get_length(self):
        # type: () -> int
        return self.valobj.GetChildMemberWithName("length").GetValueAsUnsigned()


class StdVecSyntheticProvider(ArrayLikeSyntheticProviderBase):
    """Pretty-printer for alloc::vec::Vec<T>

    struct Vec<T> { buf: RawVec<T>, len: usize }
    struct RawVec<T> { ptr: Unique<T>, cap: usize, ... }
    rust 1.33.0: struct Unique<T: ?Sized> { pointer: *const T, ... }
    rust 1.62.0: struct Unique<T: ?Sized> { pointer: NonNull<T>, ... }
    struct NonNull<T> { pointer: *const T }
    """

    def get_data_ptr(self):
        # type: () -> SBValue
        return get_vec_data_ptr(self.valobj)

    def get_length(self):
        # type: () -> int
        return get_vec_length(self.valobj)


class StdVecDequeSyntheticProvider:
    """Pretty-printer for alloc::collections::vec_deque::VecDeque<T>

    struct VecDeque<T> { tail: usize, head: usize, buf: RawVec<T> }
    """

    def __init__(self, valobj, _dict):
        # type: (SBValue, dict) -> None
        self.valobj = valobj
        self.update()

    def num_children(self):
        # type: () -> int
        return self.size

    def get_child_index(self, name):
        # type: (str) -> int
        index = name.lstrip('[').rstrip(']')
        if index.isdigit() and self.tail <= int(index) and (self.tail + int(index)) % self.cap < self.head:
            return int(index)
        else:
            return -1

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        start = self.data_ptr.GetValueAsUnsigned()
        address = start + ((index + self.tail) % self.cap) * self.element_type_size
        element = self.data_ptr.CreateValueFromAddress("[%s]" % index, address, self.element_type)
        return element

    def update(self):
        # type: () -> None
        self.head = self.valobj.GetChildMemberWithName("head").GetValueAsUnsigned()
        self.tail = self.valobj.GetChildMemberWithName("tail").GetValueAsUnsigned()
        self.buf = self.valobj.GetChildMemberWithName("buf")
        self.cap = self.buf.GetChildMemberWithName("cap").GetValueAsUnsigned()
        self.size = self.head - self.tail if self.head >= self.tail else self.cap + self.head - self.tail
        self.data_ptr = unwrap_unique_or_non_null(self.buf.GetChildMemberWithName("ptr"))
        self.element_type = self.data_ptr.GetType().GetPointeeType()
        self.element_type_size = self.element_type.GetByteSize()

    def has_children(self):
        # type: () -> bool
        return True


class StdHashMapSyntheticProvider:
    """Pretty-printer for hashbrown's HashMap"""

    def __init__(self, valobj, _dict, show_values=True):
        # type: (SBValue, dict, bool) -> None
        self.valobj = valobj
        self.show_values = show_values
        self.update()

    def num_children(self):
        # type: () -> int
        return self.size

    def get_child_index(self, name):
        # type: (str) -> int
        index = name.lstrip('[').rstrip(']')
        if index.isdigit():
            return int(index)
        else:
            return -1

    def get_child_at_index(self, index):
        # type: (int) -> SBValue
        pairs_start = self.data_ptr.GetValueAsUnsigned()
        idx = self.valid_indices[index]
        if self.new_layout:
            idx = -(idx + 1)
        address = pairs_start + idx * self.pair_type_size
        element = self.data_ptr.CreateValueFromAddress("[%s]" % index, address, self.pair_type)
        if self.show_values:
            return element
        else:
            key = element.GetChildAtIndex(0)
            return self.valobj.CreateValueFromData("[%s]" % index, key.GetData(), key.GetType())

    def update(self):
        # type: () -> None
        table = self.table()
        inner_table = table.GetChildMemberWithName("table")

        capacity = inner_table.GetChildMemberWithName("bucket_mask").GetValueAsUnsigned() + 1
        ctrl = inner_table.GetChildMemberWithName("ctrl").GetChildAtIndex(0)

        self.size = inner_table.GetChildMemberWithName("items").GetValueAsUnsigned()

        if table.type.GetNumberOfTemplateArguments() > 0:
            self.pair_type = table.type.template_args[0].GetTypedefedType()
        else:
            # MSVC LLDB (does not support template arguments at the moment)
            type_name = table.type.name  # expected "RawTable<tuple$<K,V>,alloc::alloc::Global>"
            first_template_arg = get_template_params(type_name)[0]
            self.pair_type = table.GetTarget().FindTypes(first_template_arg).GetTypeAtIndex(0)

        self.pair_type_size = self.pair_type.GetByteSize()

        self.new_layout = not inner_table.GetChildMemberWithName("data").IsValid()
        if self.new_layout:
            self.data_ptr = ctrl.Cast(self.pair_type.GetPointerType())
        else:
            self.data_ptr = inner_table.GetChildMemberWithName("data").GetChildAtIndex(0)

        u8_type = self.valobj.GetTarget().GetBasicType(eBasicTypeUnsignedChar)
        u8_type_size = self.valobj.GetTarget().GetBasicType(eBasicTypeUnsignedChar).GetByteSize()

        self.valid_indices = []
        for idx in range(capacity):
            address = ctrl.GetValueAsUnsigned() + idx * u8_type_size
            value = ctrl.CreateValueFromAddress("ctrl[%s]" % idx, address,
                                                u8_type).GetValueAsUnsigned()
            is_present = value & 128 == 0
            if is_present:
                self.valid_indices.append(idx)

    def table(self):
        # type: () -> SBValue
        if self.show_values:
            hashbrown_hashmap = self.valobj.GetChildMemberWithName("base")
        else:
            # HashSet wraps `hashbrown::HashSet`, which wraps `hashbrown::HashMap`
            hashbrown_hashmap = self.valobj.GetChildAtIndex(0).GetChildAtIndex(0)
        return hashbrown_hashmap.GetChildMemberWithName("table")

    def has_children(self):
        # type: () -> bool
        return True


class StdHashSetSyntheticProvider(StdHashMapSyntheticProvider):
    def __init__(self, valobj, _dict):
        super().__init__(valobj, _dict, show_values=False)


def StdRcSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    strong = valobj.GetChildMemberWithName("strong").GetValueAsUnsigned()
    weak = valobj.GetChildMemberWithName("weak").GetValueAsUnsigned()
    return "strong={}, weak={}".format(strong, weak)


class StdRcSyntheticProvider:
    """Pretty-printer for alloc::rc::Rc<T> and alloc::sync::Arc<T>

    struct Rc<T> { ptr: NonNull<RcBox<T>>, ... }
    struct NonNull<T> { pointer: *const T }
    struct RcBox<T> { strong: Cell<usize>, weak: Cell<usize>, value: T }
    struct Cell<T> { value: UnsafeCell<T> }
    struct UnsafeCell<T> { value: T }

    struct Arc<T> { ptr: NonNull<ArcInner<T>>, ... }
    struct ArcInner<T> { strong: atomic::AtomicUsize, weak: atomic::AtomicUsize, data: T }
    struct AtomicUsize { v: UnsafeCell<usize> }
    """

    def __init__(self, valobj, _dict, is_atomic=False):
        # type: (SBValue, dict, bool) -> None
        self.valobj = valobj
        self.ptr = unwrap_unique_or_non_null(self.valobj.GetChildMemberWithName("ptr"))
        self.value = self.ptr.GetChildMemberWithName("data" if is_atomic else "value")
        self.strong = self.ptr.GetChildMemberWithName("strong").GetChildAtIndex(0).GetChildMemberWithName("value")
        self.weak = self.ptr.GetChildMemberWithName("weak").GetChildAtIndex(0).GetChildMemberWithName("value")
        self.value_builder = ValueBuilder(valobj)
        self.update()

    def num_children(self):
        # type: () -> int
        # Actually there are 3 children, but only the `value` should be shown as a child
        return 1

    def get_child_index(self, name):
        # type: (str) -> int
        if name == "value":
            return 0
        if name == "strong":
            return 1
        if name == "weak":
            return 2
        return -1

    def get_child_at_index(self, index):
        # type: (int) -> Optional[SBValue]
        if index == 0:
            return self.value
        if index == 1:
            return self.value_builder.from_uint("strong", self.strong_count)
        if index == 2:
            return self.value_builder.from_uint("weak", self.weak_count)

        return None

    def update(self):
        # type: () -> None
        self.strong_count = self.strong.GetValueAsUnsigned()
        self.weak_count = self.weak.GetValueAsUnsigned() - 1

    def has_children(self):
        # type: () -> bool
        return True


class StdArcSyntheticProvider(StdRcSyntheticProvider):
    def __init__(self, valobj, _dict):
        super().__init__(valobj, _dict, is_atomic=True)


class StdCellSyntheticProvider:
    """Pretty-printer for std::cell::Cell"""

    def __init__(self, valobj, _dict):
        # type: (SBValue, dict) -> None
        self.valobj = valobj
        self.value = valobj.GetChildMemberWithName("value").GetChildAtIndex(0)

    def num_children(self):
        # type: () -> int
        return 1

    def get_child_index(self, name):
        # type: (str) -> int
        if name == "value":
            return 0
        return -1

    def get_child_at_index(self, index):
        # type: (int) -> Optional[SBValue]
        if index == 0:
            return self.value
        return None

    def update(self):
        # type: () -> None
        pass

    def has_children(self):
        # type: () -> bool
        return True


def StdRefSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    borrow = valobj.GetChildMemberWithName("borrow").GetValueAsSigned()
    return "borrow={}".format(borrow) if borrow >= 0 else "borrow_mut={}".format(-borrow)


class StdRefSyntheticProvider:
    """Pretty-printer for std::cell::Ref, std::cell::RefMut, and std::cell::RefCell"""

    def __init__(self, valobj, _dict, is_cell=False):
        # type: (SBValue, dict, bool) -> None
        self.valobj = valobj

        borrow = valobj.GetChildMemberWithName("borrow")
        value = valobj.GetChildMemberWithName("value")
        if is_cell:
            self.borrow = borrow.GetChildMemberWithName("value").GetChildMemberWithName("value")
            self.value = value.GetChildMemberWithName("value")
        else:
            self.borrow = borrow.GetChildMemberWithName("borrow").GetChildMemberWithName(
                "value").GetChildMemberWithName("value")
            # BACKCOMPAT: Rust 1.62.0. Drop `else`-branch
            if value.GetChildMemberWithName("pointer"):
                # Since Rust 1.63.0, `Ref` and `RefMut` use `value: NonNull<T>` instead of `value: &T`
                # https://github.com/rust-lang/rust/commit/d369045aed63ac8b9de1ed71679fac9bb4b0340a
                # https://github.com/rust-lang/rust/commit/2b8041f5746bdbd7c9f6ccf077544e1c77e927c0
                self.value = unwrap_unique_or_non_null(value).Dereference()
            else:
                self.value = value.Dereference()

        self.value_builder = ValueBuilder(valobj)

        self.update()

    def num_children(self):
        # type: () -> int
        # Actually there are 2 children, but only the `value` should be shown as a child
        return 1

    def get_child_index(self, name):
        if name == "value":
            return 0
        if name == "borrow":
            return 1
        return -1

    def get_child_at_index(self, index):
        # type: (int) -> Optional[SBValue]
        if index == 0:
            return self.value
        if index == 1:
            return self.value_builder.from_int("borrow", self.borrow_count)
        return None

    def update(self):
        # type: () -> None
        self.borrow_count = self.borrow.GetValueAsSigned()

    def has_children(self):
        # type: () -> bool
        return True


class StdRefCellSyntheticProvider(StdRefSyntheticProvider):
    def __init__(self, valobj, _dict):
        super().__init__(valobj, _dict, is_cell=True)


def StdNonZeroNumberSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    objtype = valobj.GetType()
    field = objtype.GetFieldAtIndex(0)
    element = valobj.GetChildMemberWithName(field.name)
    return element.GetValue()


def StdRangeSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    return "{}..{}".format(valobj.GetChildMemberWithName("start").GetValueAsSigned(),
                           valobj.GetChildMemberWithName("end").GetValueAsSigned())


def StdRangeFromSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    return "{}..".format(valobj.GetChildMemberWithName("start").GetValueAsSigned())


def StdRangeInclusiveSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    return "{}..={}".format(valobj.GetChildMemberWithName("start").GetValueAsSigned(),
                            valobj.GetChildMemberWithName("end").GetValueAsSigned())


def StdRangeToSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    return "..{}".format(valobj.GetChildMemberWithName("end").GetValueAsSigned())


def StdRangeToInclusiveSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    return "..={}".format(valobj.GetChildMemberWithName("end").GetValueAsSigned())
