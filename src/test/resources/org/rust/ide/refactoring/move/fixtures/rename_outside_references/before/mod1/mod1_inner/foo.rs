fn func() {}

fn test1() {
    use super::mod1_inner_func;
    mod1_inner_func();

    use super::super::mod1_func;
    mod1_func();
}

fn test2() {
    use super::*;
    mod1_inner_func();

    use super::super::*;
    mod1_func();
}

fn test3() {
    foo_inner1::foo_inner1_func();
    foo_inner1::foo_inner2::foo_inner2_func();
}

mod foo_inner1 {
    pub fn foo_inner1_func() {}

    fn test1() {
        super::func();
        foo_inner2::foo_inner2_func();
    }

    fn test2() {
        super::super::mod1_inner_func();
        super::super::super::mod1_func();
    }

    pub mod foo_inner2 {
        pub fn foo_inner2_func() {}

        fn test1() {
            super::super::func();
            super::foo_inner1_func();
        }

        fn test2() {
            super::super::super::mod1_inner_func();
            super::super::super::super::mod1_func();
        }
    }
}
