enum Foo {
    /// I am a well documented enum variant
    Bar { field: i32 },
    Baz(i32),
}


fn main() {
    let _ = Foo::<caret>Bar;
}
