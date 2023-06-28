enum A {
    A0,
    A1(i32, u32),
    A2 { x: i32, y: i32 },
    A3 { x: i32, y: i32 },
    A4 { x: B },
}
enum B { B1 }
fn main() {
    let a = A::A0;
    match a {
        A::A0 => {}
        A::A1 { 1: x, 0: y } => {}
        A::A2 { x, y } => {}
        A::A3 { x: x2, y: y2 } => {}
        A::A4 { x: B::B1 } => {}
    }
}
