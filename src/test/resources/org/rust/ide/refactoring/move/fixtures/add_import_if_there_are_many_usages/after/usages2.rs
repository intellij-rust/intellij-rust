use crate::mod1::*;
use crate::mod2::foo;

// add new import if there are many usages
fn test1() {
    foo::func();
    foo::func();
    foo::func();
}

fn test2() {
    use crate::mod1;
    use crate::mod2::foo;
    foo::func();
    foo::func();
    foo::func();
}
