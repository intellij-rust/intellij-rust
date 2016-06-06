struct S {
    x: f32
}

impl S {
    fn foo(&self) {
        let S { x: x } = S { x: 0. };

        <caret>x;
    }
}
