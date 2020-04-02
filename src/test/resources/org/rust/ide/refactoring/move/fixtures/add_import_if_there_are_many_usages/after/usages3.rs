// add new import to local use group if such exists
fn test() {
    use crate::mod1::*;
    use crate::mod2::foo;

    foo::func();
    foo::func();
    foo::func();
}
