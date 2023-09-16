struct S;
fn main() {
    let mut a = S;
    let b = foo(&mut a);
}
fn foo(a: &mut S) {}
