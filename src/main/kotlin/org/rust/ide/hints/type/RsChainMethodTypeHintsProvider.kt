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
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAnon
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.escaped
import javax.swing.JComponent
import javax.swing.JPanel

class RsChainMethodTypeHintsProvider : InlayHintsProvider<RsChainMethodTypeHintsProvider.Settings> {
    override val key: SettingsKey<Settings> get() = KEY

    override val name: String get() = RsBundle.message("settings.rust.inlay.hints.title.method.chains")

    override val previewText: String? = null

    override val group: InlayGroup
        get() = InlayGroup.METHOD_CHAINS_GROUP

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        override val mainCheckboxText: String
            get() = RsBundle.message("settings.rust.inlay.hints.for")
        override val cases: List<Case>
            get() = listOf(
                Case(RsBundle.message("settings.rust.inlay.hints.for.same.consecutive.types"), "consecutive_types", settings::showSameConsecutiveTypes),
                // New inlay hint settings consider case name as html, as a result `<...>` isn't rendered properly.
                // So let's escape it if needed
                Case(RsBundle.message("settings.rust.inlay.hints.for.iterators").escapeIfNeeded(), "iterators", settings::iteratorSpecialCase)
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
        val crate = (file as? RsFile)?.crate

        return object : FactoryInlayHintsCollector(editor) {
            val typeHintsFactory = RsTypeHintsPresentationFactory(factory, true)

            private val lookupAndIteratorTrait: Pair<ImplLookup?, BoundElement<RsTraitItem>?> by lazy(LazyThreadSafetyMode.PUBLICATION) {
                val (lookup, items) = (file as? RsFile)?.implLookupAndKnownItems ?: (null to null)
                val iterator = items?.Iterator?.let { BoundElement(it) }
                lookup to iterator
            }

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (DumbService.isDumb(project)) return true
                if (element !is RsMethodCall) return true
                if (!element.isLastInChain) return true
                val isAttrProcMacro = when (element.getCodeStatus(crate)) {
                    RsCodeStatus.CFG_DISABLED -> return true
                    RsCodeStatus.ATTR_PROC_MACRO_CALL -> true
                    else -> false
                }

                val (lookup, iterator) = lookupAndIteratorTrait

                val chain = collectChain(element)
                val chainExpanded = if (isAttrProcMacro) {
                    collectExpandedChain(element)
                        ?.takeIf { it.size == chain.size } ?: return true
                } else {
                    chain
                }
                var lastType: Ty? = null
                for ((call, callExpanded) in (chain zip chainExpanded).dropLast(1)) {
                    if (!call.isLastOnLine) continue
                    val type = normalizeType(callExpanded.type, lookup, iterator)
                    if (type != TyUnknown) {
                        if (settings.showSameConsecutiveTypes || !type.isEquivalentTo(lastType)) {
                            presentTypeForMethodCall(call, type)
                        }
                        lastType = type
                    }
                }

                return true
            }

            private fun presentTypeForMethodCall(call: RsMethodCall, type: Ty) {
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

        @Nls
        private fun String.escapeIfNeeded(): String = if (isNewSettingsEnabled) escaped else this

        private val isNewSettingsEnabled: Boolean
            get() {
                return Registry.`is`("new.inlay.settings", false)
            }
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

private fun collectExpandedChain(call: RsMethodCall): List<RsMethodCall>? {
    val leaf = call.identifier
    val leafExpanded = leaf.findExpansionElements()?.singleOrNull() ?: return null
    val callExpanded = leafExpanded.parent as? RsMethodCall ?: return null
    return collectChain(callExpanded)
}
