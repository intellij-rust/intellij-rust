enum E {
    X {
        bazbarfoo: i32
    }
}

fn main() {
    let _ = E::X { baz<caret>: 92 }
}


