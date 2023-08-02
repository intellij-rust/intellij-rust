struct Foo1(i32);
struct Foo2(i32, i32);
enum E {
    V1(i32),
    V2(i32, i32),
}
fn main() {
    Foo1(1);
    Foo2(2, 3);
    E::V1(4);
    E::V2(5, 6);
}
