use crate::mod1::*;

// replace with absolute path if there is one usage
fn test1() {
    foo::func();
}

fn test2() {
    use crate::mod1;
    mod1::foo::func();
}
