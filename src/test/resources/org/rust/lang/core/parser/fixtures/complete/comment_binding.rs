/// Does useful things
/// Really useful
fn documented_function() {
    /// inner items can have docs too!
    fn foo() { }
}

/// doc
mod m {
    //! This is module docs
    //! It can span more the one line,
    //! like this.
    fn undocumented_function() {}

    /// Does other things
    fn documented_function() {}
}

/// Can mix doc comments and outer attributes
#[cfg(test)]
/// foo
struct S {
    /// Fields can have docs,
    /// sometimes long ones.
    field: f32
}

/// documentation
// simple comments do not interfer with doc comments
struct T (
  /// Even for tuple structs!
  i32
);

/// doc
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

/// doc
macro_rules! makro {
    () => { };
}

////////////////////////////////
// This is not a doc comment ///
////////////////////////////////

///
///
/// foo
///
///
fn blanks() {}

// A blank line after non-doc comment detaches it from item.

// This multi-line
// non-doc comment should be attached as well
/// Blank lines after doc comments do not matter

fn foo() {}


/// Non-doc comments after a doc comment do not matter.
// Like this one!
fn bar() {}
