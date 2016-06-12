fn foo(x: i32,
y: i32,
            z: i32) {

}

pub fn new<S>(shape: S,
                    material_idx: usize)
      -> Primitive
                where S: Shape + 'static {}

fn main() {
    fooooo(1,
    2,
                   3)
}

extern {
    fn variadic(a: i32,
                            b: i32,
    c: i32,
                        ...)
-> i32;
}
