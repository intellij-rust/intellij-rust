use crate::mod1::*;

// add new import if there are many usages
fn test1() {
    foo::func();
    foo::func();
    foo::func();
}

fn test2() {
    use crate::mod1;
    mod1::foo::func();
    mod1::foo::func();
    mod1::foo::func();
}
