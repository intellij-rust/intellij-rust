extern crate foo;
#[macro_use] extern crate bar;
extern crate spam as eggs;
// should be annotated as error
extern crate self;
extern crate self as foo;
