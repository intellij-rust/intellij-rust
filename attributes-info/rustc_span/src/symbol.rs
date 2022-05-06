//! An "interner" is a data structure that associates values with usize tags and
//! allows bidirectional lookup; i.e., given a value, one can easily find the
//! type, and vice versa.

use std::cmp::PartialEq;
use std::fmt;
use std::hash::Hash;
use std::lazy::SyncLazy;
use std::str;

include!(concat!(env!("OUT_DIR"), "/symbol.rs"));

#[derive(Copy, Clone, Eq, PartialEq, Hash)]
pub struct Symbol(pub u32);

static INTERNER: SyncLazy<Interner> = SyncLazy::new(|| Interner::fresh());

impl Symbol {
    const fn new(n: u32) -> Self {
        Symbol(n)
    }

    pub fn as_str(&self) -> &'static str {
        INTERNER.strings[self.0 as usize]
    }
}

#[derive(Default)]
pub struct Interner {
    pub strings: Vec<&'static str>,
}

impl Interner {
    fn prefill(init: &[&'static str]) -> Self {
        Interner {
            strings: init.into(),
        }
    }
}


impl fmt::Debug for Symbol {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Debug::fmt(&self.as_str(), f)
    }
}

impl fmt::Display for Symbol {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Display::fmt(&self.as_str(), f)
    }
}



// This module has a very short name because it's used a lot.
/// This module contains all the defined keyword `Symbol`s.
///
/// Given that `kw` is imported, use them like `kw::keyword_name`.
/// For example `kw::Loop` or `kw::Break`.
pub mod kw {
    pub use super::kw_generated::*;
}

// This module has a very short name because it's used a lot.
/// This module contains all the defined non-keyword `Symbol`s.
///
/// Given that `sym` is imported, use them like `sym::symbol_name`.
/// For example `sym::rustfmt` or `sym::u8`.
pub mod sym {
    #[doc(inline)]
    pub use super::sym_generated::*;

    // Used from a macro in `librustc_feature/accepted.rs`
    pub use super::kw::MacroRules as macro_rules;
}
