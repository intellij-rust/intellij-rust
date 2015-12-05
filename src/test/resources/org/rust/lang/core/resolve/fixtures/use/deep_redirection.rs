mod foo {
    pub fn a() {}
    pub use bar::b as c;
    pub use bar::d as e;
}

mod bar {
    pub use foo::a as b;
    pub use foo::c as d;
}


fn main() {
    foo::<caret>e();
}
