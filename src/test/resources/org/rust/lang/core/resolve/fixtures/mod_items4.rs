mod Bar {
  pub fn foo() {}
}

mod Baz {
  fn boo() {}

  mod Foo {
    fn foo()
    {
      super::bo<caret>o() /* resolved */
    }
  }
}
