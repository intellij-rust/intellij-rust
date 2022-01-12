struct Foo<T1, T2, T3> { x: T1, y: T2, z: T3 }

fn new_foo<T1, T2, T3>(x: T1, y: T2, z: T3) -> Foo<T1, T2, T3> {
    Foo { x, y, z }
}

fn main() {
    let foo = new_foo(1, "abc", true);
}
