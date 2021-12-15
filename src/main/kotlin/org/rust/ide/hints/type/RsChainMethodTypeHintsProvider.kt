/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
@file:Suppress("UnstableApiUsage")

package org.rust.ide.hints.type

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.ImmediateConfigurable.Case
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
import org.rust.RsBundle
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAnon
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import javax.swing.JComponent
import javax.swing.JPanel

abstract class RsChainMethodTypeHintsProviderBase : InlayHintsProvider<RsChainMethodTypeHintsProviderBase.Settings> {
    override val key: SettingsKey<Settings> get() = KEY

    override val name: String get() = RsBundle.message("settings.rust.inlay.hints.title.method.chains")

    override val previewText: String? = null

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        override val mainCheckboxText: String
            get() = RsBundle.message("settings.rust.inlay.hints.for")
        override val cases: List<Case>
            get() = listOf(
                Case(RsBundle.message("settings.rust.inlay.hints.for.same.consecutive.types"), "consecutive_types", settings::showSameConsecutiveTypes),
                Case(RsBundle.message("settings.rust.inlay.hints.for.iterators"), "iterators", settings::iteratorSpecialCase)
            )

        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ): InlayHintsCollector =
        object : FactoryInlayHintsCollector(editor) {
            val typeHintsFactory = RsTypeHintsPresentationFactory(factory, true)

            private val lookupAndIteratorTrait: Pair<ImplLookup?, BoundElement<RsTraitItem>?> by lazy(LazyThreadSafetyMode.PUBLICATION) {
                val (lookup, items) = (file as? RsFile)?.implLookupAndKnownItems ?: null to null
                val iterator = items?.Iterator?.let { BoundElement(it) }
                lookup to iterator
            }

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (DumbService.isDumb(element.project)) return true
                if (element !is RsMethodCall) return true
                if (!element.isLastInChain) return true
                if (!element.existsAfterExpansion) return true

                val (lookup, iterator) = lookupAndIteratorTrait

                val chain = collectChain(element)
                var lastType: Ty? = null
                for (call in chain.dropLast(1)) {
                    val type = normalizeType(call.type, lookup, iterator)
                    if (type != TyUnknown && call.isLastOnLine) {
                        if (settings.showSameConsecutiveTypes || !type.isEquivalentTo(lastType)) {
                            presentTypeForMethodCall(call, type)
                        }
                        lastType = type
                    }
                }

                return true
            }

            private fun presentTypeForMethodCall(call: RsMethodCall, type: Ty) {
                val project = call.project
                val presentation = typeHintsFactory.typeHint(type)
                val finalPresentation = presentation.withDisableAction(project)
                sink.addInlineElement(call.endOffset, true, finalPresentation, false)
            }

            /**
             * Returns fake impl Iterator<Item=...> type if [type] implements the Iterator trait and
             * `iteratorSpecialCase` setting is enabled.
             * */
            private fun normalizeType(type: Ty, lookup: ImplLookup?, iteratorTrait: BoundElement<RsTraitItem>?): Ty {
                if (!settings.iteratorSpecialCase || iteratorTrait == null) return type

                val assoc = lookup?.selectAllProjectionsStrict(TraitRef(type, iteratorTrait)) ?: return type
                return TyAnon(null, listOf(iteratorTrait.copy(assoc = assoc)))
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
