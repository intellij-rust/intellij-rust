struct Foo<T> where T: for<'a, 'b> Fn(i32) -> () {
    a: T
}
