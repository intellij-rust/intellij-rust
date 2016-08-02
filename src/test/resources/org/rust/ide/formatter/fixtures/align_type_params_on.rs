struct Foo<T: A + B + 'c,
    K: X + Y + 'z> {}

struct Foo<'x: 'y + 'z,
    K: X + Y + 'z> {}

struct Foo<T> where T: for<'a,
'b> Fn(i32) -> () {
a: T
}
