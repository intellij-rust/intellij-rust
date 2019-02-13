import re


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
    STD_BTREE_SET = "StdBTreeSet"
    STD_BTREE_MAP = "StdBTreeMap"
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
STD_BTREE_SET_REGEX = re.compile(r"^(alloc::(\w+::)+)BTreeSet<.+>$")
STD_BTREE_MAP_REGEX = re.compile(r"^(alloc::(\w+::)+)BTreeMap<.+>$")
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


def classify_struct(name, fields):
    if len(fields) == 0:
        return RustType.EMPTY

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
    if re.match(STD_BTREE_SET_REGEX, name):
        return RustType.STD_BTREE_SET
    if re.match(STD_BTREE_MAP_REGEX, name):
        return RustType.STD_BTREE_MAP

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
