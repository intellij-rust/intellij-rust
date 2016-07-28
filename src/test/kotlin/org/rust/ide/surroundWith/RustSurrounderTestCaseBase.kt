package org.rust.ide.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.readText
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCaseBase
import java.nio.file.Paths
import java.util.*

abstract class RustSurrounderTestCaseBase(val surrounder: Surrounder) : RustTestCaseBase() {
    override val dataPath: String get() = "org/rust/ide/surroundWith/fixtures/$testClassName"

    protected val testClassName: String =
        surrounder.javaClass.simpleName.replace(Regex("^RustWith(\\w+)Surrounder$"), "$1").decapitalize()

    protected fun doTest(isApplicable: Boolean = true) {
        val before = if (isApplicable) fileName else "notApplicable/$fileName"
        myFixture.configureByFile(before)

        val descriptor = LanguageSurrounders.INSTANCE.allForLanguage(RustLanguage)
            .first { descriptor ->
                descriptor.surrounders.any { surrounder ->
                    surrounder.javaClass == this.surrounder.javaClass
                }
            }

        val selectionModer = myFixture.editor.selectionModel
        val elements = descriptor.getElementsToSurround(
            myFixture.file, selectionModer.selectionStart, selectionModer.selectionEnd)

        assertThat(surrounder.isApplicable(elements))
            .withFailMessage(
                "surrounder %s be applicable to given selection:\n\n%s\nElements: %s",
                if (isApplicable) "should" else "shouldn't",
                Paths.get("$testDataPath/$before").readText(),
                Arrays.toString(elements)
            )
            .isEqualTo(isApplicable)

        if (isApplicable) {
            WriteCommandAction.runWriteCommandAction(myFixture.project) {
                SurroundWithHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, surrounder)
            }

            val after = before.replace(".rs", "_after.rs")
            myFixture.checkResultByFile(after, true)
        }
    }
}
