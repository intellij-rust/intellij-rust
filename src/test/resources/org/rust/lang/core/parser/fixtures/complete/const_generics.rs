struct S<T, const N: i32, const M: &'static str>;
fn foo<T, const N: i32, const M: &'static str>() {}
fn main() { foo::<S<i32, 0, { x }>, -0, "">() }
