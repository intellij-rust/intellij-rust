struct Foo {
    /// Documented
    pub foo: i32
}

fn main() {
    let x = Foo { foo: 8 };
    x.fo<caret>o
}
