mod a {
    fn foo() {}
    fn bar() {}
}

mod b {
    use a::*;

    fn main() {
        <caret>foo();
    }
}
