fn foo() { }

mod inner {
    fn inner() {
        <caret>foo();
    }
}
