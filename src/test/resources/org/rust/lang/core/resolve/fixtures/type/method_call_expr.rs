struct S;

impl S {
    fn bar(&self) {}
    fn foo(&self) {
        let s: S = S;

        s.<caret>bar();
    }
}
