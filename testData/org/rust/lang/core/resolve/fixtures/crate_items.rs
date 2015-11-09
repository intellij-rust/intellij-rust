mod foo {
  fn bar() {}
}

mod m {
    fn main() {
        foo()
    }

    fn foo() {
      ::foo::b<caret>ar();
    }
}
