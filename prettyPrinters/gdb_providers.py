from sys import version_info

import gdb
from gdb import Value

if version_info[0] >= 3:
    xrange = range

ZERO_FIELD = "__0"
FIRST_FIELD = "__1"

class StructProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        self.fields = self.valobj.type.fields()

    def to_string(self):
        return self.valobj.type.name

    def children(self):
        for field in self.fields:
            yield field.name, self.valobj[field.name]


class TupleProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        self.fields = self.valobj.type.fields()

    def to_string(self):
        return "size={}".format(len(self.fields))

    def children(self):
        for i, field in enumerate(self.fields):
            yield str(i), self.valobj[field.name]

    @staticmethod
    def display_hint():
        return "array"


# BACKCOMPAT: 2020.3 (gdb <= 9.1)
class OldEnumProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        content = valobj[valobj.type.fields()[0]]
        fields = content.type.fields()
        self.empty = len(fields) == 0
        if not self.empty:
            if len(fields) == 1:
                discriminant = 0
            else:
                discriminant = int(content[fields[0]]) + 1
            self.active_variant = content[fields[discriminant]]
            self.name = fields[discriminant].name
            self.full_name = "{}::{}".format(valobj.type.name, self.name)
        else:
            self.full_name = valobj.type.name

    def to_string(self):
        return self.full_name

    def children(self):
        if not self.empty:
            yield self.name, self.active_variant


# gdb >= 10.1
class NewEnumProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        fields = valobj.type.fields()
        variant_field = None
        for field in fields:
            if not field.artificial:
                # The active variant is simply the first non-artificial field
                # https://github.com/bminor/binutils-gdb/blob/9c6a1327ad9a92b8584f0501dd25bf8ba9e84ac6/gdb/rust-lang.c#L93
                variant_field = field
        self.active_variant = valobj[variant_field]
        self.name = variant_field.name
        self.full_name = "{}::{}".format(valobj.type.name, self.name)

    def to_string(self):
        return self.full_name

    def children(self):
        yield self.name, self.active_variant


class StdStringProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        vec = valobj["vec"]
        self.length = int(vec["len"])
        self.data_ptr = vec["buf"]["ptr"]["pointer"]

    def to_string(self):
        return self.data_ptr.lazy_string(encoding="utf-8", length=self.length)

    @staticmethod
    def display_hint():
        return "string"


class StdOsStringProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        buf = self.valobj["inner"]["inner"]
        is_windows = "Wtf8Buf" in buf.type.name
        vec = buf[ZERO_FIELD] if is_windows else buf

        self.length = int(vec["len"])
        self.data_ptr = vec["buf"]["ptr"]["pointer"]

    def to_string(self):
        return self.data_ptr.lazy_string(encoding="utf-8", length=self.length)

    @staticmethod
    def display_hint():
        return "string"


class StdStrProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        self.length = int(valobj["length"])
        self.data_ptr = valobj["data_ptr"]

    def to_string(self):
        return self.data_ptr.lazy_string(encoding="utf-8", length=self.length)

    @staticmethod
    def display_hint():
        return "string"


class StdVecProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        self.length = int(valobj["len"])
        self.data_ptr = valobj["buf"]["ptr"]["pointer"]

    def to_string(self):
        return "size={}".format(self.length)

    def children(self):
        for index in xrange(self.length):
            yield "[{}]".format(index), (self.data_ptr + index).dereference()

    @staticmethod
    def display_hint():
        return "array"


class StdVecDequeProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj
        self.head = int(valobj["head"])
        self.tail = int(valobj["tail"])
        self.cap = int(valobj["buf"]["cap"])
        self.data_ptr = valobj["buf"]["ptr"]["pointer"]
        if self.head >= self.tail:
            self.size = self.head - self.tail
        else:
            self.size = self.cap + self.head - self.tail

    def to_string(self):
        return "size={}".format(self.size)

    def children(self):
        for index in xrange(0, self.size):
            yield "[{}]".format(index), (self.data_ptr + ((self.tail + index) % self.cap)).dereference()

    @staticmethod
    def display_hint():
        return "array"


class StdRcProvider:
    def __init__(self, valobj, is_atomic=False):
        # type: (Value, bool) -> None
        self.valobj = valobj
        self.ptr = valobj["ptr"]["pointer"]
        self.value = self.ptr["data" if is_atomic else "value"]
        self.strong = self.ptr["strong"]["v" if is_atomic else "value"]["value"]
        self.weak = self.ptr["weak"]["v" if is_atomic else "value"]["value"] - 1

    def to_string(self):
        return "strong={}, weak={}".format(int(self.strong), int(self.weak))

    def children(self):
        yield "value", self.value
        yield "strong", self.strong
        yield "weak", self.weak


class StdCellProvider:
    def __init__(self, valobj):
        self.value = valobj["value"]["value"]

    def children(self):
        yield "value", self.value


class StdRefProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.value = valobj["value"].dereference()
        self.borrow = valobj["borrow"]["borrow"]["value"]["value"]

    def to_string(self):
        borrow = int(self.borrow)
        return "borrow={}".format(borrow) if borrow >= 0 else "borrow_mut={}".format(-borrow)

    def children(self):
        yield "*value", self.value
        yield "borrow", self.borrow


class StdRefCellProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.value = valobj["value"]["value"]
        self.borrow = valobj["borrow"]["value"]["value"]

    def to_string(self):
        borrow = int(self.borrow)
        return "borrow={}".format(borrow) if borrow >= 0 else "borrow_mut={}".format(-borrow)

    def children(self):
        yield "value", self.value
        yield "borrow", self.borrow


class StdNonZeroNumberProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        fields = valobj.type.fields()
        assert len(fields) == 1
        field = list(fields)[0]
        self.value = str(valobj[field.name])

    def to_string(self):
        return self.value


# Yields children (in a provider's sense of the word) for a BTreeMap.
def children_of_btree_map(map):
    # Yields each key/value pair in the node and in any child nodes.
    def children_of_node(node_ptr, height):
        def cast_to_internal(node):
            # type: (Value) -> Value
            internal_type_name = node.type.target().name.replace("LeafNode", "InternalNode", 1)
            internal_type = gdb.lookup_type(internal_type_name)
            return node.cast(internal_type.pointer())

        # BACKCOMPAT: rust 1.49. Just drop this condition
        if node_ptr.type.name.startswith("alloc::collections::btree::node::BoxedNode<"):
            node_ptr = node_ptr["ptr"]

        node_ptr = node_ptr["pointer"]
        leaf = node_ptr.dereference()
        keys = leaf["keys"]
        vals = leaf["vals"]
        edges = cast_to_internal(node_ptr)["edges"] if height > 0 else None
        length = leaf["len"]

        for i in xrange(0, length + 1):
            if height > 0:
                child_ptr = edges[i]["value"]["value"]
                for child in children_of_node(child_ptr, height - 1):
                    yield child
            if i < length:
                # Avoid "Cannot perform pointer math on incomplete type" on zero-sized arrays.
                key = keys[i]["value"]["value"] if keys.type.sizeof > 0 else gdb.parse_and_eval("()")
                val = vals[i]["value"]["value"] if vals.type.sizeof > 0 else gdb.parse_and_eval("()")
                yield key, val

    if map["length"] > 0:
        root = map["root"]
        if root.type.name.startswith("core::option::Option<"):
            root = root.cast(gdb.lookup_type(root.type.name[21:-1]))
        node_ptr = root["node"]
        height = root["height"]
        for child in children_of_node(node_ptr, height):
            yield child


class StdBTreeSetProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj

    def to_string(self):
        return "size={}".format(self.valobj["map"]["length"])

    def children(self):
        inner_map = self.valobj["map"]
        for i, (child, _) in enumerate(children_of_btree_map(inner_map)):
            yield "[{}]".format(i), child

    @staticmethod
    def display_hint():
        return "array"


class StdBTreeMapProvider:
    def __init__(self, valobj):
        # type: (Value) -> None
        self.valobj = valobj

    def to_string(self):
        return "size={}".format(self.valobj["length"])

    def children(self):
        for i, (key, val) in enumerate(children_of_btree_map(self.valobj)):
            yield "key{}".format(i), key
            yield "val{}".format(i), val

    @staticmethod
    def display_hint():
        return "map"


class StdHashMapProvider:
    def __init__(self, valobj, show_values=True):
        # type: (Value, bool) -> None
        self.valobj = valobj
        self.show_values = show_values

        table = self.table()
        # BACKCOMPAT: rust 1.51. Just drop `else` branch
        if table.type.fields()[0].name == "table":
            inner_table = table["table"]
        else:
            inner_table = table

        capacity = int(inner_table["bucket_mask"]) + 1
        ctrl = inner_table["ctrl"]["pointer"]

        self.size = int(inner_table["items"])
        self.pair_type = table.type.template_argument(0).strip_typedefs()

        self.new_layout = "data" not in inner_table.type
        if self.new_layout:
            self.data_ptr = ctrl.cast(self.pair_type.pointer())
        else:
            self.data_ptr = inner_table["data"]["pointer"]

        self.valid_indices = []
        for idx in range(capacity):
            address = ctrl + idx
            value = address.dereference()
            is_presented = value & 128 == 0
            if is_presented:
                self.valid_indices.append(idx)

    def table(self):
        if self.show_values:
            hashbrown_hashmap = self.valobj["base"]
        elif self.valobj.type.fields()[0].name == "map":
            # BACKCOMPAT: rust 1.47
            # HashSet wraps std::collections::HashMap, which wraps hashbrown::HashMap
            hashbrown_hashmap = self.valobj["map"]["base"]
        else:
            # HashSet wraps hashbrown::HashSet, which wraps hashbrown::HashMap
            hashbrown_hashmap = self.valobj["base"]["map"]
        return hashbrown_hashmap["table"]

    def to_string(self):
        return "size={}".format(self.size)

    def children(self):
        pairs_start = self.data_ptr

        for index in range(self.size):
            idx = self.valid_indices[index]
            if self.new_layout:
                idx = -(idx + 1)
            element = (pairs_start + idx).dereference()
            if self.show_values:
                yield "key{}".format(index), element[ZERO_FIELD]
                yield "val{}".format(index), element[FIRST_FIELD]
            else:
                yield "[{}]".format(index), element[ZERO_FIELD]

    def display_hint(self):
        return "map" if self.show_values else "array"
