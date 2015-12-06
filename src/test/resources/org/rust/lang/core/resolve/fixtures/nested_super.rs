mod foo {
    mod bar {
        fn main() {
            self::super::super::f<caret>oo()
        }
    }
}

fn foo() {}
