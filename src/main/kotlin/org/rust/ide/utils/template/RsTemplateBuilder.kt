/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.template

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.startOffset

/**
 * A wrapper for [TemplateBuilder][com.intellij.codeInsight.template.TemplateBuilder]
 */
@Suppress("UnstableApiUsage", "unused", "MemberVisibilityCanBePrivate", "SameParameterValue")
class RsTemplateBuilder(
    private val owner: PsiElement,
    private val delegate: TemplateBuilderImpl,
    private val editor: Editor,
    private val hostEditor: Editor,
) {
    private val variables: MutableMap<String, TemplateVariable> = mutableMapOf()
    private val usageToVar: MutableMap<String, String> = mutableMapOf()
    private var variableCounter: Int = 0
    private var highlightExpressions: Boolean = false
    private var disableDaemonHighlighting: Boolean = false
    private val listeners: MutableList<TemplateEditingListener> = mutableListOf()

    fun replaceElement(element: PsiElement, replacementText: String? = null): RsTemplateBuilder {
        delegate.replaceElement(element, replacementText ?: element.text)
        return this
    }

    fun replaceElement(element: PsiElement, rangeWithinElement: TextRange, replacementText: String): RsTemplateBuilder {
        delegate.replaceElement(element, rangeWithinElement, replacementText)
        return this
    }


    fun replaceElement(element: PsiElement, expression: Expression): RsTemplateBuilder {
        delegate.replaceElement(element, expression)
        return this
    }

    fun replaceElement(element: PsiElement, rangeWithinElement: TextRange, expression: Expression): RsTemplateBuilder {
        delegate.replaceElement(element, rangeWithinElement, expression)
        return this
    }


    fun replaceRange(rangeWithinElement: TextRange, replacementText: String): RsTemplateBuilder {
        delegate.replaceRange(rangeWithinElement, replacementText)
        return this
    }

    fun replaceRange(rangeWithinElement: TextRange, expression: Expression): RsTemplateBuilder {
        delegate.replaceRange(rangeWithinElement, expression)
        return this
    }


    private fun replaceElement(element: PsiElement, variable: TemplateVariable, replacementText: String, alwaysStopAt: Boolean): RsTemplateBuilder {
        delegate.replaceElement(element, variable.name, ConstantNode(replacementText), alwaysStopAt)
        return this
    }

    fun introduceVariable(element: PsiElement, replacementText: String? = null): TemplateVariable {
        val variable = newVariable()
        replaceElement(element, variable, replacementText ?: element.text, true)
        return variable
    }

    private fun newVariable(): TemplateVariable {
        var name: String
        do {
            variableCounter++
            name = "variable$variableCounter"
        } while (variables[name] != null)
        return newVariable(name)
    }

    private fun newVariable(name: String): TemplateVariable {
        if (variables[name] != null) error("The variable `$variables` is already defined")
        val variable = TemplateVariable(name)
        variables[name] = variable
        return variable
    }

    fun withExpressionsHighlighting(): RsTemplateBuilder {
        highlightExpressions = true
        return this
    }

    fun withDisabledDaemonHighlighting(): RsTemplateBuilder {
        disableDaemonHighlighting = true
        return this
    }

    fun withListener(listener: TemplateEditingListener): RsTemplateBuilder {
        listeners += listener
        return this
    }

    fun withResultListener(listener: (TemplateResultListener.TemplateResult) -> Unit): RsTemplateBuilder {
        return withListener(TemplateResultListener(listener))
    }

    fun withFinishResultListener(listener: () -> Unit): RsTemplateBuilder {
        return withResultListener {
            if (it == TemplateResultListener.TemplateResult.Finished) {
                listener()
            }
        }
    }

    fun runInline() {
        val project = owner.project

        // From TemplateBuilderImpl.run()
        val template = delegate.buildInlineTemplate()
        editor.caretModel.moveToOffset(owner.startOffset)
        val templateState = TemplateManager.getInstance(project).runTemplate(hostEditor, template)

        val isAlreadyFinished = templateState.isFinished // Can be true in unit tests
        for (listener in listeners) {
            if (isAlreadyFinished) {
                listener.templateFinished(template, false)
            } else {
                templateState.addTemplateStateListener(listener)
            }
        }

        if (isAlreadyFinished) return

        if (highlightExpressions) {
            setupUsageHighlighting(templateState, template)
        }

        if (disableDaemonHighlighting) {
            DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(templateState)
        }
    }

    private fun setupUsageHighlighting(templateState: TemplateState, template: Template) {
        val varToUsages = mutableMapOf<String, MutableSet<Int>>()
        for (i in 0 until templateState.segmentsCount) {
            val variableName = template.getSegmentName(i)
            if (variableName !in variables) {
                val parentVarName = usageToVar[variableName]
                if (parentVarName != null) {
                    varToUsages.computeIfAbsent(parentVarName) { mutableSetOf() }.add(i)
                }
            }
        }

        if (varToUsages.isNotEmpty()) {
            val h = RsTemplateHighlighting(hostEditor, HighlightManager.getInstance(owner.project), varToUsages)
            h.highlightVariablesAt(templateState, template, 0)
            templateState.addTemplateStateListener(h)
            Disposer.register(templateState, h)
        }
    }

    inner class TemplateVariable(val name: String) {
        private var dependentVarCounter: Int = 0

        fun replaceElementWithVariable(element: PsiElement) {
            delegate.replaceElement(element, newSubsequentVariable(), VariableNode(name, null), false)
        }

        fun replaceElementWithVariable(element: PsiElement, rangeWithinElement: TextRange) {
            delegate.replaceElement(element, rangeWithinElement, newSubsequentVariable(), VariableNode(name, null), false)
        }

        private fun newSubsequentVariable(): String {
            val dependentVar = name + "_" + dependentVarCounter
            dependentVarCounter++
            usageToVar[dependentVar] = name
            return dependentVar
        }
    }

    class RsTemplateHighlighting(
        private val hostEditor: Editor,
        private val highlightManager: HighlightManager,
        private val varToUsages: Map<String, Set<Int>>,
    ) : TemplateEditingAdapter(), Disposable {
        private val highlighters: MutableList<RangeHighlighter> = mutableListOf()

        fun highlightVariablesAt(templateState: TemplateState, template: Template, index: Int) {
            releaseHighlighters()
            val key = EditorColors.SEARCH_RESULT_ATTRIBUTES
            val name = (template as TemplateImpl).getVariableNameAt(index)
            for (i in varToUsages[name].orEmpty()) {
                val range = templateState.getSegmentRange(i)
                highlightManager.addOccurrenceHighlight(hostEditor, range.startOffset, range.endOffset, key, 0, highlighters)
            }
        }

        override fun currentVariableChanged(templateState: TemplateState, template: Template, oldIndex: Int, newIndex: Int) {
            if (newIndex >= 0) {
                highlightVariablesAt(templateState, template, newIndex)
            }
        }

        override fun dispose() {
            releaseHighlighters()
        }

        private fun releaseHighlighters() {
            for (highlighter in highlighters) {
                highlightManager.removeSegmentHighlighter(hostEditor, highlighter)
            }
            highlighters.clear()
        }
    }
}
