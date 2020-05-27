fn func() {}

fn test1() {
    super::mod1_inner_func();
    super::super::mod1_func();
}

fn test2() {
    use super::mod1_inner_func;
    mod1_inner_func();

    use super::super::mod1_func;
    mod1_func();
}

fn test3() {
    use super::*;
    mod1_inner_func();

    use super::super::*;
    mod1_func();
}

mod foo_inner {
    fn test4() {
        super::func();
    }

    fn test1() {
        super::super::mod1_inner_func();
        super::super::super::mod1_func();
    }
}
