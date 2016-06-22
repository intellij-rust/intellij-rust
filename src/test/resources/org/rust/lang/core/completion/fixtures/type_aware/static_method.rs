struct S;

impl S {
    fn create() -> S { S }
}

fn main() {
    let _ = S::cr<caret>
}
