mod foo {
    pub fn hello() {}
}

mod bar {
    use foo::{self};

    fn main() {
        foo::<caret>hello();
    }
}
