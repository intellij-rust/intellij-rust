/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

class RsMacroExpansionTest : RsMacroExpansionTestBase() {
    fun `test ident`() = doTest("""
        macro_rules! foo {
            ($ i:ident) => (
                fn $ i() {}
            )
        }
        foo! { bar }
    """, """
        fn bar() {}
    """)

    fun `test ident 'self'`() = doTest("""
        macro_rules! foo {
            ($ i:ident) => (
                use foo::{$ i};
            )
        }
        foo! { self }
    """, """
        use foo::{self};
    """)

    fun `test ident 'super', 'crate'`() = doTest("""
        macro_rules! foo {
            ($ i:ident) => (
                use $ i::foo;
            )
        }
        foo! { super }
        foo! { crate }
    """, """
        use super::foo;
    """, """
        use crate::foo;
    """)

    fun `test ident 'Self'`() = doTest("""
        macro_rules! foo {
            ($ i:ident) => (
                impl S { fn foo() -> $ i { S } }
            )
        }
        foo! { Self }
    """, """
        impl S { fn foo() -> Self { S } }
    """)

    // This test doesn't check result of '$crate' expansion because it's implementation detail.
    // For example rustc expands '$crate' to synthetic token without text representation
    fun `test '$crate' metavar is matched as identifier`() = doTest("""
        macro_rules! foo {
            () => { bar!($ crate); } // $ crate consumed as a single identifier by `bar`
        }
        macro_rules! bar {
            ($ i:ident) => { mod a {} }
        }
        foo! {}
    """, """
        mod a {}
    """)

    fun `test path`() = doTest("""
        macro_rules! foo {
            ($ i:path) => {
                fn foo() { let a = $ i; }
            }
        }
        foo! { foo }
        foo! { bar::<u8> }
        foo! { bar::baz::<u8> }
        foo! { bar::<u8>::baz::<u8> }
    """, """
        fn foo() { let a = foo; }
    """, """
        fn foo() { let a = bar::<u8>; }
    """, """
        fn foo() { let a = bar::baz::<u8>; }
    """, """
        fn foo() { let a = bar::<u8>::baz::<u8>; }
    """)

    fun `test expr`() = doTest("""
        macro_rules! foo {
            ($ i:expr) => ( fn bar() { $ i; } )
        }
        foo! { 2 + 2 * baz(3).quux() }
    """, """
         fn bar() { (2 + 2 * baz(3).quux()); }
    """)

    fun `test ty`() = doTest("""
        macro_rules! foo {
            ($ i:ty) => (
                fn bar() -> $ i { unimplemented!() }
            )
        }
        foo! { Baz<u8> }
    """, """
        fn bar() -> Baz<u8> { unimplemented!() }
    """)

    fun `test pat`() = doTest("""
        macro_rules! foo {
            ($ i:pat) => { fn foo() { let $ i; } }
        }
        foo! { (a, b) }
    """, """
        fn foo() { let (a, b); }
    """)

    fun `test stmt`() = doTest("""
        macro_rules! foo {
            ($ i:stmt) => (
                fn bar() { $ i; }
            )
        }
        foo! { 2 }
        foo! { let a = 0 }
    """, """
         fn bar() { 2; }
    """, """
         fn bar() { let a = 0; }
    """)

    fun `test block`() = doTest("""
        macro_rules! foo {
            ($ i:block) => { fn foo() $ i }
        }
        foo! { { 1; } }
    """, """
        fn foo() { 1; }
    """)

    fun `test meta`() = doTest("""
        macro_rules! foo {
            ($ i:meta) => (
                #[$ i]
                fn bar() {}
            )
        }
        foo! { cfg(target_os = "windows") }
    """, """
        #[cfg(target_os = "windows")]
         fn bar() {}
    """)

    fun `test tt block`() = doTest("""
        macro_rules! foo {
            ($ i:tt) => { fn foo() $ i }
        }
        foo! { { 1; } }
    """, """
        fn foo() { 1; }
    """)

    fun `test tt collapsed token`() = doTest("""
        macro_rules! foo {
            ($ i:tt) => { fn foo() { true $ i false; } }
        }
        foo! { && }
    """, """
        fn foo() { true && false; }
    """)

    fun `test tt group`() = doTest("""
        macro_rules! foo {
            ($($ i:tt)*) => { $($ i)* }
        }
        foo! { fn foo() {} }
    """, """
        fn foo() {}
    """)

    fun `test empty group`() = doTest(MacroExpansionMarks.groupInputEnd1, """
        macro_rules! foo {
            ($ ($ i:item)*) => ($ ( $ i )*)
        }
        foo! {}
    """, "")

