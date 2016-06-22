struct S {
    x: f32
}

impl S {
    fn foo(&self) {
        let (_, s) = ((), S { x: 0. });

        s.<caret>x;
    }
}
