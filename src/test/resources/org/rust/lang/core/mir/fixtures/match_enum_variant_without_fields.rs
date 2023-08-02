enum E { A, B }
fn main() {
    let e = E::A;
    match e {
        E::A => { 1 }
        E::B => { 2 }
    };
}
