trait T {
    fn foo() {}
    fn bar();
    fn baz();
}

<error descr="Not all trait items implemented, missing: `bar`">impl T for ()</error> {
    fn baz() {}

    fn <error descr="Method is not a member of trait `T`">quux</error>() {}

}
