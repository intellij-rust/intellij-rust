/// Does useful things
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

/// Can mix doc comments an outer attributes
#[cfg(test)]
/// foo
struct S;

/// documentation
// simple comments do not interfer with doc comments
struct T;