    fun `test group after empty group`() = doTest("""
        macro_rules! foo {
            ($($ i:meta)* ; $($ j:item)*) => {
                $($ j)*
            }
        }

        foo!{ ; fn foo() {} }
    """, """
        fn foo() {}
    """)

    fun `test all items`() = doTest("""
        macro_rules! foo {
            ($ ($ i:item)*) => ($ (
                $ i
            )*)
        }
        foo! {
            extern crate a;
            mod b;
            mod c {}
            use d;
            const E: i32 = 0;
            static F: i32 = 0;
            impl G {}
            struct H;
            enum I { Foo }
            trait J {}
            fn h() {}
            extern {}
            type T = u8;
        }
    """, """
        extern crate a;
        mod b;
        mod c {}
        use d;
        const E: i32 = 0;
        static F: i32 = 0;
        impl G {}
        struct H;
        enum I { Foo }
        trait J {}
        fn h() {}
        extern {}
        type T = u8;
    """)

    fun `test match complex pattern`() = doTest("""
        macro_rules! foo {
            (=/ $ i1:item #%*=> $ i2:item) => (
                $ i1
                $ i2
            )
        }
        foo! {
            =/
            fn foo() {}
            #%*=>
            fn bar() {}
        }
    """, """
        fn foo() {}
        fn bar() {}
    """)

    fun `test match pattern by first token`() = doTest(MacroExpansionMarks.failMatchPatternByToken, """
        macro_rules! foo {
            ($ i:ident) => (
                mod $ i {}
            );
            (= $ i:ident) => (
                fn $ i() {}
            );
            (+ $ i:ident) => (
                struct $ i;
            )
        }
        foo! {   foo }
        foo! { = bar }
        foo! { + Baz }
    """, """
        mod foo {}
    """, """
        fn bar() {}
    """, """
        struct Baz;
    """)

    fun `test match pattern by last token`() = doTest(MacroExpansionMarks.failMatchPatternByToken, """
        macro_rules! foo {
            ($ i:ident) => (
                mod $ i {}
            );
            ($ i:ident =) => (
                fn $ i() {}
            );
            ($ i:ident +) => (
                struct $ i;
            )
        }
        foo! { foo }
        foo! { bar = }
        foo! { Baz + }
    """, """
        mod foo {}
    """, """
        fn bar() {}
    """, """
        struct Baz;
    """)

    fun `test match pattern by word token`() = doTest(MacroExpansionMarks.failMatchPatternByToken, """
        macro_rules! foo {
            ($ i:ident) => (
                mod $ i {}
            );
            (spam $ i:ident) => (
                fn $ i() {}
            );
            (eggs $ i:ident) => (
                struct $ i;
            )
        }
        foo! { foo }
        foo! { spam bar }
        foo! { eggs Baz }
    """, """
        mod foo {}
    """, """
        fn bar() {}
    """, """
        struct Baz;
    """)

    fun `test match pattern by binding type 1`() = doTest(MacroExpansionMarks.failMatchPatternByExtraInput, """
        macro_rules! foo {
            ($ i:ident) => (
                fn $ i() {}
            );
            ($ i:ty) => (
                struct Bar { field: $ i }
            )
        }
        foo! { foo }
        foo! { Baz<u8> }
    """, """
        fn foo() {}
    """, """
        struct Bar { field: Baz<u8> }
    """)

    fun `test match pattern by binding type 2`() = doTest(MacroExpansionMarks.failMatchPatternByBindingType, """
        macro_rules! foo {
            ($ i:item) => (
                $ i
            );
            ($ i:ty) => (
                struct Bar { field: $ i }
            )
        }
        foo! { fn foo() {} }
        foo! { Baz<u8> }
    """, """
        fn foo() {}
    """, """
        struct Bar { field: Baz<u8> }
    """)

    fun `test match group pattern by separator token`() = doTest("""
        macro_rules! foo {
            ($ ($ i:ident),*) => ($ (
                mod $ i {}
            )*);
            ($ ($ i:ident)#*) => ($ (
                fn $ i() {}
            )*);
            ($ i:ident ,# $ j:ident) => (
                struct $ i;
                struct $ j;
            )
        }
        foo! { foo, bar }
        foo! { foo# bar }
        foo! { Foo,# Bar }
    """, """
        mod foo {}
        mod bar {}
    """ to null, """
        fn foo() {}
        fn bar() {}
    """ to MacroExpansionMarks.failMatchGroupBySeparator, """
        struct Foo;
        struct Bar;
    """ to MacroExpansionMarks.failMatchPatternByExtraInput)

    fun `test match * vs + group pattern`() = doTest("""
        macro_rules! foo {
            ($ ($ i:ident)+) => (
                mod plus_matched {}
            );
            ($ ($ i:ident)*) => (
                mod asterisk_matched {}
            );
        }
        foo! {  }
        foo! { foo }
    """, """
        mod asterisk_matched {}
    """ to MacroExpansionMarks.failMatchGroupTooFewElements, """
        mod plus_matched {}
    """ to null)

