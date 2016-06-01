struct S;

impl S {
    fn bar(self) { }

    fn foo(self) {
        self.<caret>bar()
    }
}
