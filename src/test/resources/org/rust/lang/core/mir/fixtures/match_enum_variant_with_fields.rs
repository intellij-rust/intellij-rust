enum E { A(i32), B(i32, i32), C(i32, i32, i32) }
fn main() {
    let e = E::A(0);
    match e {
        E::A(a) => { 1 }
        E::B(b1, b2) => { 2 }
        E::C(c1, c2, c3) => { 3 }
    };
}
