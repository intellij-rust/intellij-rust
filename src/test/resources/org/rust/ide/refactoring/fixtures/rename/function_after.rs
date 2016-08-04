mod a {
    mod b {
        fn spam() {}

        fn bar() {
            spam()
        }
    }

    use self::b::spam;

    fn bar() {
        spam()
    }
}

fn foo() { }

fn bar() {
    foo()
}
