enum E {
    Variant(i32, i32),
}


struct S {
    field: E
}

fn main() {
    let S { field: E::Variant(x, _) } = S { field : E::Variant(0, 0) };
    <caret>x;
}
