enum Foo<T: A + B + 'c,
    K: X + Y + 'z> {
    Some(T, i32, f32,
         T, i32, f32,
         K, i32, f32)
}

fn foo(x: i32,
       y: i32,
       z: i32) {
    let foo: (i32,
              i32,
              i32) = ();
}

fn foo(a: i32,
       b: i32,
       c: i32)
       -> i32
       where T: 'static {}

impl moo {
    pub fn with_bindings<R, F, I>(&mut self, bindings: I, f: F) -> R
        where F: FnOnce(&mut TypeContext<'a>) -> R,
              I: IntoIterator<Item = (&'a Ident, Type)> {}
}

fn main() {
    foo(123,
        456,
        789);

    write!(123,
           456,
           789);

    let foo = (123,
               456,
               789);

    match 1 {
        FooSome(1234, true,
                5678, false,
                91011, "foobar") => 10
    }

    let foo = moo.boo().goo()
        .foo().bar()
        .map(|x| x.foo().doo()
            .moo().boo()
            .bar().baz())
        .baz().moo();
}
