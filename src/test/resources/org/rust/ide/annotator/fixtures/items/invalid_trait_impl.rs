trait T {
    fn foo() {}
    fn bar();
    fn baz();
}

<error descr="Not all trait items implemented, missing: `bar`">impl T for ()</error> {

    fn baz() {}

}
