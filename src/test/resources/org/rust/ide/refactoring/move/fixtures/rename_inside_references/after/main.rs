mod mod1;
mod mod2;

mod usages1 {
    fn test() {
        crate::mod2::foo::func();
        crate::mod2::foo::func2();
    }
}

mod usages2 {
    fn test() {
        use crate::mod2::foo::func;
        func();
    }
}

mod usages3 {
    fn test() {
        use crate::mod2::foo;
        foo::func();
        foo::func2();
    }
}

mod usages4 {
    fn test() {
        use crate::{mod1, mod2};
        mod2::foo::func();
    }
}

mod usages5 {
    fn test() {
        use crate::mod2::foo::*;
        func();
        func2();
    }
}

mod usages6 {
    // can't change to `use crate::mod2;` because we already have `mod2` in scope
    use crate::mod1;

    mod mod2 {}

    fn test() {
        crate::mod2::foo::func();
    }
}
