enum E { A, B }
use E::*;
fn main() {
    let a = A;
    match a {
        A => { 1 }
        B => { 2 }
    };
}
