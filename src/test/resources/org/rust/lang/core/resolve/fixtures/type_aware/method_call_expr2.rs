enum S { X }

impl S {
    fn bar(&self) {}
    fn foo(&self) {
        let s = S::X;

        s.<caret>bar();
    }
}
