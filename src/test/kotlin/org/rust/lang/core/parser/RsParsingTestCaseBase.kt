/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.core.CoreInjectedLanguageManager
import com.intellij.lang.LanguageBraceMatching
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import org.rust.RsTestCase
import org.rust.TestCase
import org.rust.ide.typing.RsBraceMatcher
import org.rust.lang.RsLanguage

@RunWith(JUnit38ClassRunner::class) // TODO: drop the annotation when issue with Gradle test scanning go away
abstract class RsParsingTestCaseBase(@NonNls dataPath: String) : ParsingTestCase(
    "org/rust/lang/core/parser/fixtures/$dataPath",
    "rs",
    /*lowerCaseFirstLetter = */ true ,
    RustParserDefinition()
), RsTestCase {

    override fun setUp() {
        super.setUp()
        addExplicitExtension(LanguageBraceMatching.INSTANCE, RsLanguage, RsBraceMatcher())
        project.registerService(InjectedLanguageManager::class.java, CoreInjectedLanguageManager::class.java)
    }

    override fun getTestDataPath(): String = "src/test/resources"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return TestCase.camelOrWordsToSnake(camelCase)
    }

    protected fun hasError(file: PsiFile): Boolean {
        var hasErrors = false
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiErrorElement) {
                    hasErrors = true
                    return
                }
                element.acceptChildren(this)
            }
        })
        return hasErrors
    }

    /** Just check that the file is parsed (somehow) without checking its AST */
    protected fun checkFileParsed() {
        val name = testName
        parseFile(name, loadFile("$name.$myFileExt"));
    }
}
