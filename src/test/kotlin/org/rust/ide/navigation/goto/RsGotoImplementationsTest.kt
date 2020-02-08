/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsGotoImplementationsTest : RsTestBase() {
    fun `test trait`() = doTest("""
        trait T/*caret*/{
            fn test(&self);
        }
        impl T for (){
            fn test(&self) {}
        }
    """, """
        trait T{
            fn test(&self);
        }
        /*caret*/impl T for (){
            fn test(&self) {}
        }
    """)

    fun `test member`() = doTest("""
        trait T{
            fn test/*caret*/(&self);
        }
        impl T for (){
            fn test(&self) {}
        }
    """, """
        trait T{
            fn test(&self);
        }
        impl T for (){
            fn /*caret*/test(&self) {}
        }
    """)

    fun `test not implemented`() = doTest("""
        trait T{
            fn test/*caret*/(&self) {}
        }
        impl T for (){
        }
    """, """
        trait T{
            fn test/*caret*/(&self) {}
        }
        impl T for (){
        }
    """)


    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkEditorAction(before, after, IdeActions.ACTION_GOTO_IMPLEMENTATION)
}
