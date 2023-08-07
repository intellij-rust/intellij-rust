enum E { Foo { a: i32, b: i32 } }
fn main() {
    let foo = E::Foo { a: 0, b: 0 };
    match foo {
        E::Foo { a, b } => {}
    }
}
