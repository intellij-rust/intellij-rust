fn func() {}

fn test1() {
    use crate::mod1::mod1_inner::mod1_inner_func;
    mod1_inner_func();

    use crate::mod1::mod1_func;
    mod1_func();
}

fn test2() {
    use crate::mod1::mod1_inner::*;
    mod1_inner_func();

    use crate::mod1::*;
    mod1_func();
}

fn test3() {
    foo_inner1::foo_inner1_func();
    foo_inner1::foo_inner2::foo_inner2_func();
}

mod foo_inner1 {
    use crate::mod1::mod1_inner;
    use crate::mod1;

    pub fn foo_inner1_func() {}

    fn test1() {
        super::func();
        foo_inner2::foo_inner2_func();
    }

    fn test2() {
        mod1_inner::mod1_inner_func();
        mod1::mod1_func();
    }

    pub mod foo_inner2 {
        use crate::mod1::mod1_inner;
        use crate::mod1;

        pub fn foo_inner2_func() {}

        fn test1() {
            super::super::func();
            super::foo_inner1_func();
        }

        fn test2() {
            mod1_inner::mod1_inner_func();
            mod1::mod1_func();
        }
    }
}
