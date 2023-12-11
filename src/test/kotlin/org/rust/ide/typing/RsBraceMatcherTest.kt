/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.highlighting.BraceMatchingUtil.getMatchedBraceOffset
import com.intellij.openapi.editor.ex.EditorEx
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsBraceMatcherTest : RsTestBase() {
    fun `test don't pair parenthesis before identifier`() = doTest(
        "fn main() { let _ = /*caret*/typing; }",
        '(',
        "fn main() { let _ = (/*caret*/typing; }"
    )

    fun `test don't pair parenthesis before string literal`() = doTest(
        """fn main() { let _ = /*caret*/"foo"; }""",
        '(',
        """fn main() { let _ = (/*caret*/"foo"; }"""
    )

    fun `test don't pair parenthesis before integral literal`() = doTest(
        "fn main() { let _ = /*caret*/1; }",
        '(',
        "fn main() { let _ = (/*caret*/1; }"
    )

    fun `test don't pair parenthesis before ref operator`() = doTest(
        "fn main() { let _ = /*caret*/&foo; }",
        '(',
        "fn main() { let _ = (/*caret*/&foo; }"
    )

    fun `test don't pair parenthesis before dereference operator`() = doTest(
        "fn main() { let _ = /*caret*/*foo.bar; }",
        '(',
        "fn main() { let _ = (/*caret*/*foo.bar; }"
    )

    fun `test don't pair parenthesis before not operator`() = doTest(
        "fn main() { let _ = /*caret*/!foo; }",
        '(',
        "fn main() { let _ = (/*caret*/!foo; }"
    )

    fun `test don't pair parenthesis before unary minus operator`() = doTest(
        "fn main() { let _ = /*caret*/-foo; }",
        '(',
        "fn main() { let _ = (/*caret*/-foo; }"
    )

    fun `test don't pair parenthesis before box operator`() = doTest(
        "fn main() { let _ = /*caret*/box foo; }",
        '(',
        "fn main() { let _ = (/*caret*/box foo; }"
    )

    fun `test don't pair parenthesis before if expression`() = doTest(
        "fn main() { let _ = /*caret*/if foo { bar } else { baz }; }",
        '(',
        "fn main() { let _ = (/*caret*/if foo { bar } else { baz }; }"
    )

    fun `test don't pair parenthesis before match expression`() = doTest(
        "fn main() { let _ = /*caret*/match foo { _ => () }; }",
        '(',
        "fn main() { let _ = (/*caret*/match foo { _ => () }; }"
    )

    fun `test don't pair braces before let statement`() = doTest(
        "fn main() { /*caret*/let _ = 1; }",
        '{',
        "fn main() { {/*caret*/let _ = 1; }"
    )

    fun `test don't pair braces before while expression`() = doTest(
        "fn main() { /*caret*/while foo {} }",
        '{',
        "fn main() { {/*caret*/while foo {} }"
    )

    fun `test don't pair braces before if expression`() = doTest(
        "fn main() { /*caret*/if foo { bar; } else { baz; } }",
        '{',
        "fn main() { {/*caret*/if foo { bar; } else { baz; } }"
    )

    fun `test don't pair braces before for expression`() = doTest(
        "fn main() { /*caret*/for a in b {} }",
        '{',
        "fn main() { {/*caret*/for a in b {} }"
    )

    fun `test don't pair braces before loop expression`() = doTest(
        "fn main() { /*caret*/loop {} }",
        '{',
        "fn main() { {/*caret*/loop {} }"
    )

    fun `test don't pair braces before match expression`() = doTest(
        "fn main() { /*caret*/match foo { _ => () } }",
        '{',
        "fn main() { {/*caret*/match foo { _ => () } }"
    )

    fun `test pair parenthesis before semicolon`() = doTest(
        "fn main() { let _ = /*caret*/; }",
        '(',
        "fn main() { let _ = (/*caret*/); }"
    )

    fun `test pair parenthesis before brace`() = doTest(
        "fn foo/*caret*/{}",
        '(',
        "fn foo(/*caret*/){}"
    )

    fun `test pair parenthesis deletion simple`() = doTest(
        "fn foo(){(/*caret*/)}",
        '\b',
        "fn foo(){/*caret*/}"
    )

    fun `test pair parenthesis deletion after bracket`() = doTest(
        "fn foo(){[];(/*caret*/)}",
        '\b',
        "fn foo(){[];/*caret*/}"
    )

    fun `test pair parenthesis before bracket`() = doTest(
        "fn main() { let _ = &[foo/*caret*/]; }",
        '(',
        "fn main() { let _ = &[foo(/*caret*/)]; }"
    )

    fun `test match parenthesis`() = doMatch("fn foo/*caret*/(x: (i32, ()) ) {}", ")")

    fun `test match square brackets`() = doMatch("fn foo(x: /*caret*/[i32; 192]) {}", "]")

    fun `test match angle brackets`() {
        InlineFile("""
            type R = Result<Foo, /*comment*/ Box<[T; 92]>  b   >;

            fn foo<'a, T: Clone, K: Clone + Debug>(x: Y, y: K) {}

            fn bar<T: IntoFuture<Item=Result<(), ()>>>() {
                let x = xs.map().collect::<self::foo::Vec<_>>();
                let x = xs.map().collect::<::Vec<super::Result<&mut String, * const i32>>>();
                let x:Punctuated<Ident,Token![|]>;
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
                getMatchedBraceOffset(myFixture.editor, forward, myFixture.file)
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
        noMatch("let a = 4 /*caret*/< 5 && 2 > 1;")
        noMatch("let a = (2 /*caret*/< 3 || 3 > 2);")
        noMatch("fn foo() { let _ = 1 /*caret*/< 2; let _ = 1 > 2;}")
        noMatch("fn a() { 1 /*caret*/< 2 } fn b() { 1 > 2 }")
    }

    private fun noMatch(source: String) {
        InlineFile(source)
        val editorHighlighter = (myFixture.editor as EditorEx).highlighter
        val iterator = editorHighlighter.createIterator(myFixture.editor.caretModel.offset)
        val matched = BraceMatchingUtil.matchBrace(myFixture.editor.document.charsSequence, myFixture.file.fileType, iterator, true)
        check(!matched)
    }

    private fun doMatch(source: String, coBrace: String) {
        InlineFile(source)
        val expected = source.replace("/*caret*/", "").lastIndexOf(coBrace)
        check(getMatchedBraceOffset(myFixture.editor, true, myFixture.file) == expected)
    }

    private fun doTest(@Language("Rust") before: String, type: Char, @Language("Rust") after: String) {
        InlineFile(before)
        myFixture.type(type)
        myFixture.checkResult(replaceCaretMarker(after))
    }
}

