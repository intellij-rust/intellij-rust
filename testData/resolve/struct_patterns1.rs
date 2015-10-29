#[derive(Default)]
struct S {
    foo: i32
}

fn main() {
    let S {foo} = S::default();
    <caret>foo;
}
