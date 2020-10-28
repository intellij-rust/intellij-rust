use ::;
use ::::;
use foo::;
use foo::::;
use foo::::bar;
use foo::bar::;

fn foo() {
    ::;
    ::::;
    Foo::;
    Foo::::;
    Foo::::bar;
    Foo::bar::;
    Foo::<bar>::;
    Foo::<bar::>::baz;
}
