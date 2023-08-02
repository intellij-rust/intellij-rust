struct S;
fn main() {
    let mut a = S;
    let b = &*&a;
}
