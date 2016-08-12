trait T {
    fn foo(&self) -> f64;
    fn <error descr="Duplicate trait method 'foo'">foo</error>(&self) -> f64;
}