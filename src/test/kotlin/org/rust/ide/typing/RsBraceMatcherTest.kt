/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.highlighting.BraceMatchingUtil.getMatchedBraceOffset
import com.intellij.openapi.editor.ex.EditorEx
import org.rust.lang.RsFileType
import org.rust.lang.RsTestBase

class RsBraceMatcherTest : RsTestBase() {
    fun `test don't pair parenthesis before identifier`() = doTest(
        "fn main() { let _ = <caret>typing }",
        '(',
        "fn main() { let _ = (<caret>typing }"
    )

    fun `test pair parenthesis before semicolon`() = doTest(
        "fn main() { let _ = <caret>; }",
        '(',
        "fn main() { let _ = (<caret>); }"
    )

    fun `test pair parenthesis before brace`() = doTest(
        "fn foo<caret>{}",
        '(',
        "fn foo(<caret>){}"
    )

    fun `test match parenthesis`() = doMatch("fn foo<caret>(x: (i32, ()) ) {}", ")")

    fun `test match square brackets`() = doMatch("fn foo(x: <caret>[i32; 192]) {}", "]")

    fun `test match angle brackets`() {
        InlineFile("""
            type R = Result<Foo, /*comment*/ Box<[T; 92]>  b   >;

            fn foo<'a, T: Clone, K: Clone + Debug>(x: Y, y: K) {}

            fn bar<T: IntoFuture<Item=Result<(), ()>>>() {
                let x = xs.map().collect::<self::foo::Vec<_>>();
                let x = xs.map().collect::<::Vec<super::Result<&mut String, * const i32>>>();
            }
        """)

        val text = myFixture.file.text
        for ((offset, brace) in text.withIndex()) {
            if (brace !in "<>") continue
            val parent = myFixture.file.findElementAt(offset)!!.parent!!
            myFixture.editor.caretModel.moveToOffset(offset)
            val forward = brace == '<'
            val coBrace = if (forward) '>' else '<'

            val pairOffset = try {
                BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, forward, myFixture.file)
            } catch (e: AssertionError) {
                error("Failed to find a pair for `$brace` in `${parent.text}`")
            }
            check(text[pairOffset] == coBrace)
            val pairParent = myFixture.file.findElementAt(pairOffset)!!.parent
            check(parent == pairParent) {
                "parent of `$brace` is\n${parent.text}\nParent of `$coBrace` is\n${pairParent.text}"
            }
        }
    }

    fun `test no match`() {
        noMatch("let a = 4 <caret>< 5 && 2 > 1;")
        noMatch("let a = (2 <caret>< 3 || 3 > 2);")
        noMatch("fn foo() { let _ = 1 <caret>< 2; let _ = 1 > 2;}")
        noMatch("fn a() { 1 <caret>< 2 } fn b() { 1 > 2 }")
    }

    private fun noMatch(source: String) {
        myFixture.configureByText(RsFileType, source)
        val editorHighlighter = (myFixture.editor as EditorEx).highlighter
        val iterator = editorHighlighter.createIterator(myFixture.editor.caretModel.offset)
        val matched = BraceMatchingUtil.matchBrace(myFixture.editor.document.charsSequence, myFixture.file.fileType, iterator, true)
        check(!matched)
    }

    private fun doMatch(source: String, coBrace: String) {
        myFixture.configureByText(RsFileType, source)
        val expected = source.replace("<caret>", "").lastIndexOf(coBrace)
        check(getMatchedBraceOffset(myFixture.editor, true, myFixture.file) == expected)
    }

    private fun doTest(before: String, type: Char, after: String) {
        myFixture.configureByText(RsFileType, before)
        myFixture.type(type)
        myFixture.checkResult(after)
    }
}

