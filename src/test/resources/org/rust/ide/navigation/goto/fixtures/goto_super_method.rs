trait T {
     fn foo(); // <- should go here
}

impl T for () {
    fn foo<caret>() {}
}
