struct S {
    x: f32
}

impl S {
    fn foo(&self) {
        let s = S { x: 0. };

        s.<caret>x;
    }
}
