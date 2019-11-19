/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.components.CheckBox
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import javax.swing.JPanel

class RsInlayTypeHintsProvider : InlayHintsProvider<RsInlayTypeHintsProvider.Settings> {
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
        val showForVariables = "Show for variables"
        val showForLambdas = "Show for closures"
        val showForIterators = "Show for iterators"
        val showObviousTypes = "Show obvious types"

        private val varField = CheckBox(showForVariables)
        private val lambdaField = CheckBox(showForLambdas)
        private val iteratorField = CheckBox(showForIterators)
        private val obviousTypesField = CheckBox(showObviousTypes)

        override fun createComponent(listener: ChangeListener): JPanel {
            varField.isSelected = settings.showForVariables
            varField.addItemListener { handleChange(listener) }
            lambdaField.isSelected = settings.showForLambdas
            lambdaField.addItemListener { handleChange(listener) }
            iteratorField.isSelected = settings.showForIterators
            iteratorField.addItemListener { handleChange(listener) }
            obviousTypesField.isSelected = settings.showObviousTypes
            obviousTypesField.addItemListener { handleChange(listener) }

            val panel = panel {
                row { varField(pushX) }
                row { lambdaField(pushX) }
                row { iteratorField(pushX) }
                row { obviousTypesField(pushX) }
            }
            panel.border = JBUI.Borders.empty(5)
            return panel
        }

        private fun handleChange(listener: ChangeListener) {
            settings.showForVariables = varField.isSelected
            settings.showForLambdas = lambdaField.isSelected
            settings.showForIterators = iteratorField.isSelected
            settings.showObviousTypes = obviousTypesField.isSelected
            listener.settingsChanged()
        }
    }

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector? =
        object : FactoryInlayHintsCollector(editor) {

            val typeHintsFactory = RsTypeHintsPresentationFactory(factory, settings.showObviousTypes)

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (file.project.service<DumbService>().isDumb) return true
                if (element !is RsElement) return true
                if (!element.isEnabledByCfg) return true

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
                        if (element.typeReference != null) return
                        val pat = element.pat ?: return
                        presentTypeForPat(pat, element.expr)
                    }
                    is RsCondition -> {
                        for (pat in element.patList) {
                            presentTypeForPat(pat, element.expr)
                        }
                    }
                    is RsMatchExpr -> {
                        for (pat in element.arms.flatMap { it.patList }) {
                            presentTypeForPat(pat, element.expr)
                        }
                    }
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
                sink.addInlineElement(binding.endOffset, false, finalPresentation)
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
