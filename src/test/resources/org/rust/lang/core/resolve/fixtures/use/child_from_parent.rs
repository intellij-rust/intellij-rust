mod foo {
    // This visits `mod foo` twice during resolve
    use foo::bar::baz;

    pub mod bar {
        pub fn baz() {}
    }

    fn main() {
        <caret>baz();
    }
}
