#[macro_use] extern crate foo;

enum Foo<T: A + B + 'c,
    K: X + Y + 'z> {
    Some(T, i32, f32,
         T, i32, f32,
         K, i32, f32)
}

fn foo(a: i32,
       b: i32,
       c: i32)
       -> i32
       where T: 'static,
             K: 'a {
    let foo: (i32,
              i32,
              i32) = ();
}

fn main() {
    foobar(123,
           456,
           789);

    let foo = (123,
               456,
               789);

    let foo = moo.boo().goo()
        .foo().bar()
        .map(|x| x.foo().doo()
            .moo().boo()
            .bar().baz())
        .baz().moo();

    let x = match func() { Ok(v) => v.unwrap_or(0), Err(_) => ()};
}
