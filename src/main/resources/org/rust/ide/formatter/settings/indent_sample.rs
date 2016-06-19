struct Foo<
    T: A + B + 'c,
    K: X + Y + 'z> {
    x: T,
    y: K
}

impl Bar {
    #[allow(dead_code)]
    fn moo(func: F, a: i32,
           b: i32, c: i32)
           -> Bar
           where F: Fn(i32) -> i32 {
        Bar {
            abra: 1234,
            kada: "foo",
        }
    }

    fn bar() -> ! { panic!("bar"); }
}

fn main() {
    let mut foo = Foo(AB::new(), XY::new());

    foo.func("foo", "bar",
             "bar", "baz");

    'label: loop {
        foo.do_something();
        for i in 1..10 {
            foo.prepare_something_different();
            if let Some(_) = foo.get_something_different() {
                break 'label
            }
        }
    }
}
