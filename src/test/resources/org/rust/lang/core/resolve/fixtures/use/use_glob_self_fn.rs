fn f() {}

mod foo {
    // This looks strange, but is allowed by the Rust Language
    use f::{<caret>self};
}
