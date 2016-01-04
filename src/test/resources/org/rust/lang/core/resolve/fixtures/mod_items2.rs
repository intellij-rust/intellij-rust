mod Bar {
  pub fn foo() {}
}

mod Baz {
  fn boo() {}

  mod Foo {
    fn foo()
    {
      super::super::Bar::fo<caret>o() /* resolved */
    }
  }
}
