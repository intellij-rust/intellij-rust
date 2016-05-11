struct S<T> { field: T }

impl<T> S<T> {
    fn foo() -> <caret>T { }
}
