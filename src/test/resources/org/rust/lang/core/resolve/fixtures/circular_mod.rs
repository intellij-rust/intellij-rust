use baz::b<caret>ar;

// This "self declaration" should not resolve
// but it once caused a stack overflow in the resolve.
mod circular_mod;
