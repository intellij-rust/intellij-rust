struct Foo<T> where T: for<'a, 'b> Fn(i32) -> () {
    a: T
}

struct Client<'a> {
    auth_fn: &'a for<'b> Fn(&'b str) -> Cow<'a, str>
}
