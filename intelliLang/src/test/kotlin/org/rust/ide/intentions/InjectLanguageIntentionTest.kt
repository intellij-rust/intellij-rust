/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.injection.Injectable
import org.intellij.lang.annotations.Language
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.rust.lang.core.psi.ext.ancestorOrSelf

class InjectLanguageIntentionTest : RsIntentionTestBase(InjectLanguageAction()) {
    fun `test available inside string`() = checkAvailable("""
        const C: &str = "/*caret*/";
    """)

    fun `test available before string`() = checkAvailable("""
        const C: &str = /*caret*/"123";
    """)

    fun `test unavailable inside number`() = doUnavailableTest("""
        const C: i32 = /*caret*/123;
    """)

    fun `test inject RegExp`() = doTest("RegExp", """
        const C: &str = /*caret*/"abc(def)";
    """)

    private fun checkAvailable(@Language("Rust") code: String) {
        InlineFile(code.trimIndent()).withCaret()
        check(intention.isAvailable(project, myFixture.editor, myFixture.file)) {
            "Intention is not available"
        }
    }

    private fun doTest(lang: String, @Language("Rust") code: String) {
        InlineFile(code.trimIndent()).withCaret()
        val language = InjectedLanguage.findLanguageById(lang)
        val injectable = Injectable.fromLanguage(language)
        InjectLanguageAction.invokeImpl(project, myFixture.editor, myFixture.file, injectable)
        val host = findInjectionHost(myFixture.editor, myFixture.file)
            ?: error("InjectionHost not found")
        val injectedList = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host)
            ?: error("Language injection failed")
        assertEquals(injectedList.size, 1)
        val injectedPsi = injectedList.first().first
        assertEquals(injectedPsi.language, language)
    }
}

private fun findInjectionHost(editor: Editor, file: PsiFile): PsiLanguageInjectionHost? {
    if (editor is EditorWindow) return null
    val offset = editor.caretModel.offset
    val vp = file.viewProvider
    for (language in vp.languages) {
        val host = vp.findElementAt(offset, language)
            ?.ancestorOrSelf<PsiLanguageInjectionHost>()
        if (host != null && host.isValidHost) {
            return host
        }
    }
    return null
}
