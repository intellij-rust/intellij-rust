#[derive(Default)]
struct S {
    foo: i32
}

fn main() {
    let S {foo: bar} = S::default();
    <caret>bar;
}
