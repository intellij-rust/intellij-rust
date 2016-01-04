mod Bar {
  pub fn foo() {}
}

mod Baz {
  fn boo() {}

  mod Foo {
    fn foo()
    {
      Bar::fo<caret>o() /* unresolved */
    }
  }
}
