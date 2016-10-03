struct S {
    foobar: i32,
    frobnicator: i32,
}

fn main() {
    let _ = S {
        foo<caret>
        frobnicator: 92
    };
}
