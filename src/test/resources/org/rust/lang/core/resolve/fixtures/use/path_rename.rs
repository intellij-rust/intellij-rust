fn foo() {}

mod inner {
    use foo as bar;

    fn main() {
        <caret>bar();
    }
}
