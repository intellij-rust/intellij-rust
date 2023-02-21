/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.resolve.NameResolutionTestmarks

class RsGotoDeclarationTest : RsTestBase() {
    fun `test struct declaration`() = doTest("""
        struct /*caret_after*/S;
        type T = /*caret_before*/S;
    """)

    fun `test defined with a macro`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { struct /*caret_after*/S; }
        type T = /*caret_before*/S;
    """)

    fun `test defined with a macro with doc comment`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        /// docs
        foo! { struct /*caret_after*/S; }
        type T = /*caret_before*/S;
    """)

    fun `test defined with nested macros`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { foo! { struct /*caret_after*/S; } }
        type T = /*caret_before*/S;
    """)

    fun `test defined with a macro indirectly`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { mod a { struct /*caret_after*/S; } }
        use a::S;
        type T = /*caret_before*/S;
    """)

    fun `test defined with a macro with struct inside macro definition`() = doTest("""
        macro_rules! foo { () => { struct S; } }
        /*caret_after*/foo!();
        type T = /*caret_before*/S;
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve path to derive meta item`() = doTest("""
        #[derive(/*caret_after*/Default)]
        struct S;
        fn main() { S::/*caret_before*/default(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve aliased path to derive meta item`() = doTest("""
        #[derive(/*caret_after*/Default)]
        struct S;
        type T = S;
        fn main() { T::/*caret_before*/default(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve method to derive meta item`() = doTest("""
        #[derive(/*caret_after*/Clone)]
        struct S;
        fn main() { S./*caret_before*/clone(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve operator to derive meta item`() = doTest("""
        #[derive(/*caret_after*/PartialEq)]
        struct S;
        fn main() { S /*caret_before*/== S; }
    """)

    fun `test self parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(&mut /*caret_after*/self) {
                /*caret_before*/self;
            }
        }
    """)

    fun `test Self type in impl`() = doTest("""
        struct S;
        /// docs
        impl /*caret_after*/S {
            fn foo() -> Self/*caret_before*/ { unimplemented!() }
        }
    """)

    fun `test associated type binding`() = doTest("""
        trait Foo { type /*caret_after*/Item; }
        type T = dyn Foo</*caret_before*/Item = i32>;
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test Self-qualified path in trait impl is resolved to assoc type of current impl`() = doTest("""
        struct S;
        trait Trait {
            type Item;
            fn foo() -> Self::Item;
        }

        impl Trait for S {
            type /*caret_after*/Item = i32;
            fn foo() -> Self::/*caret_before*/Item { unreachable!() }
        }
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test Self-qualified path in trait impl is resolved to assoc type of super trait`() = doTest("""
        struct S;
        trait Trait1 { type Item; }
        trait Trait2: Trait1 { fn foo() -> i32; }

        impl Trait1 for S {
            type /*caret_after*/Item = i32;
        }

        impl Trait2 for S {
            fn foo() -> Self::/*caret_before*/Item { unreachable!() }
        }
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test 'impl for generic type' is USED for associated type resolve UFCS 1`() = doTest("""
        trait Bound {}
        trait Tr { type Item; }
        impl<A: Bound> Tr for A { type /*caret_after*/Item = (); }
        fn foo<B: Bound>(b: B) {
            let a: <B as Tr>::/*caret_before*/Item;
        }
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test 'impl for generic type' is USED for associated type resolve UFCS 2`() = doTest("""
        trait Bound { type Item; }
        impl<A: Bound> Bound for &A { type /*caret_after*/Item = (); }
        fn foo<B: Bound>(b: B) {
            let a: <&B as Bound>::/*caret_before*/Item;
        }
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test Self-qualified path in trait impl is resolved to assoc type of super trait (generic trait 1)`() = doTest("""
        struct S;
        trait Trait1<T> { type Item; }
        trait Trait2<T>: Trait1<T> { fn foo() -> i32; }

        impl Trait1<i32> for S {
            type /*caret_after*/Item = i32;
        }
        impl Trait1<u8> for S {
            type Item = u8;
        }
        impl Trait2<i32> for S {
            fn foo() -> Self::/*caret_before*/Item { unreachable!() }
        }
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test Self-qualified path in trait impl is resolved to assoc type of super trait (generic trait 2)`() = doTest("""
        struct S;
        trait Trait1<T=u8> { type Item; }
        trait Trait2<T>: Trait1<T> { fn foo() -> i32; }

        impl Trait1<i32> for S {
            type /*caret_after*/Item = i32;
        }
        impl Trait1 for S {
            type Item = u8;
        }
        impl Trait2<i32> for S {
            fn foo() -> Self::/*caret_before*/Item { unreachable!() }
        }
    """)

    @CheckTestmarkHit(NameResolutionTestmarks.TypeAliasToImpl::class)
    fun `test explicit UFCS-like type-qualified path is resolved to correct impl when inapplicable blanket impl exists`() = doTest("""
        trait Trait { type Item; }
        trait Bound {}
        impl<I: Bound> Trait for I {
            type Item = I;
        }
        struct S;
        impl Trait for S {
            type /*caret_after*/Item = ();
        }
        fn main() {
            let a: <S as Trait>::/*caret_before*/Item;
        }
    """)

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attr proc macro`() = doTest("""
        use test_proc_macros::attr_add_to_fn_beginning;

        #[attr_add_to_fn_beginning(fn /*caret_after*/foo() {})]
        fn main() {
            let _ = /*caret_before*/foo();
        }
    """)

    private fun doTest(@Language("Rust") code: String) = checkCaretMove(code) {
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
    }
}
