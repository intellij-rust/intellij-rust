struct Foo {
    bar: Bar
}

struct Bar {
    baz: i32
}

fn main() {
    let foo = Foo { bar: Bar { baz: 92 } };
    foo.bar.<caret>baz;
}
