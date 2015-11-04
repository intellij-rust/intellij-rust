mod a {
    fn foo() {}
    mod b {
        fn main() {
            <caret>foo()
        }
    }
}
