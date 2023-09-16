struct Foo1 { a: i32 }
struct Foo2 { b: Foo1 }
fn main() {
    let foo2 = Foo2 { b: Foo1 { a: 0 } };
}
