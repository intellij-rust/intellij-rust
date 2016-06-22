struct S {
    x: f32
}

fn bar() -> S {}

impl S {
    fn foo(&self) {
        let s = bar();

        s.<caret>x;
    }
}
