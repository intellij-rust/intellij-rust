mod a {
    mod b {
        fn <caret>foo() {}

        fn bar() {
            foo()
        }
    }

    use self::b::foo;

    fn bar() {
        foo()
    }
}

fn foo() { }

fn bar() {
    foo()
}
