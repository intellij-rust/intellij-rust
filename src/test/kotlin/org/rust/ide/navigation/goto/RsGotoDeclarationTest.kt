/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor

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

    private fun doTest(@Language("Rust") code: String) = checkCaretMove(code) {
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
    }
}
