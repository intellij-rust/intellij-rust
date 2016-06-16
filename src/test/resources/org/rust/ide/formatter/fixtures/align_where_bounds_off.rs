impl moo {
    pub fn with_bindings<R, F, I>(&mut self, bindings: I, f: F) -> R
        where F: FnOnce(&mut TypeContext<'a>) -> R,
              I: IntoIterator<Item = (&'a Ident, Type)> {}
}
