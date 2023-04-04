#![feature(lazy_cell)]

#[macro_use]
extern crate rustc_macros;

pub mod edition;
pub mod symbol;

#[derive(Clone, Debug)]
pub struct Span;
