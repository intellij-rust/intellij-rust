/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
@file:Suppress("UnstableApiUsage")

package org.rust.ide.hints.type

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.components.CheckBox
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import javax.swing.JPanel

class RsChainMethodTypeHintsProvider : InlayHintsProvider<RsChainMethodTypeHintsProvider.Settings> {
    override val key: SettingsKey<Settings> get() = KEY

    override val name: String get() = "Chain method hints"

    override val previewText: String? = null

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        private val consecutiveField = CheckBox("Show same consecutive types")
        private val iteratorSpecialCase = CheckBox("Show iterators as `impl Iterator<...>`")

        override fun createComponent(listener: ChangeListener): JPanel {
            consecutiveField.isSelected = settings.showSameConsecutiveTypes
            consecutiveField.addItemListener { handleChange(listener) }
            iteratorSpecialCase.isSelected = settings.iteratorSpecialCase
            iteratorSpecialCase.addItemListener { handleChange(listener) }

            val panel = panel {
                row { consecutiveField(pushX) }
                row { iteratorSpecialCase(pushX) }
            }
            panel.border = JBUI.Borders.empty(5)
            return panel
        }

        private fun handleChange(listener: ChangeListener) {
            settings.showSameConsecutiveTypes = consecutiveField.isSelected
            settings.iteratorSpecialCase = iteratorSpecialCase.isSelected
            listener.settingsChanged()
        }
    }

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector? =
        object : FactoryInlayHintsCollector(editor) {

            val typeHintsFactory = RsTypeHintsPresentationFactory(
                if (settings.iteratorSpecialCase) file as? RsElement else null,
                factory,
                true
            )

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (DumbService.isDumb(element.project)) return true
                if (element !is RsMethodCall) return true
                if (!element.isLastInChain) return true
                if (!element.isEnabledByCfg) return true

                val chain = collectChain(element)
                var lastType: Ty? = null
                for (call in chain.dropLast(1)) {
                    if (call.type != TyUnknown && call.isLastOnLine) {
                        if (settings.showSameConsecutiveTypes || call.type != lastType) {
                            presentTypeForMethodCall(call)
                        }
                        lastType = call.type
                    }
                }

                return true
            }

            private fun presentTypeForMethodCall(call: RsMethodCall) {
                val project = call.project
                val type = call.type
                val presentation = typeHintsFactory.typeHint(type)
                val finalPresentation = presentation.withDisableAction(project)
                sink.addInlineElement(call.endOffset, true, finalPresentation)
            }
        }

    private fun InlayPresentation.withDisableAction(project: Project): InsetPresentation = InsetPresentation(
        MenuOnClickPresentation(this, project) {
            listOf(InlayProviderDisablingAction(name, RsLanguage, project, key))
        }, left = 1
    )

    data class Settings(
        var showSameConsecutiveTypes: Boolean = true,
        var iteratorSpecialCase: Boolean = true
    )

    companion object {
        val KEY: SettingsKey<Settings> = SettingsKey("chain-method.hints")
    }
}

private val RsMethodCall.isLastInChain: Boolean
    get() = parentDotExpr.parent !is RsDotExpr && parentDotExpr.expr.childOfType<RsMethodCall>() != null

private val RsMethodCall.isLastOnLine: Boolean
    get() = this.parentDotExpr.isLastOnLine

private val PsiElement.isLastOnLine: Boolean
    get() {
        return when (val sibling = this.nextSibling) {
            is PsiWhiteSpace -> sibling.textContains('\n') || sibling.isLastOnLine
            is PsiComment -> sibling.isLastOnLine
            else -> false
        }
    }

private val RsMethodCall.type: Ty
    get() = parentDotExpr.type


private fun collectChain(call: RsMethodCall): List<RsMethodCall> {
    val chain = mutableListOf<RsMethodCall>()
    var current = call
    while (true) {
        chain.add(current)
        current = current.parentDotExpr.expr.childOfType() ?: break
    }
    return chain.reversed()
}
