/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.value

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.ImmediateConfigurable.Case
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPatRange
import org.rust.lang.core.psi.RsRangeExpr
import org.rust.lang.core.psi.ext.*
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class RsInlayValueHintsProvider : InlayHintsProvider<RsInlayValueHintsProvider.Settings> {
    override val key: SettingsKey<Settings> get() = KEY

    override val name: String get() = RsBundle.message("settings.rust.inlay.hints.title.values")

    override val previewText: String? = null

    override val group: InlayGroup
        get() = InlayGroup.VALUES_GROUP

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {

        override val mainCheckboxText: String
            get() = RsBundle.message("settings.rust.inlay.hints.for")

        override val cases: List<Case>
            get() = listOf(
                Case(RsBundle.message("settings.rust.inlay.hints.for.exclusive.range.expressions"), "exclusive_range_expressions", settings::showForExpressions),
                Case(RsBundle.message("settings.rust.inlay.hints.for.exclusive.range.patterns"), "exclusive_range_patterns", settings::showForPatterns)
            )

        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        val project = file.project

        return object : FactoryInlayHintsCollector(editor) {

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (project.service<DumbService>().isDumb) return true
                if (element !is RsElement) return true

                if (element is RsMacroCall) {
                    // TODO: support macro calls
                    return false
                }
                if (settings.showForExpressions) {
                    presentExpression(element)
                }
                if (settings.showForPatterns) {
                    presentPattern(element)
                }

                return true
            }

            private fun presentExpression(expr: RsElement) {
                if (expr !is RsRangeExpr || !expr.isEnabledByCfg) return
                val end = getPresentationInfos(expr.end?.startOffset, expr.op) ?: return
                sink.addInlineElement(end.offset, false, end.presentation, false)
            }

            private fun presentPattern(pat: RsElement) {
                if (pat !is RsPatRange || !pat.isEnabledByCfg) return
                val end = getPresentationInfos(pat.end?.startOffset, pat.op) ?: return
                sink.addInlineElement(end.offset, false, end.presentation, false)
            }

            private fun getPresentationInfos(offsetEnd: Int?, range: PsiElement?): PresentationInfo? {
                val textEnd = when (range?.elementType) {
                    RsElementTypes.DOTDOT -> "<"
                    else -> null
                }
                if (textEnd == null || offsetEnd == null) return null
                val presentation = factory.roundWithBackground(factory.text(textEnd)).withDisableAction(project)
                return PresentationInfo(presentation, offsetEnd)
            }
        }
    }

    private fun InlayPresentation.withDisableAction(project: Project): InsetPresentation = InsetPresentation(
        MenuOnClickPresentation(this, project) {
            listOf(InlayProviderDisablingAction(name, RsLanguage, project, key))
        }, left = 1
    )

    data class Settings(
        var showForExpressions: Boolean = true,
        var showForPatterns: Boolean = true
    )

    data class PresentationInfo(
        val presentation: InlayPresentation,
        val offset: Int
    )

    companion object {
        private val KEY: SettingsKey<Settings> = SettingsKey("rust.value.range.exclusive.hints")
    }
}
