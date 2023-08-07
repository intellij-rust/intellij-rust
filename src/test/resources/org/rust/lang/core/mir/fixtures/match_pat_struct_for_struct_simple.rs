struct Foo { a: i32, b: i32 }
fn main() {
    let foo = Foo { a: 0, b: 0 };
    match foo {
        Foo { a, b } => {}
    }
}
