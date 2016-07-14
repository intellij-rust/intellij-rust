struct S;

impl S {
    fn transmogrify(&self) {}

    fn foo(&self) {
        self.transmogrify
    }
}
