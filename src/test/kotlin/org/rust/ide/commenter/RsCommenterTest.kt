/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.commenter

import com.intellij.openapi.actionSystem.IdeActions
import org.rust.RsTestBase

class RsCommenterTest : RsTestBase() {

    fun `test single line`() = checkEditorAction("""
        fn <caret>double(x: i32) -> i32 {
            x * 2
        }
    """, """
        // fn double(x: i32) -> i32 {
            x <caret>* 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test multi line`() = checkEditorAction("""
        fn doub<selection>le(x: i32) -> i32 {
            x</selection> * 2
        }
    """, """
        // fn doub<selection>le(x: i32) -> i32 {
        //     x</selection> * 2
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
            /*
            match x {
                0 => 1,
                1 => 1,
                _ => fib(x - 2) + fib(x - 1)
            }
            */
        }
    """, IdeActions.ACTION_COMMENT_BLOCK)

    fun `test single line uncomment`() = checkEditorAction("""
        //fn d<caret>ouble(x: i32) -> i32 {
        //    x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
        //  <caret>  x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test multi line uncomment`() = checkEditorAction("""
        //fn doub<selection>le(x: i32) -> i32 {
        //     x</selection> * 2
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

    fun `test single line uncomment with space`() = checkEditorAction("""
        // fn d<caret>ouble(x: i32) -> i32 {
        //     x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
        //  <caret>   x * 2
        }
    """, IdeActions.ACTION_COMMENT_LINE)

    fun `test nested block comments`() = checkEditorAction("""
        fn double<selection>(x: i32/* foobar */) -> i32</selection> {
            x * 2
        }
    """, """
        fn double<selection>/*(x: i32*//* foobar *//*) -> i32*/</selection> {
            x * 2
        }
    """, IdeActions.ACTION_COMMENT_BLOCK) // FIXME

    fun `test indented single line comment`() = checkEditorAction("""
        fn double(x: i32) -> i32 {
            x<caret> * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            // x * 2
        }<caret>
    """, IdeActions.ACTION_COMMENT_LINE)
}
