package org.rust.lang.refactoring

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.impl.RustFile

class NameSuggestionsKtTest : RustTestCaseBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/introduce_variable/"

    fun testSuggestedNames() = doTest("""
fn foo(a: i32, veryCoolVariableName: i32) {
    a + b
}

fn bar() {
    foo(4, 10/*caret*/ + 2)
}

""") {
        val refactoring = RustIntroduceVariableRefactoring(myFixture.project, myFixture.editor, myFixture.file as RustFile)
        val expr = refactoring.possibleTargets().first()

        assertThat(nameForArgument(project, expr)).isEqualTo("veryCoolVariableName")
        assertThat(suggestedNames(project, expr)).contains("name", "variable_name", "cool_variable_name" ,"very_cool_variable_name")
    }

    private fun doTest(@Language("Rust") before: String, action: () -> Unit) {
        InlineFile(before).withCaret()
        openFileInEditor("main.rs")
        action()
    }


    @Test
    fun testToSnakeCase() {
        assertThat("variableName".toSnakeCase()).isEqualTo("variable_name")
    }

}
