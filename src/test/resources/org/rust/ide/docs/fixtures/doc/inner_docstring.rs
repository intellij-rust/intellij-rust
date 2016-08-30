/// *outer*
mod foo {
    //! **inner**
    fn bar() {}
}

fn main() {
    <caret>foo::bar()
}
