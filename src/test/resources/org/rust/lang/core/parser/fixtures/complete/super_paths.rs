fn foo() {}

mod a {
    mod b {
        fn bar() {
            use super::super::foo;
            use self::super::super::foo as bar;

            super::super::foo();
            self::super::super::foo();
        }
    }
}
