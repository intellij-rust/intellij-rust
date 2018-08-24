/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

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
        /*caret*/foo! { struct S; }
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
        /*caret*/foo! { struct S; }
        type T = S;
    """)

    fun `test defined with nested macros`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { foo! { struct S; } }
        type T = /*caret*/S;
    """, """
        macro_rules! foo { ($ i:item) => { $ i } }
        /*caret*/foo! { foo! { struct S; } }
        type T = S;
    """)

    fun `test defined with a macro indirectly`() = doTest("""
        macro_rules! foo { ($ i:item) => { $ i } }
        foo! { mod a { struct S; } }
        use a::S;
        type T = /*caret*/S;
    """, """
        macro_rules! foo { ($ i:item) => { $ i } }
        /*caret*/foo! { mod a { struct S; } }
        use a::S;
        type T = S;
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) = checkByText(before, after) {
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
    }
}
