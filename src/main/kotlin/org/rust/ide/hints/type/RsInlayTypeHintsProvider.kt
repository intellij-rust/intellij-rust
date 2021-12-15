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
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.collectInferTys
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
abstract class RsInlayTypeHintsProviderBase : InlayHintsProvider<RsInlayTypeHintsProviderBase.Settings> {
    override val key: SettingsKey<Settings> get() = KEY

    override val name: String get() = "Type hints"

    override val previewText: String
        get() = """
            struct Foo<T1, T2, T3> { x: T1, y: T2, z: T3 }

            fn main() {
                let foo = Foo { x: 1, y: "abc", z: true };
            }
            """.trimIndent()

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {

        override val cases: List<Case>
            get() = listOf(
                Case("Show for variables", "variables", settings::showForVariables),
                Case("Show for closures", "closures", settings::showForLambdas),
                Case("Show for loop variables", "loop_variables", settings::showForIterators),
                Case("Show for type placeholders", "type_placeholders", settings::showForPlaceholders),
                Case("Show obvious types", "obvious_types", settings::showObviousTypes)
            )

        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector =
        object : FactoryInlayHintsCollector(editor) {

            val typeHintsFactory = RsTypeHintsPresentationFactory(factory, settings.showObviousTypes)

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (file.project.service<DumbService>().isDumb) return true
                if (element !is RsElement) return true
                if (!element.existsAfterExpansion) return true

                if (settings.showForVariables) {
                    presentVariable(element)
                }
                if (settings.showForLambdas) {
                    presentLambda(element)
                }
                if (settings.showForIterators) {
                    presentIterator(element)
                }

                return true
            }

            private fun presentVariable(element: RsElement) {
                when (element) {
                    is RsLetDecl -> {
                        if (settings.showForPlaceholders) {
                            presentTypePlaceholders(element)
                        }

                        if (element.typeReference != null) return

                        val pat = element.pat ?: return
                        presentTypeForPat(pat, element.expr)
                    }
                    is RsCondition -> {
                        val pat = element.pat ?: return
                        presentTypeForPat(pat, element.expr)
                    }
                    is RsMatchExpr -> {
                        for (arm in element.arms) {
                            presentTypeForPat(arm.pat, element.expr)
                        }
                    }
                }
            }

            private fun presentTypePlaceholders(declaration: RsLetDecl) {
                val inferredType = declaration.pat?.type ?: return
                val formalType = declaration.typeReference?.type ?: return
                val placeholders = formalType.collectInferTys()
                    .mapNotNull {
                        if (it is TyInfer.TyVar && it.origin is RsBaseType) {
                            it to it.origin
                        } else {
                            null
                        }
                    }

                val infer = declaration.implLookup.ctx
                infer.combineTypes(inferredType, formalType)

                for ((rawType, typeElement) in placeholders) {
                    if (typeElement.underscore == null) continue
                    val type = infer.resolveTypeVarsIfPossible(rawType)
                    if (type is TyInfer || type is TyUnknown) continue

                    val presentation = typeHintsFactory.typeHint(type)
                    val finalPresentation = presentation.withDisableAction(declaration.project)
                    sink.addInlineElement(typeElement.endOffset, false, finalPresentation, false)
                }
            }

            private fun presentLambda(element: RsElement) {
                if (element !is RsLambdaExpr) return

                for (parameter in element.valueParameterList.valueParameterList) {
                    if (parameter.typeReference != null) continue
                    val pat = parameter.pat ?: continue
                    presentTypeForPat(pat)
                }
            }

            private fun presentIterator(element: RsElement) {
                if (element !is RsForExpr) return

                val pat = element.pat ?: return
                presentTypeForPat(pat)
            }

            private fun presentTypeForPat(pat: RsPat, expr: RsExpr? = null) {
                if (!settings.showObviousTypes && isObvious(pat, expr?.declaration)) return

                for (binding in pat.descendantsOfType<RsPatBinding>()) {
                    if (binding.referenceName.startsWith("_")) continue
                    if (binding.reference.resolve()?.isConstantLike == true) continue
                    if (binding.type is TyUnknown) continue

                    presentTypeForBinding(binding)
                }
            }

            private fun presentTypeForBinding(binding: RsPatBinding) {
                val project = binding.project
                val presentation = typeHintsFactory.typeHint(binding.type)
                val finalPresentation = presentation.withDisableAction(project)
                sink.addInlineElement(binding.endOffset, false, finalPresentation, false)
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
