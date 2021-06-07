/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsGotoImplementationsTestBase : RsTestBase() {
    fun `test trait`() = doSingleTargetTest("""
        trait T/*caret*/{
            fn test(&self);
        }
        /// docs
        impl T for (){
            fn test(&self) {}
        }
    """, """
        trait T{
            fn test(&self);
        }
        /// docs
        impl T for /*caret*/(){
            fn test(&self) {}
        }
    """)

    fun `test member`() = doSingleTargetTest("""
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

    fun `test not implemented`() = doSingleTargetTest("""
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

    private fun doSingleTargetTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkEditorAction(before, after, IdeActions.ACTION_GOTO_IMPLEMENTATION)

    protected fun doMultipleTargetsTest(@Language("Rust") before: String, vararg expected: String) {
        InlineFile(before).withCaret()
        val data = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
        val actual = data.targets.map { data.render(it) }
        assertEquals(expected.toList(), actual)
    }

    protected abstract fun GotoTargetHandler.GotoData.render(element: PsiElement): String
}
