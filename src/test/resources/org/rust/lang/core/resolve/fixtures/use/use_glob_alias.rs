mod foo {
    pub fn hello() {}
}

mod bar {
    use foo::{hello as spam};

    fn main() {
        <caret>spam();
    }
}
