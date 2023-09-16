enum E1 {
    V1,
    V2 { a: i32 },
}
enum E2 {
    V3 { a: i32, b: E1, c: E1 }
}
fn main() {
    let v1 = E1::V1;
    let a = 1;
    let v2 = E1::V2 { a };
    let v3 = E2::V3 { c: v1, a: 2, b: v2 };
}