    fun `test group pattern with collapsed token as a separator`() = doTest("""
        macro_rules! foo {
            ($ ($ i:ident)&&*) => ($ (
                mod $ i {}
            )*)
        }
        foo! { foo && bar }
    """, """
        mod foo {}
        mod bar {}
    """)

    fun `test insert group with separator token`() = doTest("""
        macro_rules! foo {
            ($ ($ i:expr),*) => {
                fn foo() { $ ($ i);*; }
            }
        }
        foo! { 1, 2, 3, 4 }
    """, """
        fn foo() { (1); (2); (3); (4); }
    """)

    fun `test match non-group pattern with asterisk`() = doTest("""
        macro_rules! foo {
            ($ ($ i:ident),*) => ($ (
                mod $ i {}
            )*);
            ($ i:ident ,* $ j:ident) => (
                struct $ i;
                struct $ j;
            )
        }
        foo! { foo, bar }
        foo! { Foo,* Bar }
    """, """
        mod foo {}
        mod bar {}
    """ to MacroExpansionMarks.groupInputEnd3, """
        struct Foo;
        struct Bar;
    """ to MacroExpansionMarks.failMatchPatternByExtraInput)

    fun `test multiple groups with reversed variables order`() = doTest("""
        macro_rules! foo {
            ($($ i:item),*; $($ e:expr),*) => {
                fn foo() { $($ e);*; }
                $($ i)*
            }
        }
        foo! { mod a {}, mod b {}; 1, 2 }
    """, """
        fn foo() { (1); (2); }
        mod a {}
        mod b {}
    """)

    fun `test nested groups`() = doTest("""
        macro_rules! foo {
            ($($ i:ident $($ e:expr),*);*) => {
                $(fn $ i() { $($ e);*; })*
            }
        }
        foo! { foo 1,2,3; bar 4,5,6 }
    """, """
        fn foo() { (1); (2); (3); }
        fn bar() { (4); (5); (6); }
    """)

    fun `test nested groups that uses vars from outer group`() = doTest("""
        macro_rules! foo {
            ($($ i:expr, $($ e:ident),*);*) => {
                $($( fn $ e() { $ i; } )*)*
            }
        }
        foo! { 1, foo, bar, baz; 2, quux, eggs }
    """, """
        fn foo() { (1); }
        fn bar() { (1); }
        fn baz() { (1); }
        fn quux() { (2); }
        fn eggs() { (2); }
    """)

    fun `test group in braces`() = doTest("""
        macro_rules! foo {
            ( { $($ i:item)* } $ j:expr) => {
                $( $ i )*
                fn foo() { $ j; }
            };
        }
        foo! {
            { mod a {} mod b {} }
            2
        }
    """, """
         mod a {}
         mod b {}
         fn foo() { (2); }
    """)

    fun `test group with the separator the same as the next token 1`() = doTest(MacroExpansionMarks.groupInputEnd1, """
        macro_rules! foo {
            ($($ i:item)=* =) => {
                $($ i)*
            }
        }

        foo! {
            fn foo() {} =
        }
    """, """
         fn foo() {}
    """)

    fun `test group with the separator the same as the next token 2`() = doTest(MacroExpansionMarks.groupInputEnd2, """
        macro_rules! foo {
            ($($ i:item)=* = #) => {
                $($ i)*
            }
        }

        foo! {
            fn foo() {} = #
        }
    """, """
         fn foo() {}
    """)

    fun `test impl members context`() = checkSingleMacro("""
        macro_rules! foo {
            () => {
                fn foo() {}
                type Bar = u8;
                const BAZ: u8 = 0;
            }
        }

        struct S;
        impl S {
            foo!();
        }  //^
    """, """
        fn foo() {}
        type Bar = u8;
        const BAZ: u8 = 0;
    """)

    fun `test pattern context`() = checkSingleMacro("""
        macro_rules! foo {
            ($ i:ident, $ j:ident) => {
                ($ i, $ j)
            }
        }

        fn main() {
            let (foo!(a, b), c) = ((1, 2), 3);
        }      //^
    """, """
        (a, b)
    """)

    fun `test type context`() = checkSingleMacro("""
        macro_rules! foo {
            ($ i:ident, $ j:ident) => {
                $ i<$ j>
            }
        }

        fn bar() -> foo!(Option, i32) { unimplemented!() }
                  //^
    """, """
        Option<i32>
    """)

    fun `test expend macro definition`() = doTest("""
       macro_rules! foo {
           () => {
               macro_rules! bar { () => {} }
           }
       }
       foo!();
    """, """
        macro_rules! bar { () => {} }
    """)
}
