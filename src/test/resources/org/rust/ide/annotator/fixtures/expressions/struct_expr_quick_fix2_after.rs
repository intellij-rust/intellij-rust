struct S {
    a: i32,
    b: i32,
    c: i32,
    d: i32
}

fn main() {
    let _ = S {
        b: (),
        d: (),
        a: 92,
        c: 92
    };
}
