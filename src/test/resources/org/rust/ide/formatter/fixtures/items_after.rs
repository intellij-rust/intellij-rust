#[cfg(test)]
#[foo]
pub mod m {
    enum E {
        Foo,
        Bar,
    }

    pub struct S {
        foo: i32,
        pub bar: i32
    }

    impl S {
        fn foo() -> i32 {
            92
        }

        pub fn new<S>(shape: S, material_idx: usize) -> Primitive
            where S: Shape + 'static,
                  X: 'a {}

        pub fn new<S>(shape: S, material_idx: usize)
            -> Primitive
            where S: Shape + 'static {}
    }
}
