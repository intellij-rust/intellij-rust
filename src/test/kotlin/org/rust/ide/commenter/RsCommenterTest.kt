/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.commenter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapiext.Testmark
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.RsLanguage
import kotlin.reflect.KMutableProperty0

class RsCommenterTest : RsTestBase() {
    fun `test single line`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        fn <caret>double(x: i32) -> i32 {
            x * 2
        }
    """, """
        // fn double(x: i32) -> i32 {
            x <caret>* 2
        }
    """, """
        //fn double(x: i32) -> i32 {
            x<caret> * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test multi line`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        fn doub<selection>le(x: i32) -> i32 {
            x</selection> * 2
        }
    """, """
        // fn doub<selection>le(x: i32) -> i32 {
        //     x</selection> * 2
        }
    """, """
        //fn doub<selection>le(x: i32) -> i32 {
        //    x</selection> * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test single line block`() = checkEditorAction("""
        fn d<caret>ouble(x: i32) -> i32 {
            x * 2
        }
    """, """
        fn d/*<caret>*/ouble(x: i32) -> i32 {
            x * 2
        }
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test multi line block`() = checkEditorAction("""
        fn doub<selection>le(x: i32) -> i32 {
            x</selection> * 2
        }
    """, """
        fn doub<selection>/*le(x: i32) -> i32 {
            x*/</selection> * 2
        }
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test multi line block proper indent`() = checkEditorAction("""
        fn fib(x: i32) -> i32 {
        <selection>    match x {
                0 => 1,
                1 => 1,
                _ => fib(x - 2) + fib(x - 1)
            }
        </selection>}
    """, """
        fn fib(x: i32) -> i32 {
        /*    match x {
                0 => 1,
                1 => 1,
                _ => fib(x - 2) + fib(x - 1)
            }
        */}
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test single line uncomment`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        //fn d<caret>ouble(x: i32) -> i32 {
        //    x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
        //  <caret>  x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
        //  <caret>  x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test single line uncomment with space after`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        // fn d<caret>ouble(x: i32) -> i32 {
        //    x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
        //  <caret>  x * 2
        }
    """, """
         fn double(x: i32) -> i32 {
        //   <caret> x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE, trimIndent = false)

    fun `test multi line uncomment`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        //fn doub<selection>le(x: i32) -> i32 {
        //     x</selection> * 2
        }
    """, """
        fn doub<selection>le(x: i32) -> i32 {
            x</selection> * 2
        }
    """, """
        fn doub<selection>le(x: i32) -> i32 {
             x</selection> * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test single line block uncomment`() = checkEditorAction("""
        fn d/*<caret>*/ouble(x: i32) -> i32 {
            x * 2
        }
    """, """
        fn d<caret>ouble(x: i32) -> i32 {
            x * 2
        }
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test multi line block uncomment`() = checkEditorAction("""
        fn doub<selection>/*le(x: i32) -> i32 {
            x*/</selection> * 2
        }
    """, """
        fn doub<selection>le(x: i32) -> i32 {
            x</selection> * 2
        }
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test single line uncomment with space before`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
         //fn d<caret>ouble(x: i32) -> i32 {
        //    x * 2
        }
    """, """
         fn double(x: i32) -> i32 {
        //   <caret> x * 2
        }
    """, """
         fn double(x: i32) -> i32 {
        //   <caret> x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE, trimIndent = false)

    fun `test outer doc comment uncomment`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        /// doc<caret>
        fn double(x: i32) -> i32 {
            x * 2
        }
    """, """
        doc
        fn <caret>double(x: i32) -> i32 {
            x * 2
        }
    """, """
         doc
        fn d<caret>ouble(x: i32) -> i32 {
            x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test inner doc comment uncomment`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        //! doc<caret>
        fn double(x: i32) -> i32 {
            x * 2
        }
    """, """
        doc
        fn <caret>double(x: i32) -> i32 {
            x * 2
        }
    """, """
         doc
        fn d<caret>ouble(x: i32) -> i32 {
            x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test nested block comments`() = checkEditorAction("""
        fn double<selection>(x: i32/* foobar */) -> i32</selection> {
            x * 2
        }
    """, """
        fn double<selection>/*(x: i32/* foobar */) -> i32*/</selection> {
            x * 2
        }
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test indented single line comment`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        fn double(x: i32) -> i32 {
            x<caret> * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            // x * 2
        }<caret>
    """, """
        fn double(x: i32) -> i32 {
            //x * 2
        }<caret>
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test outer doc uncomment inside`() = checkEditorAction("""
        ///
        ///<caret>
        ///
    """, """
        ///

        <caret>///
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test inner doc uncomment inside`() = checkEditorAction("""
        //!
        //!<caret>
        //!
    """, """
        //!

        <caret>//!
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test complete block comment`() = checkEditorAction("""
        /*<caret>
    """, """
        /*
        <caret>
         */
    """, IdeActions.ACTION_EDITOR_ENTER)

    fun `test move caret after commenting empty line`() = checkOption(settings()::LINE_COMMENT_ADD_SPACE, """
        <caret>
        fn foo() {}
    """, """
        // <caret>
        fn foo() {}
    """, """
        //<caret>
        fn foo() {}
    """, IdeActions.ACTION_COMMENT_LINE)

    private fun checkOption(
        optionProperty: KMutableProperty0<Boolean>,
        @Language("Rust") before: String,
        @Language("Rust") afterOn: String,
        @Language("Rust") afterOff: String,
        actionId: String,
        trimIndent: Boolean = true
    ) {
        val initialValue = optionProperty.get()
        optionProperty.set(true)
        try {
            checkEditorAction(before, afterOn, actionId, trimIndent = trimIndent)
            optionProperty.set(false)
            checkEditorAction(before, afterOff, actionId, trimIndent = trimIndent)
        } finally {
            optionProperty.set(initialValue)
        }
    }

    override fun checkEditorAction(
        before: String,
        after: String,
        actionId: String,
        trimIndent: Boolean,
        testmark: Testmark?
    ) {
        super.checkEditorAction(before, after, actionId, trimIndent, testmark)
        resetActionManagerState()
    }

    private fun settings(): CommonCodeStyleSettings = CodeStyle.getSettings(project).getCommonSettings(RsLanguage)

    /**
     * Resets [com.intellij.openapi.actionSystem.impl.ActionManagerImpl.myPrevPerformedActionId].
     * Otherwise it affect [com.intellij.codeInsight.generation.CommentByLineCommentHandler.invoke]
     * (see `startingNewLineComment` condition there).
     */
    private fun resetActionManagerState() {
        myFixture.performEditorAction(EMPTY_ACTION_ID)
    }

    override fun setUp() {
        super.setUp()
        ActionManager.getInstance().registerAction(EMPTY_ACTION_ID, EmptyAction.createEmptyAction("empty", null, true))
    }

    override fun tearDown() {
        ActionManager.getInstance().unregisterAction(EMPTY_ACTION_ID)
        super.tearDown()
    }

    companion object {
        private const val EMPTY_ACTION_ID = "!!!EmptyAction"
    }
}
