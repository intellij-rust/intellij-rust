/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

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
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfTypes
import org.rust.RsBundle
import org.rust.lang.RsLanguage
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.collectInferTys
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.testAssert
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class RsInlayTypeHintsProvider : InlayHintsProvider<RsInlayTypeHintsProvider.Settings> {
    override val key: SettingsKey<Settings> get() = KEY

    override val name: String get() = RsBundle.message("settings.rust.inlay.hints.title.types")

    override val previewText: String
        get() = """
            struct Foo<T1, T2, T3> { x: T1, y: T2, z: T3 }

            fn main() {
                let foo = Foo { x: 1, y: "abc", z: true };
            }
            """.trimIndent()

    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {

        override val mainCheckboxText: String
            get() = RsBundle.message("settings.rust.inlay.hints.for")

        /**
         * Each case may have:
         *  * Description provided by [InlayHintsProvider.getProperty].
         *  Property key has `inlay.%[InlayHintsProvider.key].id%.%case.id%` structure
         *
         *  * Preview taken from `resource/inlayProviders/%[InlayHintsProvider.key].id%/%case.id%.rs` file
         */
        override val cases: List<Case>
            get() = listOf(
                Case(RsBundle.message("settings.rust.inlay.hints.for.variables"), "variables", settings::showForVariables),
                Case(RsBundle.message("settings.rust.inlay.hints.for.closures"), "closures", settings::showForLambdas),
                Case(RsBundle.message("settings.rust.inlay.hints.for.loop.variables"), "loop_variables", settings::showForIterators),
                Case(RsBundle.message("settings.rust.inlay.hints.for.type.placeholders"), "type_placeholders", settings::showForPlaceholders),
                Case(RsBundle.message("settings.rust.inlay.hints.for.obvious.types"), "obvious_types", settings::showObviousTypes)
            )

        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector {
        val project = file.project
        val crate = (file as? RsFile)?.crate

        return object : FactoryInlayHintsCollector(editor) {

            val typeHintsFactory = RsTypeHintsPresentationFactory(factory, settings.showObviousTypes)

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (project.service<DumbService>().isDumb) return true
                if (element !is RsElement) return true

                if (element is RsMacroCall) {
                    processMacroCall(element)
                }
                if (settings.showForVariables) {
                    presentVariable(element, isExpanded = false)
                }
                if (settings.showForLambdas) {
                    presentLambda(element, isExpanded = false)
                }
                if (settings.showForIterators) {
                    presentIterator(element, isExpanded = false)
                }

                return true
            }

            private fun processMacroCall(call: RsMacroCall) {
                if (call.getCodeStatus(crate) == RsCodeStatus.CFG_DISABLED) return
                // continue if inside attribute macro

                // Macros with hardcoded PSI don't need special handling and thus are ignored here
                val macroBody = call.macroArgument ?: return
                val traverser = SyntaxTraverser.psiTraverser(macroBody)
                for (leaf in traverser.preOrderDfsTraversal()) {
                    if (leaf !is LeafPsiElement) continue
                    when (leaf.elementType) {
                        RsElementTypes.LET, RsElementTypes.MATCH -> {
                            if (!settings.showForVariables) continue
                            val parentExpanded = leaf.findExpansionElements()?.singleOrNull()?.parent as? RsElement ?: continue
                            presentVariable(parentExpanded, isExpanded = true)
                        }
                        RsElementTypes.FOR -> {
                            if (!settings.showForIterators) continue
                            val parentExpanded = leaf.findExpansionElements()?.singleOrNull()?.parent as? RsForExpr ?: continue
                            presentIterator(parentExpanded, isExpanded = true)
                        }
                        RsElementTypes.OR -> {
                            if (!settings.showForLambdas) continue
                            val leafExpanded = leaf.findExpansionElements()?.singleOrNull() ?: continue
                            val valueParameterList = leafExpanded.parent as? RsValueParameterList ?: continue
                            // Handle expansion only if `leaf` is first `|`
                            if (valueParameterList.stubChildOfElementType(RsElementTypes.OR) != leafExpanded) continue
                            val lambdaExpr = valueParameterList.parent as? RsLambdaExpr ?: continue
                            presentLambda(lambdaExpr, isExpanded = true)
                        }
                    }
                }
            }

            private fun presentVariable(element: RsElement, isExpanded: Boolean) {
                when (element) {
                    is RsLetDecl -> {
                        if (settings.showForPlaceholders) {
                            presentTypePlaceholders(element, isExpanded)
                        }

                        if (element.typeReference != null) return

                        val pat = element.pat ?: return
                        presentTypeForPat(pat, element.expr, isExpanded)
                    }
                    is RsLetExpr -> {
                        val pat = element.pat ?: return
                        presentTypeForPat(pat, element.expr, isExpanded)
                    }
                    is RsMatchExpr -> {
                        for (arm in element.arms) {
                            presentTypeForPat(arm.pat, element.expr, isExpanded)
                        }
                    }
                }
            }

            private fun presentTypePlaceholders(declaration: RsLetDecl, isExpanded: Boolean) {
                run {
                    // optimization - skip processing if no type placeholders
                    val typeReference = declaration.typeReference ?: return
                    if (typeReference.descendantOfTypeOrSelf<RsInferType>() == null) return
                }

                val expandedDeclaration = declaration.findExpandedByLeaf(crate) { it.let } ?: return
                if (isExpanded) testAssert { declaration == expandedDeclaration }
                val inferredType = expandedDeclaration.pat?.type ?: return
                val formalType = expandedDeclaration.typeReference?.rawType ?: return
                val placeholders = formalType.collectInferTys()
                    .mapNotNull {
                        if (it is TyInfer.TyVar && it.origin is RsInferType) {
                            it to it.origin
                        } else {
                            null
                        }
                    }

                val infer = expandedDeclaration.implLookup.ctx
                infer.combineTypes(inferredType, formalType)

                for ((rawType, typeElement) in placeholders) {
                    val type = infer.resolveTypeVarsIfPossible(rawType)
                    if (type is TyInfer || type is TyUnknown) continue

                    val offset = when {
                        // hints inside attribute macro call
                        declaration != expandedDeclaration -> typeElement.findElementExpandedFrom()?.endOffset
                        // hints inside function-like macro call
                        isExpanded -> findOriginalOffset(typeElement, file)
                        else -> typeElement.endOffset
                    } ?: return
                    val presentation = typeHintsFactory.typeHint(type)
                    val finalPresentation = presentation.withDisableAction(declaration.project)
                    sink.addInlineElement(offset, false, finalPresentation, false)
                }
            }

            private fun presentLambda(element: RsElement, isExpanded: Boolean) {
                if (element !is RsLambdaExpr) return

                for (parameter in element.valueParameterList.valueParameterList) {
                    if (parameter.typeReference != null) continue
                    val pat = parameter.pat ?: continue
                    presentTypeForPat(pat, expr = null, isExpanded)
                }
            }

            private fun presentIterator(element: RsElement, isExpanded: Boolean) {
                if (element !is RsForExpr) return

                val pat = element.pat ?: return
                presentTypeForPat(pat, expr = null, isExpanded)
            }

            private fun presentTypeForPat(pat: RsPat, expr: RsExpr?, isExpanded: Boolean) {
                if (!settings.showObviousTypes && isObvious(pat, expr?.declaration)) return

                for (binding in pat.descendantsOfType<RsPatBinding>()) {
                    if (binding.referenceName.startsWith("_")) continue
                    presentTypeForBinding(binding, isExpanded)
                }
            }

            private fun presentTypeForBinding(binding: RsPatBinding, isExpanded: Boolean) {
                val bindingExpanded = binding.findExpandedByLeaf(crate) { it.identifier } ?: return
                if (bindingExpanded.reference.resolve()?.isConstantLike == true) return
                if (bindingExpanded.type is TyUnknown) return

                val offset = if (isExpanded) {
                    testAssert { binding == bindingExpanded }
                    findOriginalOffset(binding.identifier, file) ?: return
                } else {
                    binding.endOffset
                }
                val presentation = typeHintsFactory.typeHint(bindingExpanded.type)
                val finalPresentation = presentation.withDisableAction(project)
                sink.addInlineElement(offset, false, finalPresentation, false)
            }
        }
    }

    private fun InlayPresentation.withDisableAction(project: Project): InsetPresentation = InsetPresentation(
        MenuOnClickPresentation(this, project) {
            listOf(InlayProviderDisablingAction(name, RsLanguage, project, key))
        }, left = 1
    )

    data class Settings(
        var showForVariables: Boolean = true,
        var showForLambdas: Boolean = true,
        var showForIterators: Boolean = true,
        var showForPlaceholders: Boolean = true,
        var showObviousTypes: Boolean = false
    )

    companion object {
        private val KEY: SettingsKey<Settings> = SettingsKey("rust.type.hints")
    }
}

