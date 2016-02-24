mod foo {
    pub fn f() {}
}

mod inner {
    use foo::{};

    fn main() {
        f<caret>oo::f();
    }
}
