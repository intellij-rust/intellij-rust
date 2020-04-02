fn test1() {
    crate::mod2::foo::func();
    crate::mod2::foo::func2();
}

fn test2() {
    use crate::mod2::foo::func;
    func();
}

fn test3() {
    use crate::mod2::foo;
    foo::func();
    foo::func2();
}

fn test4() {
    use crate::mod1;
    crate::mod2::foo::func();
}

fn test5() {
    use crate::mod2::foo::*;
    func();
    func2();
}
