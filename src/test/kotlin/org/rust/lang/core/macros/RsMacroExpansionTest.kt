/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.tree.TokenSet
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RsElementTypes.CRATE
import org.rust.lang.core.psi.tokenSetOf
import org.rust.lang.core.resolve.NameResolutionTestmarks
import org.rust.stdext.BothEditions

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

    fun `test any rust keyword may be matched as an identifier`() {
        val keywords = RS_KEYWORDS.types
        doTest("""
            macro_rules! foo {
                ($ i:ident) => (
                    bar! { $ i }
                )
            }
            ${keywords.joinToString("\n") { "foo! { $it }" }}
        """, *keywords.map { "bar! { $it }" }.toTypedArray())
    }

    fun `test any rust keyword may be used as a metavar name`() {
        val keywords = TokenSet.andNot(RS_KEYWORDS, tokenSetOf(CRATE)).types
        doTest(keywords.joinToString("\n") {"""
            macro_rules! ${it}1 {
                ($ $it:ident) => (
                    use $ $it;
                )
            }
            ${it}1! { bar }
        """}, *keywords.map { "use bar;" }.toTypedArray())
    }

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

    fun `test vis matcher`() = doTest("""
        macro_rules! foo {
            ($ vis:vis $ name:ident) => { $ vis fn $ name() {}};
        }
        foo!(pub foo);
        foo!(pub(crate) bar);
        foo!(pub(in a::b) baz);
        foo!(baz);
    """, """
        pub fn foo() {}
    """, """
        pub(crate) fn bar() {}
    """, """
        pub(in a::b) fn baz() {}
    """, """
        fn baz() {}
    """)

    fun `test lifetime matcher`() = doTest("""
        macro_rules! foo {
            ($ lt:lifetime) => { struct Ref<$ lt>(&$ lt str);};
        }
        foo!('a);
        foo!('lifetime);
    """, """
        struct Ref<'a>(&'a str);
    """, """
        struct Ref<'lifetime>(&'lifetime str);
    """)

    fun `test literal matcher`() = doTest("""
        macro_rules! foo {
            ($ type:ty $ lit:literal) => { const VALUE: $ type = $ lit;};
        }
        foo!(u8 0);
        foo!(&'static str "value");
    """, """
        const VALUE: u8 = 0;
    """, """
        const VALUE: &'static str = "value";
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

    fun `test group with $crate usage`() = doTest(MacroExpansionMarks.groupInputEnd1, """
        macro_rules! foo {
            ($ ($ i:item)*; $ ($ j:item)*) => ($ ( use $ crate::$ i; )* $ ( use $ crate::$ i; )*)
        }
        foo! {;}
    """, "")

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

    fun `test match pattern by word token 1`() = doTest(MacroExpansionMarks.failMatchPatternByToken, """
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

    fun `test match pattern by word token 2`() = doTest(MacroExpansionMarks.failMatchPatternByToken, """
        macro_rules! foo {
            ($ i:ident) => (
                mod $ i {}
            );
            ('spam $ i:ident) => (
                fn $ i() {}
            );
            ('eggs $ i:ident) => (
                struct $ i;
            )
        }
        foo! { foo }
        foo! { 'spam bar }
        foo! { 'eggs Baz }
    """, """
        mod foo {}
    """, """
        fn bar() {}
    """, """
        struct Baz;
    """)

    fun `test match pattern by word token 3`() = doTest(MacroExpansionMarks.failMatchPatternByToken, """
        macro_rules! foo {
            ($ i:ident) => (
                mod $ i {}
            );
            ("spam" $ i:ident) => (
                fn $ i() {}
            );
            ("eggs" $ i:ident) => (
                struct $ i;
            )
        }
        foo! { foo }
        foo! { "spam" bar }
        foo! { "eggs" Baz }
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

    // TODO should work only on 2018 edition
    fun `test match * vs ? group pattern`() = doTest(MacroExpansionMarks.questionMarkGroupEnd, """
        macro_rules! foo {
            ($ ($ i:ident)?) => (
                mod question_matched {}
            );
            ($ ($ i:ident)*) => (
                mod asterisk_matched {}
            );
        }

        foo! {  }
        foo! { foo }
        foo! { foo bar }
    """, """
        mod question_matched {}
    """, """
        mod question_matched {}
    """, """
        mod asterisk_matched {}
    """)

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

    fun `test stmt context`() = checkSingleMacro("""
        macro_rules! foo {
            ($ i:ident, $ j:ident) => {
                struct $ i;
                let $ j = 0;
                ($ i, $ j)
            }
        }

        fn main() {
            foo!(S, a);
        } //^
    """, """
        struct S;
        let a = 0;
        (S, a)
    """)

    // There was a problem with "debug" macro related to the fact that we parse macro call
    // with such name as a specific syntax construction
    fun `test macro with name "debug"`() = doTest("""
        macro_rules! debug {
            ($ t:ty) => { fn foo() -> $ t {} }
        }
        debug!(i32);
    """, """
        fn foo() -> i32 {}
    """)

    fun `test macro with name "vec"`() = doTest("""
       macro_rules! vec {
           ($ t:ty) => { fn foo() -> $ t {} }
       }
       vec!(i32);
    """, """
        fn foo() -> i32 {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test standard "vec!"`() = checkSingleMacro("""
        fn main() {
            vec![1, 2, 3];
        } //^
    """, """
        <[_]>::into_vec(box [(1), (2), (3)])
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

    fun `test macro defined with a macro`() = doTest("""
        macro_rules! foo {
            () => {
                macro_rules! bar { () => { fn foo() {} } }
                bar!();
            }
        }
        foo!();
    """, """
        macro_rules! bar { () => { fn foo() {} } }
        fn foo() {}
    """)

    fun `test macro (with arguments) defined with a macro`() = doTest("""
        macro_rules! foo {
            ($ a:item) => {
                macro_rules! bar { ($ b:item) => { $ b }; }
                bar!($ a);
            }
        }
        foo!(fn foo() {});
    """, """
        macro_rules! bar { ($ b:item) => { $ b }; }
        fn foo() {}
    """ to MacroExpansionMarks.substMetaVarNotFound)

    fun `test expand macro defined in function`() = doTest("""
        fn main() {
            macro_rules! foo {
                () => { 2 + 2 };
            }
            foo!();
        }
    """, """
        2 + 2
    """)

    fun `test expand macro qualified with $crate`() = doTest("""
        macro_rules! foo {
            () => { $ crate::bar!(); };
        }
        #[macro_export]
        macro_rules! bar {
            () => { fn foo() {} };
        }
        foo!();
    """, """
        fn foo() {}
    """ to NameResolutionTestmarks.dollarCrateMagicIdentifier)

    fun `test incorrect "vis" group does not cause OOM`() = doTest("""
        // error: repetition matches empty token tree
        macro_rules! foo {
            ($($ p:vis)*) => {}
        }
        foo!(a);
    """, """
        foo!(a);
    """ to MacroExpansionMarks.groupMatchedEmptyTT)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @BothEditions
    fun `test local_inner_macros 1`() = checkSingleMacroByTree("""
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        foo!();
        //^
    //- lib.rs
        #[macro_export(local_inner_macros)]
        macro_rules! foo {
            () => { bar!{} };
        }

        #[macro_export]
        macro_rules! bar {
            () => { fn bar() {} };
        }
    """, """
        fn bar() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @BothEditions
    fun `test local_inner_macros 2`() = checkSingleMacroByTree("""
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        foo!();
        //^
    //- lib.rs
        #[macro_export(local_inner_macros)]
        macro_rules! foo {
            () => {
                macro_rules! bar {
                    () => { fn fake_bar() {} };
                }
                bar!{}
            };
        }

        #[macro_export]
        macro_rules! bar {
            () => { fn bar() {} };
        }
    """, """
        macro_rules! bar {
            () => { fn fake_bar() {} };
        }
        fn bar() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @BothEditions
    fun `test local_inner_macros 3`() = checkSingleMacroByTree("""
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        macro_rules! bar {
            () => { fn local_bar() {} };
        }

        foo! { bar!{} }
        //^
    //- lib.rs
        #[macro_export(local_inner_macros)]
        macro_rules! foo {
            ($ i:item) => { bar!{} $ i };
        }

        #[macro_export]
        macro_rules! bar {
            () => { fn bar() {} };
        }
    """, """
        fn bar() {}
        fn local_bar() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @BothEditions
    fun `test local_inner_macros 4`() = checkSingleMacroByTree("""
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        macro_rules! bar {
            () => { fn local_bar() {} };
        }
        macro_rules! baz {
            () => { foo! { bar!{} } };
        }

        baz!();
        //^
    //- lib.rs
        #[macro_export(local_inner_macros)]
        macro_rules! foo {
            ($ i:item) => { bar!{} $ i };
        }

        #[macro_export]
        macro_rules! bar {
            () => { fn bar() {} };
        }
    """, """
        fn bar() {}
        fn local_bar() {}
    """)

    fun `test item with docs`() = doTest("""
        // error: repetition matches empty token tree
        macro_rules! foo {
            ($ i:item) => { $ i }
        }
        foo! {
            /// Some docs
            fn foo() {}
        }
    """, """
        #[doc = r###"Some docs"###]
        fn foo() {}
    """ to MacroExpansionMarks.docsLowering)

    fun `test docs lowering`() = doTest("""
        // error: repetition matches empty token tree
        macro_rules! foo {
            (#[$ i:meta]) => {
                #[$ i]
                fn foo() {}
            }
        }
        foo! {
            /// Some docs
        }
    """, """
        #[doc = r###"Some docs"###]
        fn foo() {}
    """ to MacroExpansionMarks.docsLowering)
}
