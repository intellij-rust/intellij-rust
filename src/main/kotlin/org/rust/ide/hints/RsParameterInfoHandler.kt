/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.stdext.buildList

/**
 * Provides functions/methods arguments hint.
 */
class RsParameterInfoHandler : ParameterInfoHandler<PsiElement, RsArgumentsDescription> {

    var hintText: String = ""

    override fun couldShowInLookup() = true

    override fun tracksParameterIndex() = true

    override fun getParameterCloseChars() = ",)"

    override fun getParametersForLookup(item: LookupElement, context: ParameterInfoContext?): Array<out Any>? {
        val el = item.`object` as? PsiElement ?: return null
        val p = el.parent?.parent ?: return null
        val isCall = p is RsCallExpr && CallInfo.resolve(p) != null || p is RsMethodCall && CallInfo.resolve(p) != null
        return if (isCall) arrayOf(p) else emptyArray()
    }

    override fun getParametersForDocumentation(p: RsArgumentsDescription, context: ParameterInfoContext?) =
        p.arguments

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val contextElement = context.file.findElementAt(context.editor.caretModel.offset) ?: return null
        return findElementForParameterInfo(contextElement)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext) =
        context.file.findElementAt(context.editor.caretModel.offset)

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        if (element !is RsValueArgumentList) return
        val argsDescr = RsArgumentsDescription.findDescription(element) ?: return
        context.itemsToShow = arrayOf(argsDescr)
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun updateParameterInfo(place: PsiElement, context: UpdateParameterInfoContext) {
        val argIndex = findArgumentIndex(place)
        if (argIndex == INVALID_INDEX) {
            context.removeHint()
            return
        }
        context.setCurrentParameter(argIndex)
        when {
            context.parameterOwner == null -> context.parameterOwner = place
            context.parameterOwner != findElementForParameterInfo(place) -> {
                context.removeHint()
                return
            }
        }
        context.objectsToView.indices.map { context.setUIComponentEnabled(it, true) }
    }

    override fun updateUI(p: RsArgumentsDescription?, context: ParameterInfoUIContext) {
        if (p == null) {
            context.isUIComponentEnabled = false
            return
        }
        val range = p.getArgumentRange(context.currentParameterIndex)
        hintText = p.presentText
        context.setupUIComponentPresentation(
            hintText,
            range.startOffset,
            range.endOffset,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor)
    }

    private fun findElementForParameterInfo(contextElement: PsiElement) =
        contextElement.ancestorStrict<RsValueArgumentList>()

    /**
     * Finds index of the argument in the given place
     */
    private fun findArgumentIndex(place: PsiElement): Int {
        val callArgs = place.ancestorStrict<RsValueArgumentList>() ?: return INVALID_INDEX
        val descr = RsArgumentsDescription.findDescription(callArgs) ?: return INVALID_INDEX
        var index = -1
        if (descr.arguments.isNotEmpty()) {
            index += generateSequence(callArgs.firstChild, { c -> c.nextSibling })
                .filter { it.text == "," }
                .count({ it.textRange.startOffset < place.textRange.startOffset }) + 1
            if (index >= descr.arguments.size) {
                index = -1
            }
        }
        return index
    }

    private companion object {
        val INVALID_INDEX: Int = -2
    }
}

/**
 * Holds information about arguments from func/method declaration
 */
class RsArgumentsDescription(
    val arguments: Array<String>
) {
    fun getArgumentRange(index: Int): TextRange {
        if (index < 0 || index >= arguments.size) return TextRange.EMPTY_RANGE
        val start = arguments.take(index).sumBy { it.length + 2 }
        return TextRange(start, start + arguments[index].length)
    }

    val presentText = if (arguments.isEmpty()) "<no arguments>" else arguments.joinToString(", ")

    companion object {
        /**
         * Finds declaration of the func/method and creates description of its arguments
         */
        fun findDescription(args: RsValueArgumentList): RsArgumentsDescription? {
            val call = args.parent
            val callInfo = when (call) {
                is RsCallExpr -> CallInfo.resolve(call)
                is RsMethodCall -> CallInfo.resolve(call)
                else -> null
            } ?: return null
            val params = buildList<String> {
                if (callInfo.selfParameter != null && call is RsCallExpr) {
                    add(callInfo.selfParameter)
                }
                addAll(callInfo.parameters.map { "${it.pattern}: ${it.type}" })
            }
            return RsArgumentsDescription(params.toTypedArray())
        }
    }
}
