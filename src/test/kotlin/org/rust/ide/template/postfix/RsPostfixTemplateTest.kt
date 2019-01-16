/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.application.runReadAction
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.RsLanguage

abstract class RsPostfixTemplateTest(val postfixTemplate: PostfixTemplate) : RsTestBase() {

    fun `test template has documentation`() {
        val description = "postfixTemplates/${postfixTemplate.javaClass.simpleName}/description.html"
        val text = getResourceAsString(description)
            ?: error("No postfix template description for ${postfixTemplate.javaClass} ($description)")
        checkHtmlStyle(text)

        checkExampleTemplate("before.rs.template")
        checkExampleTemplate("after.rs.template")
    }

    private fun checkExampleTemplate(fileName: String) {
        val path = "postfixTemplates/${postfixTemplate.javaClass.simpleName}/$fileName"
        val text = getResourceAsString(path)
            ?: error("No `$fileName` for ${postfixTemplate.javaClass.simpleName}")
        if (text.isBlank()) {
            error("Please add example text into `$path`")
        }
    }

    protected fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkSyntaxErrors: Boolean = true
    ) {
        InlineFile(before.trimIndent()).withCaret()
        checkApplicability(before.trimIndent(), true)
        myFixture.type('\t')
        if (checkSyntaxErrors) myFixture.checkHighlighting(false, false, false)

        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun doTestNotApplicable(@Language("Rust") testCase: String) {
        InlineFile(testCase).withCaret()
        checkApplicability(testCase, false)
    }

    private fun checkApplicability(testCase: String, isApplicable: Boolean) {
        val provider = LanguagePostfixTemplate.LANG_EP.allForLanguage(RsLanguage)
            .first { descriptor ->
                descriptor.templates.any { template ->
                    template.javaClass == this.postfixTemplate.javaClass
                }
            }

        val result = runReadAction {
            PostfixLiveTemplate.isApplicableTemplate(
                provider,
                postfixTemplate.key,
                myFixture.file,
                myFixture.editor
            )
        }

        check(result == isApplicable) {
            "postfixTemplate ${if (isApplicable) "should" else "shouldn't"} be applicable to given case:\n\n$testCase"
        }
    }
}
