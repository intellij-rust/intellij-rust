from gdb import Type
from gdb import TYPE_CODE_STRUCT, TYPE_CODE_UNION, TYPE_CODE_INT

from gdb_formatters.gdb_providers import *
from rust_types import *


def register_printers(objfile):
    objfile.pretty_printers.append(lookup)


# Enum representation in gdb <= 9.1
def is_old_enum(valobj):
    content = valobj[valobj.type.fields()[0]]
    if content.type.code != TYPE_CODE_UNION:
        return False
    fields = content.type.fields()
    if len(fields) > 1:
        discriminant = int(content[fields[0]]) + 1
        if discriminant > len(fields):
            # invalid discriminant
            return False
    return True


# Enum representation in gdb >= 10.1
# Introduced in https://github.com/bminor/binutils-gdb/commit/9c6a1327ad9a92b8584f0501dd25bf8ba9e84ac6
def is_new_enum(type):
    fields = type.fields()
    if len(fields) > 1:
        field0 = fields[0]
        if field0.artificial and field0.name is None and field0.type.code == TYPE_CODE_INT:
            return True
    return False


def classify_rust_type(type):
    # type: (Type) -> RustType
    type_class = type.code
    if type_class == TYPE_CODE_STRUCT:
        if is_new_enum(type):
            return RustType.ENUM
        return classify_struct(type.tag, type.fields())
    if type_class == TYPE_CODE_UNION:
        return classify_union(type.fields())

    return RustType.OTHER


def lookup(valobj):
    # type: (Value) -> object
    rust_type = classify_rust_type(valobj.type)

    if rust_type == RustType.STRUCT:
        return StructProvider(valobj)
    if rust_type == RustType.TUPLE:
        return TupleProvider(valobj)
    if rust_type == RustType.ENUM:
        if is_old_enum(valobj):
            return OldEnumProvider(valobj)
        elif is_new_enum(valobj.type):
            return NewEnumProvider(valobj)

    if rust_type == RustType.STRING:
        return StdStringProvider(valobj)
    if rust_type == RustType.OS_STRING:
        return StdOsStringProvider(valobj)
    if rust_type == RustType.STR:
        return StdStrProvider(valobj)
    if rust_type == RustType.SLICE:
        return StdSliceProvider(valobj)

    if rust_type == RustType.VEC:
        return StdVecProvider(valobj)
    if rust_type == RustType.VEC_DEQUE:
        return StdVecDequeProvider(valobj)
    if rust_type == RustType.BTREE_SET:
        return StdBTreeSetProvider(valobj)
    if rust_type == RustType.BTREE_MAP:
        return StdBTreeMapProvider(valobj)
    if rust_type == RustType.HASH_MAP:
        return StdHashMapProvider(valobj)
    if rust_type == RustType.HASH_SET:
        return StdHashMapProvider(valobj, show_values=False)
    if rust_type == RustType.RC or rust_type == RustType.RC_WEAK:
        return StdRcProvider(valobj)
    if rust_type == RustType.ARC or rust_type == RustType.ARC_WEAK:
        return StdRcProvider(valobj, is_atomic=True)

    if rust_type == RustType.CELL:
        return StdCellProvider(valobj)
    if rust_type == RustType.REF:
        return StdRefProvider(valobj)
    if rust_type == RustType.REF_MUT:
        return StdRefProvider(valobj)
    if rust_type == RustType.REF_CELL:
        return StdRefCellProvider(valobj)

    if rust_type == RustType.NONZERO_NUMBER:
        return StdNonZeroNumberProvider(valobj)

    if rust_type == RustType.RANGE:
        return StdRangeProvider(valobj)
    if rust_type == RustType.RANGE_FROM:
        return StdRangeFromProvider(valobj)
    if rust_type == RustType.RANGE_INCLUSIVE:
        return StdRangeInclusiveProvider(valobj)
    if rust_type == RustType.RANGE_TO:
        return StdRangeToProvider(valobj)
    if rust_type == RustType.RANGE_TO_INCLUSIVE:
        return StdRangeToInclusiveProvider(valobj)

    return None
