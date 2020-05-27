pub mod inner1 {
    mod inner2 {
        mod inner3 {
            pub mod mod1;
            pub mod mod2;
        }
    }
    pub use inner2::*;
}
mod usages {
    fn test1a() {
        crate::inner1::inner3::mod1::foo::func();
    }
    fn test1b() {
        use crate::inner1::inner3::mod1::foo::func;
    }
}

fn test2a() {
    inner1::inner3::mod1::foo::func();
}
fn test2b() {
    use inner1::inner3::mod1::foo::func;
}
