/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.LanguageBraceMatching
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ParsingTestCase
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.annotations.NonNls
import org.rust.RsTestBase
import org.rust.RsTestCase
import org.rust.ide.typing.RsBraceMatcher
import org.rust.lang.RsLanguage
import java.io.File

abstract class RsParsingTestCaseBase(@NonNls dataPath: String) : ParsingTestCase(
    "org/rust/lang/core/parser/fixtures/$dataPath",
    "rs",
    /*lowerCaseFirstLetter = */ true ,
    RustParserDefinition()
), RsTestCase {

    private lateinit var platformPrefix: String

    override fun setUp() {
        super.setUp()
        PlatformTestCase.doAutodetectPlatformPrefix()
        val info = ApplicationInfoImpl.getShadowInstance()
        platformPrefix = info.majorVersion.takeLast(2) + info.minorVersion.take(1)
        addExplicitExtension(LanguageBraceMatching.INSTANCE, RsLanguage, RsBraceMatcher())
    }

    override fun getTestDataPath(): String = "src/test/resources"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return RsTestBase.camelOrWordsToSnake(camelCase)
    }

    override fun checkResult(targetDataName: String, file: PsiFile) {
        val path = if (File("$myFullDataPath/$testName.txt").exists()) {
            targetDataName
        } else {
            "$platformPrefix/$targetDataName"
        }
        super.checkResult(path, file)
    }

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
}
