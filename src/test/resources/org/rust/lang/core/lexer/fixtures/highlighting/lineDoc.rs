//! # Iterator
//!
//! The heart and soul of this module is the [`Iterator`] trait. The core of
//! [`Iterator`] looks like this:
//!
//! ```
//! trait Iterator {
//!     type Item;
//!     fn next(&mut self) -> Option<Self::Item>;
//! }
//! ```
//!
//! An iterator has a method, [`next()`], which when called, returns an
//! [`Option`]`<Item>`. [`next()`] will return `Some(Item)` as long as there
//! are elements, and once they've all been exhausted, will return `None` to
//! indicate that iteration is finished. Individual iterators may choose to
//! resume iteration, and so calling [`next()`] again may or may not eventually
//! start returning `Some(Item)` again at some point.
//!
//! [`Iterator`]'s full definition includes a number of other methods as well,
//! but they are default methods, built on top of [`next()`], and so you get
//! them for free.
//!
//! Iterators are also composable, and it's common to chain them together to do
//! more complex forms of processing. See the [Adapters](#adapters) section
//! below for more details.
//!
//! [`Iterator`]: trait.Iterator.html
//! [`next()`]: trait.Iterator.html#tymethod.next
//! [`Option`]: ../../std/option/enum.Option.html

/// The `Option` type. See [the module level documentation](index.html) for more.

    /// Lorem ipsum dolor sit amet, consectetur adipiscing elit,
    /// sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
    /// Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris
    /// nisi ut aliquip ex ea commodo consequat.
