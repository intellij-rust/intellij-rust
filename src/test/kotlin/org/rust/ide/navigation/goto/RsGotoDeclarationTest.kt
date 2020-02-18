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
        struct S;
        type T = /*caret*/S;
    """, """
        struct /*caret*/S;
        type T = S;
    """)

    fun `test defined with a macro`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { struct S; }
        type T = /*caret*/S;
    """, """
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { struct /*caret*/S; }
        type T = S;
    """)

    fun `test defined with a macro with doc comment`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        /// docs
        foo! { struct S; }
        type T = /*caret*/S;
    """, """
        macro_rules! foo { ($ i:item) => { $ i } }
        /// docs
        foo! { struct /*caret*/S; }
        type T = S;
    """)

    fun `test defined with nested macros`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { foo! { struct S; } }
        type T = /*caret*/S;
    """, """
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { foo! { struct /*caret*/S; } }
        type T = S;
    """)

    fun `test defined with a macro indirectly`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { mod a { struct S; } }
        use a::S;
        type T = /*caret*/S;
    """, """
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { mod a { struct /*caret*/S; } }
        use a::S;
        type T = S;
    """)

    fun `test defined with a macro with struct inside macro definition`() = doTest("""
        macro_rules! foo { () => { struct S; } }
        foo!();
        type T = /*caret*/S;
    """, """
        macro_rules! foo { () => { struct S; } }
        /*caret*/foo!();
        type T = S;
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve path to derive meta item`() = doTest("""
        #[derive(Default)]
        struct S;
        fn main() { S::/*caret*/default(); }
    """, """
        #[derive(/*caret*/Default)]
        struct S;
        fn main() { S::default(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve aliased path to derive meta item`() = doTest("""
        #[derive(Default)]
        struct S;
        type T = S;
        fn main() { T::/*caret*/default(); }
    """, """
        #[derive(/*caret*/Default)]
        struct S;
        type T = S;
        fn main() { T::default(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve method to derive meta item`() = doTest("""
        #[derive(Clone)]
        struct S;
        fn main() { S./*caret*/clone(); }
    """, """
        #[derive(/*caret*/Clone)]
        struct S;
        fn main() { S.clone(); }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test resolve operator to derive meta item`() = doTest("""
        #[derive(PartialEq)]
        struct S;
        fn main() { S /*caret*/== S; }
    """, """
        #[derive(/*caret*/PartialEq)]
        struct S;
        fn main() { S == S; }
    """)

    fun `test self parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(&mut self) {
                /*caret*/self;
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut /*caret*/self) {
                self;
            }
        }
    """)

    fun `test Self type in impl`() = doTest("""
        struct S;
        /// docs
        impl S {
            fn foo() -> Self/*caret*/ { unimplemented!() }
        }
    """, """
        struct S;
        /// docs
        impl /*caret*/S {
            fn foo() -> Self { unimplemented!() }
        }
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkEditorAction(before, after, IdeActions.ACTION_GOTO_DECLARATION)
}
