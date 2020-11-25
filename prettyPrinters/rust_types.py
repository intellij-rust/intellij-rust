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

    STD_STRING = "StdString"
    STD_OS_STRING = "StdOsString"
    STD_STR = "StdStr"
    STD_VEC = "StdVec"
    STD_VEC_DEQUE = "StdVecDeque"
    STD_BTREE_SET = "StdBTreeSet"
    STD_BTREE_MAP = "StdBTreeMap"
    STD_HASH_MAP = "StdHashMap"
    STD_HASH_SET = "StdHashSet"
    STD_RC = "StdRc"
    STD_ARC = "StdArc"
    STD_CELL = "StdCell"
    STD_REF = "StdRef"
    STD_REF_MUT = "StdRefMut"
    STD_REF_CELL = "StdRefCell"
    STD_NONZERO_NUMBER = "StdNonZeroNumber"
    STD_OPTION = "StdOption"


# Should be synchronized with `RsDebugProcessConfigurationHelper.RUST_STD_TYPES`
STD_STRING_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)String$")
STD_STR_REGEX = re.compile(r"^&str$")
STD_OS_STRING_REGEX = re.compile(r"^(std::ffi::([a-z_]+::)+)OsString$")
STD_VEC_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)Vec<.+>$")
STD_VEC_DEQUE_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)VecDeque<.+>$")
STD_BTREE_SET_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)BTreeSet<.+>$")
STD_BTREE_MAP_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)BTreeMap<.+>$")
STD_HASH_MAP_REGEX = re.compile(r"^(std::collections::([a-z_]+::)+)HashMap<.+>$")
STD_HASH_SET_REGEX = re.compile(r"^(std::collections::([a-z_]+::)+)HashSet<.+>$")
STD_RC_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)Rc<.+>$")
STD_ARC_REGEX = re.compile(r"^(alloc::([a-z_]+::)+)Arc<.+>$")
STD_CELL_REGEX = re.compile(r"^(core::([a-z_]+::)+)Cell<.+>$")
STD_REF_REGEX = re.compile(r"^(core::([a-z_]+::)+)Ref<.+>$")
STD_REF_MUT_REGEX = re.compile(r"^(core::([a-z_]+::)+)RefMut<.+>$")
STD_REF_CELL_REGEX = re.compile(r"^(core::([a-z_]+::)+)RefCell<.+>$")
STD_NONZERO_NUMBER_REGEX = re.compile(r"^core::num::([a-z_]+::)*NonZero.+$")
STD_OPTION_REGEX = re.compile(r"^core::option::Option<.+>(::None)?$")

TUPLE_ITEM_REGEX = re.compile(r"__\d+$")

ENCODED_ENUM_PREFIX = "RUST$ENCODED$ENUM$"
ENCODED_ENUM_DISCRIMINANT = "RUST$ENUM$DISR"
ENUM_DISR_FIELD_NAME = "<<variant>>"

STD_TYPE_TO_REGEX = {
    RustType.STD_STRING: STD_STRING_REGEX,
    RustType.STD_OS_STRING: STD_OS_STRING_REGEX,
    RustType.STD_STR: STD_STR_REGEX,
    RustType.STD_VEC: STD_VEC_REGEX,
    RustType.STD_VEC_DEQUE: STD_VEC_DEQUE_REGEX,
    RustType.STD_HASH_MAP: STD_HASH_MAP_REGEX,
    RustType.STD_HASH_SET: STD_HASH_SET_REGEX,
    RustType.STD_BTREE_SET: STD_BTREE_SET_REGEX,
    RustType.STD_BTREE_MAP: STD_BTREE_MAP_REGEX,
    RustType.STD_RC: STD_RC_REGEX,
    RustType.STD_ARC: STD_ARC_REGEX,
    RustType.STD_REF: STD_REF_REGEX,
    RustType.STD_REF_MUT: STD_REF_MUT_REGEX,
    RustType.STD_REF_CELL: STD_REF_CELL_REGEX,
    RustType.STD_CELL: STD_CELL_REGEX,
    RustType.STD_NONZERO_NUMBER: STD_NONZERO_NUMBER_REGEX,
    RustType.STD_OPTION: STD_OPTION_REGEX,
}

def is_tuple_fields(fields):
    # type: (list) -> bool
    return all(TUPLE_ITEM_REGEX.match(str(field.name)) for field in fields)


def classify_struct(name, fields):
    if len(fields) == 0:
        return RustType.EMPTY

    for ty, regex in STD_TYPE_TO_REGEX.items():
        if regex.match(name):
            return ty

    if fields[0].name == ENUM_DISR_FIELD_NAME:
        return RustType.ENUM

    if is_tuple_fields(fields):
        return RustType.TUPLE

    return RustType.STRUCT


def classify_union(name, fields):
    if len(fields) == 0:
        return RustType.EMPTY

    field_names = [field.name for field in fields]
    first_field_name = field_names[0]

    for ty, regex in STD_TYPE_TO_REGEX.items():
        if regex.match(name):
            return ty

    # for MSVC LLDB
    if ENCODED_ENUM_DISCRIMINANT in field_names:
        return RustType.COMPRESSED_ENUM
    elif first_field_name is not None and first_field_name.startswith(ENCODED_ENUM_PREFIX):
        return RustType.COMPRESSED_ENUM
    elif first_field_name is None:
        if len(fields) == 1:
            return RustType.SINGLETON_ENUM
        else:
            return RustType.REGULAR_ENUM
    else:
        return RustType.REGULAR_UNION
