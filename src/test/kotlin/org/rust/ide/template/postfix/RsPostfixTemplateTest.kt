/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.application.runReadAction
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.RsLanguage
import kotlin.reflect.KClass

abstract class RsPostfixTemplateTest(
    postfixTemplateKClass: KClass<out PostfixTemplate>,
    private val postfixTemplateName: String? = null
) : RsTestBase() {
    private val postfixTemplateClass: Class<out PostfixTemplate> = postfixTemplateKClass.java

    fun `test template has documentation`() {
        val description = "postfixTemplates/${postfixTemplateClass.simpleName}/description.html"
        val text = getResourceAsString(description)
            ?: error("No postfix template description for $postfixTemplateClass ($description)")
        checkHtmlStyle(text)

        checkExampleTemplate("before.rs.template")
        checkExampleTemplate("after.rs.template")
    }

    private fun checkExampleTemplate(fileName: String) {
        val path = "postfixTemplates/${postfixTemplateClass.simpleName}/$fileName"
        val text = getResourceAsString(path)
            ?: error("No `$fileName` for ${postfixTemplateClass.simpleName}")
        if (text.isBlank()) {
            error("Please add example text into `$path`")
        }
    }

    protected fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkSyntaxErrors: Boolean = true
    ) = doTestWithAction(before, after, checkSyntaxErrors)

    protected fun doTestWithLiveTemplate(
        @Language("Rust") before: String,
        toType: String,
        @Language("Rust") after: String,
        checkSyntaxErrors: Boolean = true
    ) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        doTestWithAction(before, after, checkSyntaxErrors) {
            assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
            myFixture.type(toType)
            assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        }
    }

    private fun doTestWithAction(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkSyntaxErrors: Boolean = true,
        action: () -> Unit = {}
    ) {
        InlineFile(before.trimIndent()).withCaret()
        checkApplicability(before.trimIndent(), true)
        myFixture.type('\t')
        action()
        if (checkSyntaxErrors) myFixture.checkHighlighting(false, false, false)

        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun doTestNotApplicable(@Language("Rust") testCase: String) {
        InlineFile(testCase).withCaret()
        checkApplicability(testCase, false)
    }

    private fun checkApplicability(testCase: String, isApplicable: Boolean) {
        val template = LanguagePostfixTemplate.LANG_EP.allForLanguage(RsLanguage)
            .flatMap { it.templates }
            .first { template ->
                template.javaClass == postfixTemplateClass &&
                    (postfixTemplateName == null || postfixTemplateName == template.presentableName)
            }

        val result = runReadAction {
            PostfixLiveTemplate.isApplicableTemplate(
                template.provider ?: error("Template provider must not be null. Template: $postfixTemplateClass"),
                template.key,
                myFixture.file,
                myFixture.editor
            )
        }

        check(result == isApplicable) {
            "postfixTemplate ${if (isApplicable) "should" else "shouldn't"} be applicable to given case:\n\n$testCase"
        }
    }
}
