struct S {
    x: f32
}

impl S {
    fn foo(&self) {
        self.<caret>x;
    }
}
