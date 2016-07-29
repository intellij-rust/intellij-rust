package org.rust.ide.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCaseBase
import java.io.File
import java.util.*

abstract class RustSurrounderTestCaseBase(val surrounder: Surrounder) : RustTestCaseBase() {
    override val dataPath: String get() = "org/rust/ide/surroundWith/fixtures/$testClassName"

    protected val testClassName: String =
        surrounder.javaClass.simpleName.replace("^RustWith(\\w+)Surrounder$".toRegex(), "$1").decapitalize()

    protected fun doTest() = checkByFile {
        checkApplicability(fileName, true)

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            SurroundWithHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, surrounder)
        }
    }

    protected fun doTestNotApplicable() {
        val fileName = "notApplicable/$fileName"
        myFixture.configureByFile(fileName)
        checkApplicability(fileName, false)
    }

    private fun checkApplicability(fileName: String, isApplicable: Boolean) {
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
                FileUtil.loadFile(File("$testDataPath/$fileName")),
                Arrays.toString(elements)
            )
            .isEqualTo(isApplicable)
    }
}
