struct S;

impl S {
    fn test() { }
}

fn main() {
    S::<caret>test();
}