/**
 * Don't show hints in such cases:
 *
 * `let a = MyEnum::A(42);`
 * `let b = MyStruct { x: 42 };`
 */
private fun isObvious(pat: RsPat, declaration: RsElement?): Boolean =
    when (declaration) {
        is RsStructItem, is RsEnumVariant -> pat is RsPatIdent
        else -> false
    }

/**
 * For [anchor] expanded from function-like macro call, finds corresponding offset in [originalFile],
 * but only if macro body has enough context for showing hints:
 *
 * `foo1! { let x = 1; }` expanded to `let x = 1;` - show hints
 * `foo2!(x, 1)` expanded to `let x = 1;` - don't show hints
 */
private fun findOriginalOffset(anchor: PsiElement, originalFile: PsiFile): Int? {
    testAssert { anchor.containingFile != originalFile }  // [anchor] should be expanded

    /*
     * Expansion:
     * ... let x = 1; ...
     * ~~~~~~~~ offset2
     *         ^ anchor
     *     ~~~~~~~~~~ range2
     *
     * Original file:
     * ... foo! { let x = 1; } ...
     * ~~~~~~~~~~~~~~~ offset1
     *            ~~~~~~~~~~ range1
     *
     */
    val parent = anchor.parentOfTypes(RsLetDecl::class, RsLetExpr::class, RsMatchArm::class, RsForExpr::class, RsLambdaExpr::class) ?: return null
    val offset2 = anchor.endOffset
    val range2 = parent.textRange
    testAssert { range2.contains(offset2) }

    val call = anchor.findMacroCallExpandedFromNonRecursive() ?: return null
    val range1 = call.mapRangeFromExpansionToCallBodyStrict(range2) ?: return null
    val offset1 = range1.startOffset + (offset2 - range2.startOffset)
    // Check that [offset1] is expanded only once.
    // Ideally, we should check that [range1] is expanded only once.
    if (originalFile.findElementAt(offset1)?.findExpansionElements()?.size != 1) return null
    return offset1
}

inline fun <reified T : PsiElement> T.findExpandedByLeaf(explicitCrate: Crate? = null, getLeaf: (T) -> PsiElement): T? =
    when (getCodeStatus(explicitCrate)) {
        RsCodeStatus.CFG_DISABLED -> null
        RsCodeStatus.ATTR_PROC_MACRO_CALL -> {
            val leafExpanded = getLeaf(this).findExpansionElements()?.singleOrNull()
            leafExpanded?.parent as? T
        }
        else -> this
    }
