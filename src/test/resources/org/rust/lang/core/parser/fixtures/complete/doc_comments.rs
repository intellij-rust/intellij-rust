/// Does useful things
/// Really useful
fn documented_function() {
    /// inner items can have docs too!
    fn foo() { }
}

mod m {
    //! This is module docs
    fn undocumented_function() {}

    /// Does other things
    fn documented_function() {}
}

/// Can mix doc comments and outer attributes
#[cfg(test)]
/// foo
struct S {
    /// Fields can have docs
    field: f32
}

/// documentation
// simple comments do not interfer with doc comments
struct T (
  /// Even for tuple structs!
  i32
);

enum E {
    /// doc
    Foo,
}

enum ES {
    /// doc
    Foo {
        /// field doc
        field: usize
    },
}

extern {
    /// Doc
    fn foo();

    /// Doc
    static errno: i32;
}
