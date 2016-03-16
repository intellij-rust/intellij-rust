fn foo() {}

mod inner {
    use <caret>super::foo;
}
