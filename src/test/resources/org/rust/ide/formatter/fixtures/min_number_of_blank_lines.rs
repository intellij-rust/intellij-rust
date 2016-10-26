#![allow(unused)]
extern crate a;
extern crate b;
use c;
use d;
mod foo;
// preserve comments between items
pub mod bar;
fn foo() {}
fn bar() {}
struct S {}
enum E {}
type A = S;
type B = E;

