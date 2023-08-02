enum A { A1, A2 }
enum B { B1(A), B2(A) }
fn main() {
    let e = B::B1(A::A1);
    match e {
        B::B1(A::A1) => { 1 }
        B::B1(A::A2) => { 2 }
        B::B2(a) => { 3 }
    };
}
