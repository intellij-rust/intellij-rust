enum E {
    X {
        foo: i32
    }
}

fn main() {
    let _ = E::X { <caret>foo: 92 }
}


