struct S {
    x: f32
}

struct X {
    s: S
}

impl S {
    fn foo(&self) {
        let X { s: f } = X { s: S { x: 0. } };

        f.<caret>x;
    }
}
