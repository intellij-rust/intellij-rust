trait T {
    fn foo();
    fn eggs();
    fn bar() {}
    fn baz<T>(x: i32, y: String) -> Vec<T> where T: Clone;
    fn quux(&self) -> Self;
    fn annon_arg(Arg);
}

impl T for () {
    fn foo() {}
    fn eggs() {
        unimplemented!()
    }

    fn baz<T>(x: i32, y: String) -> Vec<T> where T: Clone {
        unimplemented!()
    }

    fn quux(&self) -> Self {
        unimplemented!()
    }

    fn annon_arg(_: Arg) {
        unimplemented!()
    }
}
