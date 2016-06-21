struct S;

impl S {
    fn test(&self) { }
}

fn main() {
    S::<caret>test();
}
