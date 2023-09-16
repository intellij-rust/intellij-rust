enum E { A(i32, i32, i32, i32), B }
fn main() {
    let e = E::A(1, 2, 3, 4);
    match e {
        E::A(a, .., b) => { a + b }
        E::B => { 2 }
    };
}
