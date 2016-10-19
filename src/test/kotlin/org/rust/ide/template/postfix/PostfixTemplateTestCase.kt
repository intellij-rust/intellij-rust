package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.rust.lang.RustFileType
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCaseBase

abstract class PostfixTemplateTestCase(val postfixTemplate: PostfixTemplate) : RustTestCaseBase() {
    override val dataPath = ""

    protected fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        myFixture.configureByText(RustFileType, before)
        checkApplicability(before, true)
        myFixture.type('\t')
        myFixture.checkResult(after)
    }

    protected fun doTestNotApplicable(@Language("Rust") testCase: String) {
        myFixture.configureByText(RustFileType, testCase)
        checkApplicability(testCase, false)
    }

    private fun checkApplicability(testCase: String, isApplicable: Boolean) {
        val provider = LanguagePostfixTemplate.LANG_EP.allForLanguage(RustLanguage)
            .first { descriptor ->
                descriptor.templates.any { surrounder ->
                    surrounder.javaClass == this.postfixTemplate.javaClass
                }
            }

        Assertions.assertThat(
            PostfixLiveTemplate.isApplicableTemplate(
                provider,
                postfixTemplate.key,
                myFixture.file,
                myFixture.editor
            )
        )
            .withFailMessage(
                "postfixTemplate %s be applicable to given case:\n\n%s",
                if (isApplicable) "should" else "shouldn't",
                testCase
            )
            .isEqualTo(isApplicable)
    }
}
