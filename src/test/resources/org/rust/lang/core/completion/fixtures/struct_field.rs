struct S {
    foobarbaz: i32
}

fn main() {
    let _ = S { foo<caret> };
}
