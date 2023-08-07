import re


class RustType(object):
    OTHER = "Other"
    STRUCT = "Struct"
    TUPLE = "Tuple"
    CSTYLE_VARIANT = "CStyleVariant"
    TUPLE_VARIANT = "TupleVariant"
    STRUCT_VARIANT = "StructVariant"
    ENUM = "Enum"
    EMPTY = "Empty"
    SINGLETON_ENUM = "SingletonEnum"
    REGULAR_ENUM = "RegularEnum"
    COMPRESSED_ENUM = "CompressedEnum"
    REGULAR_UNION = "RegularUnion"

    STRING = "String"
    OS_STRING = "OsString"
    PATH_BUF = "PathBuf"
    STR = "Str"
    MSVC_STR = "StdMsvcStr"
    MSVC_STR_DOLLAR = "StdMsvcStrDollar"
    SLICE = "Slice"
    MSVC_SLICE = "MsvcSlice"
    MSVC_SLICE2 = "StdMsvcSlice2"
    OS_STR = "OsStr"
    PATH = "Path"
    CSTRING = "CString"
    CSTR = "CStr"
    VEC = "Vec"
    VEC_DEQUE = "VecDeque"
    BTREE_SET = "BTreeSet"
    BTREE_MAP = "BTreeMap"
    HASH_MAP = "HashMap"
    HASH_SET = "HashSet"
    RC = "Rc"
    RC_WEAK = "RcWeak"
    ARC = "Arc"
    ARC_WEAK = "ArcWeak"
    CELL = "Cell"
    REF = "Ref"
    REF_MUT = "RefMut"
    REF_CELL = "RefCell"
    NONZERO_NUMBER = "NonZeroNumber"
    RANGE = "Range"
    RANGE_FROM = "RangeFrom"
    RANGE_INCLUSIVE = "RangeInclusive"
    RANGE_TO = "RangeTo"
    RANGE_TO_INCLUSIVE = "RangeToInclusive"


TUPLE_ITEM_REGEX = re.compile(r"__\d+$")

ENCODED_ENUM_PREFIX = "RUST$ENCODED$ENUM$"
ENUM_DISR_FIELD_NAME = "<<variant>>"

TYPE_TO_REGEX = {
    # &str, &mut str, *const str, *mut str
    RustType.STR: re.compile(r"^(&|&mut |\*const |\*mut )str$"),

    # BACKCOMPAT: Rust 1.66
    # str, ptr_const$<str>, ptr_mut$<str>
    RustType.MSVC_STR: re.compile(r"^(str)|((ptr_const|ptr_mut)\$<str>)$"),

    # Since Rust 1.67 https://github.com/rust-lang/rust/pull/103691
    # str$, ref$<str$>, ref_mut$<str$>, ptr_const$<str$>, ptr_mut$<str$>
    RustType.MSVC_STR_DOLLAR: re.compile(r"^(str\$)|((ref|ref_mut|ptr_const|ptr_mut)\$<str\$>)$"),

    # &[T], &mut [T], *const [T], *mut [T], but not fixed-sized like &[T;size] because they have native representations
    RustType.SLICE: re.compile(r"^(&|&mut |\*const |\*mut )?\[[^;]+]$"),

    # BACKCOMPAT: Rust 1.66
    # slice$<T>, ptr_const$<slice$<T> >, ptr_mut$<slice$<T> >
    RustType.MSVC_SLICE: re.compile(r"^(slice\$<.+>)|((ptr_const|ptr_mut)\$<slice\$<.+> >)$"),

    # Since Rust 1.67 https://github.com/rust-lang/rust/pull/103691
    # slice2$<T>, ref$<slice2$<T> >, ref_mut$<slice2$<T> >, ptr_const$<slice2$<T> >, ptr_mut$<slice2$<T> >
    RustType.MSVC_SLICE2: re.compile(r"^(slice2\$<.+>)|((ref|ref_mut|ptr_const|ptr_mut)\$<slice2\$<.+> >?)$"),

    RustType.STRING: re.compile(r"^(alloc::([a-z_]+::)+)String$"),
    RustType.OS_STRING: re.compile(r"^(std::ffi::([a-z_]+::)+)OsString$"),
    RustType.OS_STR: re.compile(r"^((&|&mut )?std::ffi::([a-z_]+::)+)OsStr( \*)?$"),
    RustType.PATH_BUF: re.compile(r"^(std::([a-z_]+::)+)PathBuf$"),
    RustType.PATH: re.compile(r"^(&?std::([a-z_]+::)+)Path( \*)?$"),
    RustType.CSTRING: re.compile(r"^((std|alloc)::ffi::([a-z_]+::)+)CString$"),
    RustType.CSTR: re.compile(r"^(&?(std|core)::ffi::([a-z_]+::)+)CStr( \*)?$"),

    RustType.VEC: re.compile(r"^(alloc::([a-z_]+::)+)Vec<.+>$"),
    RustType.VEC_DEQUE: re.compile(r"^(alloc::([a-z_]+::)+)VecDeque<.+>$"),
    RustType.HASH_MAP: re.compile(r"^(std::collections::([a-z_]+::)+)HashMap<.+>$"),
    RustType.HASH_SET: re.compile(r"^(std::collections::([a-z_]+::)+)HashSet<.+>$"),
    RustType.BTREE_MAP: re.compile(r"^(alloc::([a-z_]+::)+)BTreeMap<.+>$"),
    RustType.BTREE_SET: re.compile(r"^(alloc::([a-z_]+::)+)BTreeSet<.+>$"),

    RustType.RC: re.compile(r"^alloc::rc::Rc<.+>$"),
    RustType.RC_WEAK: re.compile(r"^alloc::rc::Weak<.+>$"),
    RustType.ARC: re.compile(r"^alloc::(sync|arc)::Arc<.+>$"),
    RustType.ARC_WEAK: re.compile(r"^alloc::(sync|arc)::Weak<.+>$"),

    RustType.CELL: re.compile(r"^(core::([a-z_]+::)+)Cell<.+>$"),
    RustType.REF: re.compile(r"^(core::([a-z_]+::)+)Ref<.+>$"),
    RustType.REF_MUT: re.compile(r"^(core::([a-z_]+::)+)RefMut<.+>$"),
    RustType.REF_CELL: re.compile(r"^(core::([a-z_]+::)+)RefCell<.+>$"),

    RustType.NONZERO_NUMBER: re.compile(r"^core::num::([a-z_]+::)*NonZero.+$"),

    RustType.RANGE: re.compile(r"^core::ops::range::Range<.+>$"),
    RustType.RANGE_FROM: re.compile(r"^core::ops::range::RangeFrom<.+>$"),
    RustType.RANGE_INCLUSIVE: re.compile(r"^core::ops::range::RangeInclusive<.+>$"),
    RustType.RANGE_TO: re.compile(r"^core::ops::range::RangeTo<.+>$"),
    RustType.RANGE_TO_INCLUSIVE: re.compile(r"^core::ops::range::RangeToInclusive<.+>$"),
}


def is_tuple_fields(fields):
    # type: (list) -> bool
    return all(TUPLE_ITEM_REGEX.match(str(field.name)) for field in fields)


def classify_struct(name, fields):
    if len(fields) == 0:
        return RustType.EMPTY

    for ty, regex in TYPE_TO_REGEX.items():
        if regex.match(name):
            return ty

    if fields[0].name == ENUM_DISR_FIELD_NAME:
        return RustType.ENUM

    if is_tuple_fields(fields):
        return RustType.TUPLE

    return RustType.STRUCT


def classify_union(fields):
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
