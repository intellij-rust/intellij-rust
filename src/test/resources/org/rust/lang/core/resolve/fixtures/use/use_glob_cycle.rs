mod foo {
    pub use bar::{b as a};
}

mod bar {
    pub use foo::{a as b};

    fn main() {
        <caret>b();
    }
}
