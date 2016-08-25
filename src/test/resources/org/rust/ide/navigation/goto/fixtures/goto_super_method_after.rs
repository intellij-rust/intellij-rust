trait T {
     fn <caret>foo(); // <- should go here
}

impl T for () {
    fn foo() {}
}
