fn main(param: i32) {}

#[test]
fn bar() {}

pub struct S {
    pub f: f32,
    g: u32
}

trait T {
    fn foo(self);
    fn bar(self) {}
    fn new() -> Self;
}

impl T for S {
    fn foo(self) {}
}

pub mod bar;
