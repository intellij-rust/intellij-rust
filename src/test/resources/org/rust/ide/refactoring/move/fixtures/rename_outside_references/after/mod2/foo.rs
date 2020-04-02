fn func() {}

fn test1() {
    crate::mod1::mod1_inner::mod1_inner_func();
    crate::mod1::mod1_func();
}

fn test2() {
    use crate::mod1::mod1_inner::mod1_inner_func;
    mod1_inner_func();

    use crate::mod1::mod1_func;
    mod1_func();
}

fn test3() {
    use crate::mod1::mod1_inner::*;
    mod1_inner_func();

    use crate::mod1::*;
    mod1_func();
}

mod foo_inner {
    fn test4() {
        super::func();
    }

    fn test1() {
        crate::mod1::mod1_inner::mod1_inner_func();
        crate::mod1::mod1_func();
    }
}
