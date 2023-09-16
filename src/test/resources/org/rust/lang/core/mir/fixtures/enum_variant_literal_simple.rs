enum E {
    V1,
    V2 { a: i32 },
}
fn main() {
    let foo = E::V2 { a: 1 };
}
