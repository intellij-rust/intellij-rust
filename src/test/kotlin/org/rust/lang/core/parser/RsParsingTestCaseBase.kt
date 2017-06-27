/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.LanguageBraceMatching
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls
import org.rust.ide.typing.RsBraceMatcher
import org.rust.lang.RsLanguage
import org.rust.lang.RsTestCase
import org.rust.lang.RsTestBase

abstract class RsParsingTestCaseBase(@NonNls dataPath: String)
    : ParsingTestCase("org/rust/lang/core/parser/fixtures/" + dataPath, "rs", true /*lowerCaseFirstLetter*/, RustParserDefinition())
      , RsTestCase {

    protected fun hasError(file: PsiFile): Boolean {
        var hasErrors = false
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is PsiErrorElement) {
                    hasErrors = true
                    return
                }
                element!!.acceptChildren(this)
            }
        })
        return hasErrors
    }


    override fun getTestDataPath(): String = "src/test/resources"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return RsTestBase.camelOrWordsToSnake(camelCase)
    }

    override fun setUp() {
        super.setUp()
        addExplicitExtension(LanguageBraceMatching.INSTANCE, RsLanguage, RsBraceMatcher())
    }
}
