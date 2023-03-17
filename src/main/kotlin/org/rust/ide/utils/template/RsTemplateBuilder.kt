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
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.intentions.util.macros.IntentionInMacroUtil
import org.rust.ide.intentions.util.macros.RsIntentionInsideMacroExpansionEditor
import org.rust.lang.core.macros.srcRange
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.startOffset

/**
 * A wrapper for [TemplateBuilder][com.intellij.codeInsight.template.TemplateBuilder]
 */
@Suppress("UnstableApiUsage", "unused")
class RsTemplateBuilder(
    private val hostPsiFile: PsiFile,
    private val editor: Editor,
    private val hostEditor: Editor,
) {
    private val hostDocument: Document get() = hostEditor.document
    private val elementsToReplace: MutableList<RsTemplateElement> = mutableListOf()
    private val variables: MutableMap<String, TemplateVariable> = mutableMapOf()
    private val usageToVar: MutableMap<String, String> = mutableMapOf()
    private var variableCounter: Int = 0
    private var highlightExpressions: Boolean = false
    private var disableDaemonHighlighting: Boolean = false
    private val listeners: MutableList<TemplateEditingListener> = mutableListOf()

    private fun replaceElement(
        range: RangeMarker?,
        expression: Expression?,
        variableName: String? = null,
        alwaysStopAt: Boolean = true
    ) {
        if (range != null) {
            elementsToReplace += RsTemplateElement(range, expression, variableName, alwaysStopAt)
        }
    }

    private fun psiToRangeMarker(
        element: PsiElement,
        rangeWithinElement: TextRange = TextRange(0, element.textLength)
    ): RangeMarker? {
        val absoluteRange = rangeWithinElement.shiftRight(element.startOffset)
        val range = if (editor is RsIntentionInsideMacroExpansionEditor && element.containingFile == editor.psiFileCopy) {
            val mutableContext = editor.context ?: return null
            mutableContext.rangeMap.mapTextRangeFromExpansionToCallBody(absoluteRange).singleOrNull()?.srcRange
                ?.shiftRight(mutableContext.rootMacroCallBodyOffset)
                ?: return null
            // TODO injection
        } else {
            InjectedLanguageManager.getInstance(element.project).injectedToHost(element, absoluteRange)
        }
        return hostDocument.createRangeMarker(range)
    }

    fun replaceElement(element: PsiElement, replacementText: String? = null): RsTemplateBuilder {
        replaceElement(
            psiToRangeMarker(element),
            replacementText?.let { ConstantNode(it) }
        )
        return this
    }

    fun replaceElement(element: PsiElement, rangeWithinElement: TextRange, replacementText: String? = null): RsTemplateBuilder {
        replaceElement(
            psiToRangeMarker(element, rangeWithinElement),
            replacementText?.let { ConstantNode(it) }
        )
        return this
    }


    fun replaceElement(element: PsiElement, expression: Expression): RsTemplateBuilder {
        replaceElement(psiToRangeMarker(element), expression)
        return this
    }

    fun replaceElement(element: PsiElement, rangeWithinElement: TextRange, expression: Expression): RsTemplateBuilder {
        replaceElement(psiToRangeMarker(element, rangeWithinElement), expression)
        return this
    }


    private fun replaceElement(element: PsiElement, variable: TemplateVariable, replacementText: String?): RsTemplateBuilder {
        replaceElement(
            psiToRangeMarker(element),
            replacementText?.let { ConstantNode(it) },
            variable.name,
            alwaysStopAt = true
        )
        return this
    }

    fun introduceVariable(element: PsiElement, replacementText: String? = null): TemplateVariable {
        val variable = newVariable()
        replaceElement(element, variable, replacementText)
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

    private fun withFinishResultListener(onFinish: () -> Unit): RsTemplateBuilder {
        return withResultListener {
            if (it == TemplateResultListener.TemplateResult.Finished) {
                onFinish()
            }
        }
    }

    private fun doPostponedOperationsAndCommit(editor: Editor) {
        PsiDocumentManager.getInstance(hostPsiFile.project).apply {
            doPostponedOperationsAndUnblockDocument(editor.document)
            commitDocument(editor.document)
        }
    }

    fun runInline() {
        val project = hostPsiFile.project

        if (editor is RsIntentionInsideMacroExpansionEditor) {
            if (editor.context?.broken == true) return
            IntentionInMacroUtil.finishActionInMacroExpansionCopy(editor)
        }

        doPostponedOperationsAndCommit(editor)
        if (editor != hostEditor) {
            doPostponedOperationsAndCommit(hostEditor)
        }

        if (elementsToReplace.isEmpty()) {
            return
        }

        var commonTextRange: TextRange = elementsToReplace.first().range.textRange

        val elements = elementsToReplace.map {
            val range = it.range.textRange
            it.range.dispose()
            commonTextRange = commonTextRange.union(range)
            RsUnwrappedTemplateElement(range, it.expression, it.variableName, it.alwaysStopAt)
        }

        val hostOwner = hostPsiFile.findElementAt(commonTextRange.startOffset)
            ?.ancestors
            ?.find { it.textRange.contains(commonTextRange) }
            ?: return

        val delegate = TemplateBuilderFactory.getInstance().createTemplateBuilder(hostOwner) as TemplateBuilderImpl

        for (element in elements) {
            val expression = element.expression
                ?: ConstantNode(element.range.subSequence(hostDocument.immutableCharSequence).toString())
            val relRange = element.range.shiftLeft(hostOwner.startOffset)
            if (element.variableName != null) {
                delegate.replaceElement(hostOwner, relRange, element.variableName, expression, element.alwaysStopAt)
            } else {
                delegate.replaceElement(hostOwner, relRange, expression)
            }
        }

        // From TemplateBuilderImpl.run()
        val template = delegate.buildInlineTemplate()
        hostEditor.caretModel.moveToOffset(hostOwner.startOffset)
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

    fun runInline(onFinish: () -> Unit) {
        withFinishResultListener(onFinish)
        runInline()
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
            val h = RsTemplateHighlighting(hostEditor, HighlightManager.getInstance(hostPsiFile.project), varToUsages)
            h.highlightVariablesAt(templateState, template, 0)
            templateState.addTemplateStateListener(h)
            Disposer.register(templateState, h)
        }
    }

    private class RsTemplateElement(
        val range: RangeMarker,
        val expression: Expression?,
        val variableName: String?,
        val alwaysStopAt: Boolean,
    )

    private class RsUnwrappedTemplateElement(
        val range: TextRange,
        val expression: Expression?,
        val variableName: String?,
        val alwaysStopAt: Boolean,
    )

    inner class TemplateVariable(val name: String) {
        private var dependentVarCounter: Int = 0

        fun replaceElementWithVariable(element: PsiElement) {
            replaceElement(
                psiToRangeMarker(element),
                VariableNode(name, null),
                variableName = newSubsequentVariable(),
                alwaysStopAt = false
            )
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
