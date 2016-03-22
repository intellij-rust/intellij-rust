package org.rust.lang.core.parser

import com.intellij.lang.LanguageBraceMatching
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls
import org.rust.ide.highlight.matcher.RustBraceMatcher
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCase
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.RustParserDefinition

abstract class RustParsingTestCaseBase(@NonNls dataPath: String)
    : ParsingTestCase("org/rust/lang/core/parser/fixtures/" + dataPath, "rs", true /*lowerCaseFirstLetter*/, RustParserDefinition())
    , RustTestCase {

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

    override fun getTestName(lowercaseFirstLetter: Boolean): String? {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return RustTestCaseBase.camelToSnake(camelCase);
    }

    override fun setUp() {
        super.setUp()
        addExplicitExtension(LanguageBraceMatching.INSTANCE, RustLanguage, RustBraceMatcher())
    }

    override fun tearDown() {
        super.tearDown()
    }
}
