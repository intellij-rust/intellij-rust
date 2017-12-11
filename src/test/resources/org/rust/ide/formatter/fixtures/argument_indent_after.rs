fn foo(
    x: i32,
    y: i32,
    z: i32,
) {}

pub fn new<S>(
    shape: S,
    material_idx: usize)
    -> Primitive
    where S: Shape + 'static {}

fn main() {
    foo(
        1,
        2,
        3,
    )
}
