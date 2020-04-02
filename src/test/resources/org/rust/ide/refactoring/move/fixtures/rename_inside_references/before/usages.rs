fn test1() {
    crate::mod1::foo::func();
    crate::mod1::foo::func2();
}

fn test2() {
    use crate::mod1::foo::func;
    func();
}

fn test3() {
    use crate::mod1::foo;
    foo::func();
    foo::func2();
}

fn test4() {
    use crate::mod1;
    mod1::foo::func();
}

fn test5() {
    use crate::mod1::foo::*;
    func();
    func2();
}
