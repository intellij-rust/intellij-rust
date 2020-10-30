/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.lint

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.ide.icons.RsIcons
import org.rust.ide.icons.multiple
import org.rust.lang.RsLanguage
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.completion.RsCompletionProvider
import org.rust.lang.core.completion.withPriority
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.qualifier
import org.rust.lang.core.psiElement
import javax.swing.Icon

abstract class RsLintCompletionProvider : RsCompletionProvider() {
    protected open val prefix: String = ""
    protected abstract val lints: List<Lint>

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val path = parameters.position.parentOfType<RsPath>() ?: return
        val currentPrefix = getPathPrefix(path)
        if (currentPrefix != prefix) return

        lints.forEach {
            addLintToCompletion(result, it)
        }
    }

    protected fun addLintToCompletion(
        result: CompletionResultSet,
        lint: Lint,
        completionText: String? = null
    ) {
        val text = completionText ?: lint.name
        val element = LookupElementBuilder.create(text)
            .withPresentableText(lint.name)
            .withIcon(getIcon(lint))
            .withPriority(getPriority(lint))
        result.addElement(element)
    }

    override val elementPattern: ElementPattern<out PsiElement>
        get() {
            return PlatformPatterns.psiElement()
                .withLanguage(RsLanguage)
                .withParent(RsPath::class.java)
                .inside(
                    psiElement<RsMetaItem>().withSuperParent(2, RsPsiPattern.lintAttributeMetaItem)
                )
        }

    private fun getIcon(lint: Lint): Icon = if (lint.isGroup) {
        GROUP_ICON
    } else {
        RsIcons.ATTRIBUTE
    }

    private fun getPriority(lint: Lint): Double = if (lint.isGroup) {
        GROUP_PRIORITY
    } else {
        LINT_PRIORITY
    }

    protected fun getPathPrefix(path: RsPath): String {
        val qualifier = path.qualifier ?: return path.coloncolon?.text ?: ""
        return "${getPathPrefix(qualifier)}${qualifier.referenceName.orEmpty()}::"
    }

    companion object {
        private const val LINT_PRIORITY = 5.0
        private const val GROUP_PRIORITY = 4.0

        private val GROUP_ICON = RsIcons.ATTRIBUTE.multiple()
    }
}
