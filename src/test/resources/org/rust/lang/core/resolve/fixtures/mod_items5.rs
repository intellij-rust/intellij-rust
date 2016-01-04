mod Bar {
  pub fn foo() {}
}

mod Baz {
  fn boo() {}

  mod Foo {
    fn foo()
    {
      bo<caret>o() /* unresolved */
    }
  }
}
