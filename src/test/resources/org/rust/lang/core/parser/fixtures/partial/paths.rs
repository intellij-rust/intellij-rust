fn foo() {
    Foo::;
    Foo::bar::;
    Foo::<bar>::;
    Foo::<bar::>::baz;
}
