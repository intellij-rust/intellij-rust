// add new import to local use group if such exists
fn test() {
    use crate::mod1::*;

    foo::func();
    foo::func();
    foo::func();
}
