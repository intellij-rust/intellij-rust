/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

class RsErrorChainMacroExpansionTest : RsMacroExpansionTestBase() {
    fun `test test error_chain`() = doTest("""
        macro_rules! impl_error_chain_kind {
            (   $ (#[$ meta:meta])*
                pub enum $ name:ident { $ ($ chunks:tt)* }
            ) => {
                impl_error_chain_kind!(SORT [pub enum $ name $ (#[$ meta])* ]
                    items [] buf []
                    queue [ $ ($ chunks)* ]);
            };
            // Queue is empty, can do the work
            (SORT [pub enum $ name:ident $ ( #[$ meta:meta] )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [ ]
                queue [ ]
            ) => {
                impl_error_chain_kind!(ENUM_DEFINITION [pub enum $ name $ ( #[$ meta] )*]
                    body []
                    queue [$ ($ ( #[$ imeta] )*
                              => $ iitem: $ imode [$ ( $ ivar: $ ityp ),*] )*]
                );
                impl_error_chain_kind!(IMPLEMENTATIONS $ name {$ (
                   $ iitem: $ imode [$ (#[$ imeta])*] [$ ( $ ivar: $ ityp ),*] {$ ( $ ifuncs )*}
                   )*});
                $ (
                    impl_error_chain_kind!(ERROR_CHECK $ imode $ ($ ifuncs)*);
                )*
            };
            // Add meta to buffer
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )*]
                queue [ #[$ qmeta:meta] $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*]
                    buf [$ ( #[$ bmeta] )* #[$ qmeta] ]
                    queue [$ ( $ tail )*]);
            };
            // Add ident to buffer
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )*]
                queue [ $ qitem:ident $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])*
                              => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*]
                    buf [$ (#[$ bmeta])* => $ qitem : UNIT [ ] ]
                    queue [$ ( $ tail )*]);
            };
            // Flush buffer on meta after ident
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )*
                    => $ bitem:ident: $ bmode:tt [$ ( $ bvar:ident: $ btyp:ty ),*] ]
                queue [ #[$ qmeta:meta] $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    enum [$ ( $ (#[$ emeta])* => $ eitem $ (( $ ($ etyp),* ))* )*
                             $ (#[$ bmeta])* => $ bitem: $ bmode $ (( $ ($ btyp),* ))*]
                    items [$ ($ ( #[$ imeta:meta] )*
                              => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*
                             $ bitem: $ bmode [$ ( $ bvar:$ btyp ),*] {} ]
                    buf [ #[$ qmeta] ]
                    queue [$ ( $ tail )*]);
            };
            // Add tuple enum-variant
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )* => $ bitem:ident: UNIT [ ] ]
                queue [($ ( $ qvar:ident: $ qtyp:ty ),+) $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*]
                    buf [$ ( #[$ bmeta] )* => $ bitem: TUPLE [$ ( $ qvar:$ qtyp ),*] ]
                    queue [$ ( $ tail )*]
                );
            };
            // Add struct enum-variant - e.g. { descr: &'static str }
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )* => $ bitem:ident: UNIT [ ] ]
                queue [{ $ ( $ qvar:ident: $ qtyp:ty ),+} $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*]
                    buf [$ ( #[$ bmeta] )* => $ bitem: STRUCT [$ ( $ qvar:$ qtyp ),*] ]
                    queue [$ ( $ tail )*]);
            };
            // Add struct enum-variant, with excess comma - e.g. { descr: &'static str, }
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )* => $ bitem:ident: UNIT [ ] ]
                queue [{$ ( $ qvar:ident: $ qtyp:ty ),+ ,} $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*]
                    buf [$ ( #[$ bmeta] )* => $ bitem: STRUCT [$ ( $ qvar:$ qtyp ),*] ]
                    queue [$ ( $ tail )*]);
            };
            // Add braces and flush always on braces
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )*
                         => $ bitem:ident: $ bmode:tt [$ ( $ bvar:ident: $ btyp:ty ),*] ]
                queue [ {$ ( $ qfuncs:tt )*} $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*
                              $ (#[$ bmeta])* => $ bitem: $ bmode [$ ( $ bvar:$ btyp ),*] {$ ( $ qfuncs )*} ]
                    buf [ ]
                    queue [$ ( $ tail )*]);
            };
            // Flush buffer on double ident
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )*
                         => $ bitem:ident: $ bmode:tt [$ ( $ bvar:ident: $ btyp:ty ),*] ]
                queue [ $ qitem:ident $ ( $ tail:tt )*]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*
                             $ (#[$ bmeta])* => $ bitem: $ bmode [$ ( $ bvar:$ btyp ),*] {} ]
                    buf [ => $ qitem : UNIT [ ] ]
                    queue [$ ( $ tail )*]);
            };
            // Flush buffer on end
            (SORT [$ ( $ def:tt )*]
                items [$ ($ ( #[$ imeta:meta] )*
                          => $ iitem:ident: $ imode:tt [$ ( $ ivar:ident: $ ityp:ty ),*]
                                        {$ ( $ ifuncs:tt )*} )* ]
                buf [$ ( #[$ bmeta:meta] )*
                    => $ bitem:ident: $ bmode:tt [$ ( $ bvar:ident: $ btyp:ty ),*] ]
                queue [ ]
            ) => {
                impl_error_chain_kind!(SORT [$ ( $ def )*]
                    items [$ ( $ (#[$ imeta])* => $ iitem: $ imode [$ ( $ ivar:$ ityp ),*] {$ ( $ ifuncs )*} )*
                             $ (#[$ bmeta])* => $ bitem: $ bmode [$ ( $ bvar:$ btyp ),*] {} ]
                    buf [ ]
                    queue [ ]);
            };
            // Public enum (Queue Empty)
            (ENUM_DEFINITION [pub enum $ name:ident $ ( #[$ meta:meta] )*]
                body [$ ($ ( #[$ imeta:meta] )*
                    => $ iitem:ident ($ (($ ( $ ttyp:ty ),+))*) {$ ({$ ( $ svar:ident: $ styp:ty ),*})*} )* ]
                queue [ ]
            ) => {
                $ (#[$ meta])*
                pub enum $ name {
                    $ (
                        $ (#[$ imeta])*
                        $ iitem $ (($ ( $ ttyp ),*))* $ ({$ ( $ svar: $ styp ),*})*,
                    )*

                    #[doc(hidden)]
                    __Nonexhaustive {}
                }
            };
            // Unit variant
            (ENUM_DEFINITION [$ ( $ def:tt )*]
                body [$ ($ ( #[$ imeta:meta] )*
                    => $ iitem:ident ($ (($ ( $ ttyp:ty ),+))*) {$ ({$ ( $ svar:ident: $ styp:ty ),*})*} )* ]
                queue [$ ( #[$ qmeta:meta] )*
                    => $ qitem:ident: UNIT [ ] $ ( $ queue:tt )*]
            ) => {
                impl_error_chain_kind!(ENUM_DEFINITION [ $ ($ def)* ]
                    body [$ ($ ( #[$ imeta] )* => $ iitem ($ (($ ( $ ttyp ),+))*) {$ ({$ ( $ svar: $ styp ),*})*} )*
                            $ ( #[$ qmeta] )* => $ qitem () {} ]
                    queue [ $ ($ queue)* ]
                );
            };
            // Tuple variant
            (ENUM_DEFINITION [$ ( $ def:tt )*]
                body [$ ($ ( #[$ imeta:meta] )*
                    => $ iitem:ident ($ (($ ( $ ttyp:ty ),+))*) {$ ({$ ( $ svar:ident: $ styp:ty ),*})*} )* ]
                queue [$ ( #[$ qmeta:meta] )*
                    => $ qitem:ident: TUPLE [$ ( $ qvar:ident: $ qtyp:ty ),+] $ ( $ queue:tt )*]
            ) => {
                impl_error_chain_kind!(ENUM_DEFINITION [ $ ($ def)* ]
                    body [$ ($ ( #[$ imeta] )* => $ iitem ($ (($ ( $ ttyp ),+))*) {$ ({$ ( $ svar: $ styp ),*})*} )*
                            $ ( #[$ qmeta] )* => $ qitem (($ ( $ qtyp ),*)) {} ]
                    queue [ $ ($ queue)* ]
                );
            };
            // Struct variant
            (ENUM_DEFINITION [$ ( $ def:tt )*]
                body [$ ($ ( #[$ imeta:meta] )*
                    => $ iitem:ident ($ (($ ( $ ttyp:ty ),+))*) {$ ({$ ( $ svar:ident: $ styp:ty ),*})*} )* ]
                queue [$ ( #[$ qmeta:meta] )*
                    => $ qitem:ident: STRUCT [$ ( $ qvar:ident: $ qtyp:ty ),*] $ ( $ queue:tt )*]
            ) => {
                impl_error_chain_kind!(ENUM_DEFINITION [ $ ($ def)* ]
                    body [$ ($ ( #[$ imeta] )* => $ iitem ($ (($ ( $ ttyp ),+))*) {$ ({$ ( $ svar: $ styp ),*})*} )*
                            $ ( #[$ qmeta] )* => $ qitem () {{$ ( $ qvar: $ qtyp ),*}} ]
                    queue [ $ ($ queue)* ]
                );
            };
            (IMPLEMENTATIONS
                $ name:ident {$ (
                    $ item:ident: $ imode:tt [$ (#[$ imeta:meta])*] [$ ( $ var:ident: $ typ:ty ),*] {$ ( $ funcs:tt )*}
                )*}
            ) => {
                #[allow(unknown_lints, unused, renamed_and_removed_lints, unused_doc_comment, unused_doc_comments)]
                impl ::std::fmt::Display for $ name {
                    fn fmt(&self, fmt: &mut ::std::fmt::Formatter)
                        -> ::std::fmt::Result
                    {
                        match *self {
                            $ (
                                $ (#[$ imeta])*
                                impl_error_chain_kind!(ITEM_PATTERN
                                    $ name $ item: $ imode [$ ( ref $ var ),*]
                                ) => {
                                    let display_fn = impl_error_chain_kind!(FIND_DISPLAY_IMPL
                                        $ name $ item: $ imode
                                        {$ ( $ funcs )*});

                                    display_fn(self, fmt)
                                }
                            )*

                            _ => Ok(())
                        }
                    }
                }
                #[allow(unknown_lints, unused, renamed_and_removed_lints, unused_doc_comment, unused_doc_comments)]
                impl $ name {
                    /// A string describing the error kind.
                    pub fn description(&self) -> &str {
                        match *self {
                            $ (
                                $ (#[$ imeta])*
                                impl_error_chain_kind!(ITEM_PATTERN
                                    $ name $ item: $ imode [$ ( ref $ var ),*]
                                ) => {
                                    impl_error_chain_kind!(FIND_DESCRIPTION_IMPL
                                        $ item: $ imode self fmt [$ ( $ var ),*]
                                        {$ ( $ funcs )*})
                                }
                            )*

                            _ => "",
                        }
                    }
                }
            };
            (FIND_DISPLAY_IMPL $ name:ident $ item:ident: $ imode:tt
                { display($ self_:tt) -> ($ ( $ exprs:tt )*) $ ( $ tail:tt )*}
            ) => {
                |impl_error_chain_kind!(IDENT $ self_): &$ name, f: &mut ::std::fmt::Formatter| {
                    write!(f, $ ( $ exprs )*)
                }
            };
            (FIND_DISPLAY_IMPL $ name:ident $ item:ident: $ imode:tt
                { display($ pattern:expr) $ ( $ tail:tt )*}
            ) => {
                |_, f: &mut ::std::fmt::Formatter| { write!(f, $ pattern) }
            };
            (FIND_DISPLAY_IMPL $ name:ident $ item:ident: $ imode:tt
                { display($ pattern:expr, $ ( $ exprs:tt )*) $ ( $ tail:tt )*}
            ) => {
                |_, f: &mut ::std::fmt::Formatter| { write!(f, $ pattern, $ ( $ exprs )*) }
            };
            (FIND_DISPLAY_IMPL $ name:ident $ item:ident: $ imode:tt
                { $ t:tt $ ( $ tail:tt )*}
            ) => {
                impl_error_chain_kind!(FIND_DISPLAY_IMPL
                    $ name $ item: $ imode
                    {$ ( $ tail )*})
            };
            (FIND_DISPLAY_IMPL $ name:ident $ item:ident: $ imode:tt
                { }
            ) => {
                |self_: &$ name, f: &mut ::std::fmt::Formatter| {
                    write!(f, "{}", self_.description())
                }
            };
            (FIND_DESCRIPTION_IMPL $ item:ident: $ imode:tt $ me:ident $ fmt:ident
                [$ ( $ var:ident ),*]
                { description($ expr:expr) $ ( $ tail:tt )*}
            ) => {
                $ expr
            };
            (FIND_DESCRIPTION_IMPL $ item:ident: $ imode:tt $ me:ident $ fmt:ident
                [$ ( $ var:ident ),*]
                { $ t:tt $ ( $ tail:tt )*}
            ) => {
                impl_error_chain_kind!(FIND_DESCRIPTION_IMPL
                    $ item: $ imode $ me $ fmt [$ ( $ var ),*]
                    {$ ( $ tail )*})
            };
            (FIND_DESCRIPTION_IMPL $ item:ident: $ imode:tt $ me:ident $ fmt:ident
                [$ ( $ var:ident ),*]
                { }
            ) => {
                stringify!($ item)
            };
            (ITEM_BODY $ (#[$ imeta:meta])* $ item:ident: UNIT
            ) => { };
            (ITEM_BODY $ (#[$ imeta:meta])* $ item:ident: TUPLE
                [$ ( $ typ:ty ),*]
            ) => {
                ($ ( $ typ ),*)
            };
            (ITEM_BODY $ (#[$ imeta:meta])* $ item:ident: STRUCT
                [$ ( $ var:ident: $ typ:ty ),*]
            ) => {
                {$ ( $ var:$ typ ),*}
            };
            (ITEM_PATTERN $ name:ident $ item:ident: UNIT []
            ) => {
                $ name::$ item
            };
            (ITEM_PATTERN $ name:ident $ item:ident: TUPLE
                [$ ( ref $ var:ident ),*]
            ) => {
                $ name::$ item ($ ( ref $ var ),*)
            };
            (ITEM_PATTERN $ name:ident $ item:ident: STRUCT
                [$ ( ref $ var:ident ),*]
            ) => {
                $ name::$ item {$ ( ref $ var ),*}
            };
            // This one should match all allowed sequences in "funcs" but not match
            // anything else.
            // This is to contrast FIND_* clauses which just find stuff they need and
            // skip everything else completely
            (ERROR_CHECK $ imode:tt display($ self_:tt) -> ($ ( $ exprs:tt )*) $ ( $ tail:tt )*)
            => { impl_error_chain_kind!(ERROR_CHECK_COMMA $ imode $ ($ tail)*); };
            (ERROR_CHECK $ imode:tt display($ pattern: expr) $ ( $ tail:tt )*)
            => { impl_error_chain_kind!(ERROR_CHECK_COMMA $ imode $ ($ tail)*); };
            (ERROR_CHECK $ imode:tt display($ pattern: expr, $ ( $ exprs:tt )*) $ ( $ tail:tt )*)
            => { impl_error_chain_kind!(ERROR_CHECK_COMMA $ imode $ ($ tail)*); };
            (ERROR_CHECK $ imode:tt description($ expr:expr) $ ( $ tail:tt )*)
            => { impl_error_chain_kind!(ERROR_CHECK_COMMA $ imode $ ($ tail)*); };
            (ERROR_CHECK $ imode:tt ) => {};
            (ERROR_CHECK_COMMA $ imode:tt , $ ( $ tail:tt )*)
            => { impl_error_chain_kind!(ERROR_CHECK $ imode $ ($ tail)*); };
            (ERROR_CHECK_COMMA $ imode:tt $ ( $ tail:tt )*)
            => { impl_error_chain_kind!(ERROR_CHECK $ imode $ ($ tail)*); };
            // Utility functions
            (IDENT $ ident:ident) => { $ ident }
        }

        macro_rules! impl_error_chain_processed {
            // Default values for `types`.
            (
                types {}
                 $ (  $ rest: tt )*
            ) => {
                impl_error_chain_processed! {
                    types {
                        Error, ErrorKind, ResultExt, Result;
                    }
                     $ (  $ rest )*
                }
            };
            // With `Result` wrapper.
            (
                types {
                     $ error_name:ident,  $ error_kind_name:ident,
                     $ result_ext_name:ident,  $ result_name:ident;
                }
                 $ (  $ rest: tt )*
            ) => {
                impl_error_chain_processed! {
                    types {
                         $ error_name,  $ error_kind_name,
                         $ result_ext_name;
                    }
                     $ (  $ rest )*
                }
                /// Convenient wrapper around `std::Result`.
                #[allow(unused)]
                pub type  $ result_name<T> = ::std::result::Result<T,  $ error_name>;
            };
            // Without `Result` wrapper.
            (
                types {
                     $ error_name:ident,  $ error_kind_name:ident,
                     $ result_ext_name:ident;
                }

                links {
                     $ (  $ link_variant:ident (  $ link_error_path:path,  $ link_kind_path:path )
                        $ ( #[ $ meta_links:meta] )*; ) *
                }

                foreign_links {
                     $ (  $ foreign_link_variant:ident (  $ foreign_link_error_path:path )
                        $ ( #[ $ meta_foreign_links:meta] )*; )*
                }

                errors {
                     $ (  $ error_chunks:tt ) *
                }

            ) => {
                /// The Error type.
                ///
                /// This tuple struct is made of two elements:
                ///
                /// - an `ErrorKind` which is used to determine the type of the error.
                /// - An internal `State`, not meant for direct use outside of `error_chain`
                ///   internals, containing:
                ///   - a backtrace, generated when the error is created.
                ///   - an error chain, used for the implementation of `Error::cause()`.
                #[derive(Debug)]
                pub struct  $ error_name(
                    // The members must be `pub` for `links`.
                    /// The kind of the error.
                    pub  $ error_kind_name,
                    /// Contains the error chain and the backtrace.
                    #[doc(hidden)]
                    pub  $ crate::State,
                );

                impl  $ crate::ChainedError for  $ error_name {
                    type ErrorKind =  $ error_kind_name;

                    fn new(kind:  $ error_kind_name, state:  $ crate::State) ->  $ error_name {
                         $ error_name(kind, state)
                    }

                    fn from_kind(kind: Self::ErrorKind) -> Self {
                        Self::from_kind(kind)
                    }

                    fn with_chain<E, K>(error: E, kind: K)
                        -> Self
                        where E: ::std::error::Error + Send + 'static,
                              K: Into<Self::ErrorKind>
                    {
                        Self::with_chain(error, kind)
                    }

                    fn kind(&self) -> &Self::ErrorKind {
                        self.kind()
                    }

                    fn iter(&self) ->  $ crate::Iter {
                         $ crate::Iter::new(Some(self))
                    }

                    fn chain_err<F, EK>(self, error: F) -> Self
                        where F: FnOnce() -> EK,
                              EK: Into< $ error_kind_name> {
                        self.chain_err(error)
                    }

                    fn backtrace(&self) -> Option<& $ crate::Backtrace> {
                        self.backtrace()
                    }

                    impl_extract_backtrace!( $ error_name
                                             $ error_kind_name
                                             $ ([ $ link_error_path,  $ (#[ $ meta_links])*])*);
                }

                #[allow(dead_code)]
                impl  $ error_name {
                    /// Constructs an error from a kind, and generates a backtrace.
                    pub fn from_kind(kind:  $ error_kind_name) ->  $ error_name {
                         $ error_name(
                            kind,
                             $ crate::State::default(),
                        )
                    }

                    /// Constructs a chained error from another error and a kind, and generates a backtrace.
                    pub fn with_chain<E, K>(error: E, kind: K)
                        ->  $ error_name
                        where E: ::std::error::Error + Send + 'static,
                              K: Into< $ error_kind_name>
                    {
                         $ error_name::with_boxed_chain(Box::new(error), kind)
                    }

                    /// Construct a chained error from another boxed error and a kind, and generates a backtrace
                    pub fn with_boxed_chain<K>(error: Box<::std::error::Error + Send>, kind: K)
                        ->  $ error_name
                        where K: Into< $ error_kind_name>
                    {
                         $ error_name(
                            kind.into(),
                             $ crate::State::new::< $ error_name>(error, ),
                        )
                    }

                    /// Returns the kind of the error.
                    pub fn kind(&self) -> & $ error_kind_name {
                        &self.0
                    }

                    /// Iterates over the error chain.
                    pub fn iter(&self) ->  $ crate::Iter {
                         $ crate::ChainedError::iter(self)
                    }

                    /// Returns the backtrace associated with this error.
                    pub fn backtrace(&self) -> Option<& $ crate::Backtrace> {
                        self.1.backtrace()
                    }

                    /// Extends the error chain with a new entry.
                    pub fn chain_err<F, EK>(self, error: F) ->  $ error_name
                        where F: FnOnce() -> EK, EK: Into< $ error_kind_name> {
                         $ error_name::with_chain(self, Self::from_kind(error().into()))
                    }
                }

                impl ::std::error::Error for  $ error_name {
                    fn description(&self) -> &str {
                        self.0.description()
                    }

                    #[allow(unknown_lints, unused_doc_comment)]
                    fn cause(&self) -> Option<&::std::error::Error> {
                        match self.1.next_error {
                            Some(ref c) => Some(&**c),
                            None => {
                                match self.0 {
                                     $ (
                                         $ (#[ $ meta_foreign_links])*
                                         $ error_kind_name:: $ foreign_link_variant(ref foreign_err) => {
                                            foreign_err.cause()
                                        }
                                    ) *
                                    _ => None
                                }
                            }
                        }
                    }
                }

                impl ::std::fmt::Display for  $ error_name {
                    fn fmt(&self, f: &mut ::std::fmt::Formatter) -> ::std::fmt::Result {
                        ::std::fmt::Display::fmt(&self.0, f)
                    }
                }

                 $ (
                     $ (#[ $ meta_links])*
                    impl From< $ link_error_path> for  $ error_name {
                        fn from(e:  $ link_error_path) -> Self {
                             $ error_name(
                                 $ error_kind_name:: $ link_variant(e.0),
                                e.1,
                            )
                        }
                    }
                ) *

                 $ (
                     $ (#[ $ meta_foreign_links])*
                    impl From< $ foreign_link_error_path> for  $ error_name {
                        fn from(e:  $ foreign_link_error_path) -> Self {
                             $ error_name::from_kind(
                                 $ error_kind_name:: $ foreign_link_variant(e)
                            )
                        }
                    }
                ) *

                impl From< $ error_kind_name> for  $ error_name {
                    fn from(e:  $ error_kind_name) -> Self {
                         $ error_name::from_kind(e)
                    }
                }

                impl<'a> From<&'a str> for  $ error_name {
                    fn from(s: &'a str) -> Self {
                         $ error_name::from_kind(s.into())
                    }
                }

                impl From<String> for  $ error_name {
                    fn from(s: String) -> Self {
                         $ error_name::from_kind(s.into())
                    }
                }

                impl ::std::ops::Deref for  $ error_name {
                    type Target =  $ error_kind_name;

                    fn deref(&self) -> &Self::Target {
                        &self.0
                    }
                }


                // The ErrorKind type
                // --------------

                impl_error_chain_kind! {
                    /// The kind of an error.
                    #[derive(Debug)]
                    pub enum  $ error_kind_name {

                        /// A convenient variant for String.
                        Msg(s: String) {
                            description(&s)
                            display("{}", s)
                        }

                         $ (
                             $ (#[ $ meta_links])*
                             $ link_variant(e:  $ link_kind_path) {
                                description(e.description())
                                display("{}", e)
                            }
                        ) *

                         $ (
                             $ (#[ $ meta_foreign_links])*
                             $ foreign_link_variant(err:  $ foreign_link_error_path) {
                                description(::std::error::Error::description(err))
                                display("{}", err)
                            }
                        ) *

                         $ ( $ error_chunks)*
                    }
                }

                 $ (
                     $ (#[ $ meta_links])*
                    impl From< $ link_kind_path> for  $ error_kind_name {
                        fn from(e:  $ link_kind_path) -> Self {
                             $ error_kind_name:: $ link_variant(e)
                        }
                    }
                ) *

                impl<'a> From<&'a str> for  $ error_kind_name {
                    fn from(s: &'a str) -> Self {
                         $ error_kind_name::Msg(s.to_string())
                    }
                }

                impl From<String> for  $ error_kind_name {
                    fn from(s: String) -> Self {
                         $ error_kind_name::Msg(s)
                    }
                }

                impl From< $ error_name> for  $ error_kind_name {
                    fn from(e:  $ error_name) -> Self {
                        e.0
                    }
                }

                // The ResultExt trait defines the `chain_err` method.

                /// Additional methods for `Result`, for easy interaction with this crate.
                pub trait  $ result_ext_name<T> {
                    /// If the `Result` is an `Err` then `chain_err` evaluates the closure,
                    /// which returns *some type that can be converted to `ErrorKind`*, boxes
                    /// the original error to store as the cause, then returns a new error
                    /// containing the original error.
                    fn chain_err<F, EK>(self, callback: F) -> ::std::result::Result<T,  $ error_name>
                        where F: FnOnce() -> EK,
                              EK: Into< $ error_kind_name>;
                }

                impl<T, E>  $ result_ext_name<T> for ::std::result::Result<T, E> where E: ::std::error::Error + Send + 'static {
                    fn chain_err<F, EK>(self, callback: F) -> ::std::result::Result<T,  $ error_name>
                        where F: FnOnce() -> EK,
                              EK: Into< $ error_kind_name> {
                        self.map_err(move |e| {
                            let state =  $ crate::State::new::< $ error_name>(Box::new(e), );
                             $ crate::ChainedError::new(callback().into(), state)
                        })
                    }
                }

                impl<T>  $ result_ext_name<T> for ::std::option::Option<T> {
                    fn chain_err<F, EK>(self, callback: F) -> ::std::result::Result<T,  $ error_name>
                        where F: FnOnce() -> EK,
                              EK: Into< $ error_kind_name> {
                        self.ok_or_else(move || {
                             $ crate::ChainedError::from_kind(callback().into())
                        })
                    }
                }


            };
        }

        macro_rules! error_chain_processing {
            (
                ({},  $ b:tt,  $ c:tt,  $ d:tt)
                types  $ content:tt
                 $ (  $ tail:tt )*
            ) => {
                error_chain_processing! {
                    ( $ content,  $ b,  $ c,  $ d)
                     $ ( $ tail)*
                }
            };
            (
                ( $ a:tt, {},  $ c:tt,  $ d:tt)
                links  $ content:tt
                 $ (  $ tail:tt )*
            ) => {
                error_chain_processing! {
                    ( $ a,  $ content,  $ c,  $ d)
                     $ ( $ tail)*
                }
            };
            (
                ( $ a:tt,  $ b:tt, {},  $ d:tt)
                foreign_links  $ content:tt
                 $ (  $ tail:tt )*
            ) => {
                error_chain_processing! {
                    ( $ a,  $ b,  $ content,  $ d)
                     $ ( $ tail)*
                }
            };
            (
                ( $ a:tt,  $ b:tt,  $ c:tt, {})
                errors  $ content:tt
                 $ (  $ tail:tt )*
            ) => {
                error_chain_processing! {
                    ( $ a,  $ b,  $ c,  $ content)
                     $ ( $ tail)*
                }
            };
            ( ( $ a:tt,  $ b:tt,  $ c:tt,  $ d:tt) ) => {
                impl_error_chain_processed! {
                    types  $ a
                    links  $ b
                    foreign_links  $ c
                    errors  $ d
                }
            };
        }

        macro_rules! impl_extract_backtrace {
            ($ error_name: ident
             $ error_kind_name: ident
             $ ([$ link_error_path: path, $ (#[$ meta_links: meta])*])*) => {
                #[allow(unknown_lints, renamed_and_removed_lints, unused_doc_comment, unused_doc_comments)]
                fn extract_backtrace(e: &(::std::error::Error + Send + 'static))
                    -> Option<$ crate::InternalBacktrace> {
                    if let Some(e) = e.downcast_ref::<$ error_name>() {
                        return Some(e.1.backtrace.clone());
                    }
                    $ (
                        $ ( #[$ meta_links] )*
                        {
                            if let Some(e) = e.downcast_ref::<$ link_error_path>() {
                                return Some(e.1.backtrace.clone());
                            }
                        }
                    ) *
                    None
                }
            }
        }

        macro_rules! error_chain {
            ( $ ( $ block_name:ident { $ ( $ block_content:tt )* } )* ) => {
                error_chain_processing! {
                    ({}, {}, {}, {})
                    $ ($ block_name { $ ( $ block_content )* })*
                }
            };
        }

        error_chain! {
            types {
                Error, ErrorKind, ResultExt, Result;
            }

            links {
                Another(other_error::Error, other_error::ErrorKind) #[cfg(unix)];
            }

            foreign_links {
                Fmt(::std::fmt::Error);
                Io(::std::io::Error) #[cfg(unix)];
            }

            errors {
                InvalidToolchainName(t: String) {
                    description("invalid toolchain name")
                    display("invalid toolchain name: '{}'", t)
                }

                UnknownToolchainVersion(v: String) {
                    description("unknown toolchain version"), // note the ,
                    display("unknown toolchain version: '{}'", v), // trailing comma is allowed
                }
            }
        }
    """, """
        #[derive(Debug)]
        pub struct Error(
            pub ErrorKind,
            #[doc(hidden)]
            pub ::State,
        );
        impl ::ChainedError for Error {
            type ErrorKind = ErrorKind;

            fn new(kind: ErrorKind, state: ::State) -> Error {
                Error(kind, state)
            }

            fn from_kind(kind: Self::ErrorKind) -> Self {
                Self::from_kind(kind)
            }

            fn with_chain<E, K>(error: E, kind: K)
                                -> Self
                where E: ::std::error::Error + Send + 'static,
                      K: Into<Self::ErrorKind>
            {
                Self::with_chain(error, kind)
            }

            fn kind(&self) -> &Self::ErrorKind {
                self.kind()
            }

            fn iter(&self) -> ::Iter {
                ::Iter::new(Some(self))
            }

            fn chain_err<F, EK>(self, error: F) -> Self
                where F: FnOnce() -> EK,
                      EK: Into<ErrorKind> {
                self.chain_err(error)
            }

            fn backtrace(&self) -> Option<&::Backtrace> {
                self.backtrace()
            }

            #[allow(unknown_lints, renamed_and_removed_lints, unused_doc_comment, unused_doc_comments)]
            fn extract_backtrace(e: &(    ::std::error::Error + Send + 'static    ))
                                 -> Option<::InternalBacktrace> {
                if let Some(e) = e.downcast_ref::<Error
                >() {
                    return Some(e.1.backtrace.clone());
                }
                #[cfg(unix)]
                    {
                        if let Some(e) = e.downcast_ref::<other_error::Error>() {
                            return Some(e.1.backtrace.clone());
                        }
                    }
                None
            }
        }
        #[allow(dead_code)]
        impl Error {
            pub fn from_kind(kind: ErrorKind) -> Error {
                Error(
                    kind,
                    ::State::default(),
                )
            }


            pub fn with_chain<E, K>(error: E, kind: K)
                                    -> Error
                where E: ::std::error::Error + Send + 'static,
                      K: Into<ErrorKind>
            {
                Error::with_boxed_chain(Box::new(error), kind)
            }


            pub fn with_boxed_chain<K>(error: Box<::std::error::Error + Send>, kind: K)
                                       -> Error
                where K: Into<ErrorKind>
            {
                Error(
                    kind.into(),
                    ::State::new::<Error>(error, ),
                )
            }


            pub fn kind(&self) -> &ErrorKind {
                &self.0
            }


            pub fn iter(&self) -> ::Iter {
                ::ChainedError::iter(self)
            }


            pub fn backtrace(&self) -> Option<&::Backtrace> {
                self.1.backtrace()
            }


            pub fn chain_err<F, EK>(self, error: F) -> Error
                where F: FnOnce() -> EK, EK: Into<ErrorKind> {
                Error::with_chain(self, Self::from_kind(error().into()))
            }
        }
        impl ::std::error::Error for Error {
            fn description(&self) -> &str {
                self.0.description()
            }

            #[allow(unknown_lints, unused_doc_comment)]
            fn cause(&self) -> Option<&::std::error::Error> {
                match self.1.next_error {
                    Some(ref c) => Some(&**c),
                    None => {
                        match self.0 {
                            ErrorKind::Fmt(ref foreign_err) => {
                                foreign_err.cause()
                            }
                            #[cfg(unix)]
                            ErrorKind::Io(ref foreign_err) => {
                                foreign_err.cause()
                            }
                            _ => None
                        }
                    }
                }
            }
        }
        impl ::std::fmt::Display for Error {
            fn fmt(&self, f: &mut ::std::fmt::Formatter) -> ::std::fmt::Result {
                ::std::fmt::Display::fmt(&self.0, f)
            }
        }
        #[cfg(unix)]
        impl From<other_error::Error> for Error {
            fn from(e: other_error::Error) -> Self {
                Error(
                    ErrorKind::Another(e.0),
                    e.1,
                )
            }
        }
        impl From<::std::fmt::Error> for Error {
            fn from(e: ::std::fmt::Error) -> Self {
                Error::from_kind(
                    ErrorKind::Fmt(e)
                )
            }
        }
        #[cfg(unix)]
        impl From<::std::io::Error> for Error {
            fn from(e: ::std::io::Error) -> Self {
                Error::from_kind(
                    ErrorKind::Io(e)
                )
            }
        }
        impl From<ErrorKind> for Error {
            fn from(e: ErrorKind) -> Self {
                Error::from_kind(e)
            }
        }
        impl<'a> From<&'a str> for Error {
            fn from(s: &'a str) -> Self {
                Error::from_kind(s.into())
            }
        }
        impl From<String> for Error {
            fn from(s: String) -> Self {
                Error::from_kind(s.into())
            }
        }
        impl ::std::ops::Deref for Error {
            type Target = ErrorKind;

            fn deref(&self) -> &Self::Target {
                &self.0
            }
        }
        #[derive(Debug)]
        pub enum ErrorKind {
            Msg(String),
            #[cfg(unix)]
            Another(other_error::ErrorKind),
            Fmt(::std::fmt::Error),
            #[cfg(unix)]
            Io(::std::io::Error),
            InvalidToolchainName(String),
            UnknownToolchainVersion(String),

            #[doc(hidden)]
            __Nonexhaustive {}
        }
        #[allow(unknown_lints, unused, renamed_and_removed_lints, unused_doc_comment, unused_doc_comments)]
        impl ::std::fmt::Display for ErrorKind {
            fn fmt(&self, fmt: &mut ::std::fmt::Formatter)
                   -> ::std::fmt::Result
            {
                match *self {
                    ErrorKind::Msg(ref s) => {
                        let display_fn = |_, f: &mut ::std::fmt::Formatter| { write!(f, ("{}"), s) };

                        display_fn(self, fmt)
                    }
                    #[cfg(unix)]
                    ErrorKind::Another(ref e) => {
                        let display_fn = |_, f: &mut ::std::fmt::Formatter| { write!(f, ("{}"), e) };

                        display_fn(self, fmt)
                    }
                    ErrorKind::Fmt(ref err) => {
                        let display_fn = |_, f: &mut ::std::fmt::Formatter| { write!(f, ("{}"), err) };

                        display_fn(self, fmt)
                    }
                    #[cfg(unix)]
                    ErrorKind::Io(ref err) => {
                        let display_fn = |_, f: &mut ::std::fmt::Formatter| { write!(f, ("{}"), err) };

                        display_fn(self, fmt)
                    }
                    ErrorKind::InvalidToolchainName(ref t) => {
                        let display_fn = |_, f: &mut ::std::fmt::Formatter| { write!(f, ("invalid toolchain name: '{}'"), t) };

                        display_fn(self, fmt)
                    }
                    ErrorKind::UnknownToolchainVersion(ref v) => {
                        let display_fn = |_, f: &mut ::std::fmt::Formatter| { write!(f, ("unknown toolchain version: '{}'"), v) };

                        display_fn(self, fmt)
                    }

                    _ => Ok(())
                }
            }
        }
        #[allow(unknown_lints, unused, renamed_and_removed_lints, unused_doc_comment, unused_doc_comments)]
        impl ErrorKind {
            pub fn description(&self) -> &str {
                match *self {
                    ErrorKind::Msg(ref s) => {
                        (&s)
                    }
                    #[cfg(unix)]
                    ErrorKind::Another(ref e) => {
                        (e.description())
                    }
                    ErrorKind::Fmt(ref err) => {
                        (::std::error::Error::description(err))
                    }
                    #[cfg(unix)]
                    ErrorKind::Io(ref err) => {
                        (::std::error::Error::description(err))
                    }
                    ErrorKind::InvalidToolchainName(ref t) => {
                        ("invalid toolchain name")
                    }
                    ErrorKind::UnknownToolchainVersion(ref v) => {
                        ("unknown toolchain version")
                    }

                    _ => "",
                }
            }
        }
        #[cfg(unix)]
        impl From<other_error::ErrorKind> for ErrorKind {
            fn from(e: other_error::ErrorKind) -> Self {
                ErrorKind::Another(e)
            }
        }
        impl<'a> From<&'a str> for ErrorKind {
            fn from(s: &'a str) -> Self {
                ErrorKind::Msg(s.to_string())
            }
        }
        impl From<String> for ErrorKind {
            fn from(s: String) -> Self {
                ErrorKind::Msg(s)
            }
        }
        impl From<Error> for ErrorKind {
            fn from(e: Error) -> Self {
                e.0
            }
        }
        pub trait ResultExt<T> {
            fn chain_err<F, EK>(self, callback: F) -> ::std::result::Result<T, Error>
                where F: FnOnce() -> EK,
                      EK: Into<ErrorKind>;
        }
        impl<T, E> ResultExt<T> for ::std::result::Result<T, E> where E: ::std::error::Error + Send + 'static {
            fn chain_err<F, EK>(self, callback: F) -> ::std::result::Result<T, Error>
                where F: FnOnce() -> EK,
                      EK: Into<ErrorKind> {
                self.map_err(move |e| {
                    let state = ::State::new::<Error>(Box::new(e), );
                    ::ChainedError::new(callback().into(), state)
                })
            }
        }
        impl<T> ResultExt<T> for ::std::option::Option<T> {
            fn chain_err<F, EK>(self, callback: F) -> ::std::result::Result<T, Error>
                where F: FnOnce() -> EK,
                      EK: Into<ErrorKind> {
                self.ok_or_else(move || {
                    ::ChainedError::from_kind(callback().into())
                })
            }
        }
        #[allow(unused)]
        pub type Result<T> = ::std::result::Result<T, Error>;
""")
}
