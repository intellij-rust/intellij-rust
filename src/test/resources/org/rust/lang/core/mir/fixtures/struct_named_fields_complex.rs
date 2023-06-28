struct A { x: i32 }
struct B {
    y: i32,
    z: A,
}
fn main() {
    let a1 = A { x: 1 };
    let b1 = B { y: 2, z: a1 };
    let a2 = b1.z;
    let b2 = B { y: 3, z: a2 };
}
