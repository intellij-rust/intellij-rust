fn foo() { }

mod inner {
    use foo;
    
    fn inner() {
        <caret>foo();
    }
}
