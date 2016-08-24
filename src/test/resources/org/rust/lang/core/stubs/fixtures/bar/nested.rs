pub fn hello() -> String { "Hello".to_owned() }


mod inner {
    fn foo() {
        struct S;

        trait T {
            fn bar() {}
        }
    }
}
