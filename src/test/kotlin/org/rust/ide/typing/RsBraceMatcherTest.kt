package org.rust.ide.typing

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.highlighting.BraceMatchingUtil.getMatchedBraceOffset
import com.intellij.openapi.editor.ex.EditorEx
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RsFileType
import org.rust.lang.RsTestBase

class RsBraceMatcherTest : RsTestBase() {
    override val dataPath: String get() = ""

    fun testBeforeIdent() = doTest(
        "fn main() { let _ = <caret>typing }",
        '(',
        "fn main() { let _ = (<caret>typing }"
    )

    fun testBeforeSemicolon() = doTest(
        "fn main() { let _ = <caret>; }",
        '(',
        "fn main() { let _ = (<caret>); }"
    )

    fun testBeforeBrace() = doTest(
        "fn foo<caret>{}",
        '(',
        "fn foo(<caret>){}"
    )

    fun testNoMatch() {
        noMatch("let a = 4 <caret>< 5 && 2 > 1;")
        noMatch("let a = (2 <caret>< 3 || 3 > 2);")
    }

    fun testMatchingGeneric() {
        doMatch("Result<caret><Foo, Bar>")
        doMatch("Result<caret><Foo, /* X*/ Bar>")
        doMatch("Result<caret><Foo, /* X*/ Bar>")
        doMatch("struct K<caret><R //fwf\n > {}")
        doMatch("fn create() -> Box<caret><[T]> {}")
        doMatch("pub struct Reader<caret><'a, T: 'a> {}")
        doMatch("fn print_area<caret><T: HasArea>(shape: T)")
        doMatch("fn foo<caret><T: Clone, K: Clone + Debug>(x: T, y: K) {}")
        doMatch("fn normal<caret><T: ConvertTo<i64>>(x: &T) {}")
    }

    private fun noMatch(source: String) {
        myFixture.configureByText(RsFileType, source)
        val editorHighlighter = (myFixture.editor as EditorEx).highlighter
        val iterator = editorHighlighter.createIterator(myFixture.editor.caretModel.offset)
        val matched = BraceMatchingUtil.matchBrace(myFixture.editor.document.charsSequence, myFixture.file.fileType, iterator, true)
        assertThat(matched).`as`(source).isFalse()
    }

    private fun doMatch(source: String) {
        myFixture.configureByText(RsFileType, source)
        assertThat(getMatchedBraceOffset(myFixture.editor, true, myFixture.file))
            .isEqualTo(source.replace("<caret>", "").lastIndexOf('>'))
    }

    private fun doTest(before: String, type: Char, after: String) {
        myFixture.configureByText(RsFileType, before)
        myFixture.type(type)
        myFixture.checkResult(after)
    }
}

