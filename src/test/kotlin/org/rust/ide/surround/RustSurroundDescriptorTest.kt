package org.rust.ide.surround

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import org.rust.lang.RustTestCaseBase

class RustSurroundDescriptorTest : RustTestCaseBase() {

    val parenthesisSurrounder = DelimiterSurrounder("(", ")", "Surround with ()")
    val bracesSurrounder = DelimiterSurrounder("{", "}", "Surround with {}")

    override val dataPath = ""

    fun testSurroundParenthesis() = doTest(
        parenthesisSurrounder,
        "fn main() { let a = { let inner = 3; <selection>inner * inner</selection> };}",
        "fn main() { let a = { let inner = 3; (inner * inner) };}"
    )

    fun testSurroundbraces() = doTest(
        bracesSurrounder,
        "fn main() { let a = { let inner = 3; <selection>inner * inner</selection> };}",
        "fn main() { let a = { let inner = 3; {inner * inner} };}"
    )

    fun testSelectPartOfExpression() = doTest(
        parenthesisSurrounder,
        "fn main() { let a = { let inner = 3; inner <selection>* inner</selection> };}",
        "fn main() { let a = { let inner = 3; inner * inner };}"
    )

    fun testSelectStatement() = doTest(
        parenthesisSurrounder,
        "fn main() { let a = { <selection>let inner = 3;</selection> inner * inner };}",
        "fn main() { let a = { let inner = 3; inner * inner };}"
    )

    fun testSpacing() = doTest(
        parenthesisSurrounder,
        "fn main() {let _ = <selection>1 + 1</selection>; }",
        "fn main() {let _ = (1 + 1); }"
    )

    private fun doTest(surrounder: Surrounder, before: String, after: String) {
        myFixture.configureByText(fileName, before)
        perform(surrounder)
        myFixture.checkResult(after)
    }

    private fun perform(surrounder: Surrounder) {
        WriteCommandAction.runWriteCommandAction(null, {
            SurroundWithHandler.invoke(project, myFixture.editor, myFixture.file, surrounder)
            PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)
            PsiDocumentManager.getInstance(project)
                .doPostponedOperationsAndUnblockDocument(myFixture.getDocument(myFixture.file))
        })
    }
}
