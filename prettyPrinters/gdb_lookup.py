import gdb

from gdb_providers import *
from rust_types import *


def register_printers(objfile):
    objfile.pretty_printers.append(lookup)


def classify_rust_type(type):
    type_class = type.code
    if type_class == gdb.TYPE_CODE_STRUCT:
        return classify_struct(type.tag, type.fields())
    if type_class == gdb.TYPE_CODE_UNION:
        return classify_union(type.fields())

    return RustType.OTHER


def lookup(valobj):
    rust_type = classify_rust_type(valobj.type)

    if rust_type == RustType.STRUCT:
        return StructProvider(valobj)
    if rust_type == RustType.TUPLE:
        return TupleProvider(valobj)
    if rust_type == RustType.ENUM:
        return EnumProvider(valobj)

    if rust_type == RustType.STD_STRING:
        return StdStringProvider(valobj)
    if rust_type == RustType.STD_OS_STRING:
        return StdOsStringProvider(valobj)
    if rust_type == RustType.STD_STR:
        return StdStrProvider(valobj)

    if rust_type == RustType.STD_VEC:
        return StdVecProvider(valobj)
    if rust_type == RustType.STD_VEC_DEQUE:
        return StdVecDequeProvider(valobj)
    if rust_type == RustType.STD_BTREE_SET:
        return StdBTreeSetProvider(valobj)
    if rust_type == RustType.STD_BTREE_MAP:
        return StdBTreeMapProvider(valobj)
    if rust_type == RustType.STD_HASH_MAP:
        return StdHashMapProvider(valobj)
    if rust_type == RustType.STD_HASH_SET:
        return StdHashMapProvider(valobj["map"], show_values=False)

    if rust_type == RustType.STD_RC:
        return StdRcProvider(valobj)
    if rust_type == RustType.STD_ARC:
        return StdRcProvider(valobj, is_atomic=True)

    if rust_type == RustType.STD_CELL:
        return StdCellProvider(valobj)
    if rust_type == RustType.STD_REF:
        return StdRefProvider(valobj)
    if rust_type == RustType.STD_REF_MUT:
        return StdRefProvider(valobj)
    if rust_type == RustType.STD_REF_CELL:
        return StdRefCellProvider(valobj)

    return None
