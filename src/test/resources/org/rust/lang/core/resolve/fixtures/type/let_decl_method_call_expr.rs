struct S {
    x: f32
}



impl S {
    fn bar(&self) -> S {}
    fn foo(&self) {
        let s = self.bar();

        s.<caret>x;
    }
}
