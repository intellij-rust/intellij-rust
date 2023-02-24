/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.startOffset
import org.rust.stdext.buildList

/**
 * Provides functions/methods arguments hint.
 */
class RsParameterInfoHandler : RsAsyncParameterInfoHandler<RsValueArgumentList, RsArgumentsDescription>() {
    override fun findTargetElement(file: PsiFile, offset: Int): RsValueArgumentList? =
        file.findElementAt(offset)?.ancestorStrict()

    override fun calculateParameterInfo(element: RsValueArgumentList): Array<RsArgumentsDescription>? {
        return RsArgumentsDescription.findDescription(element)?.let { arrayOf(it) }
    }

    override fun updateParameterInfo(parameterOwner: RsValueArgumentList, context: UpdateParameterInfoContext) {
        if (context.parameterOwner != parameterOwner) {
            context.removeHint()
            return
        }
        val currentParameterIndex = if (parameterOwner.startOffset == context.offset) {
            -1
        } else {
            ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.node, context.offset, RsElementTypes.COMMA)
        }
        context.setCurrentParameter(currentParameterIndex)
    }

    override fun updateUI(p: RsArgumentsDescription, context: ParameterInfoUIContext) {
        val range = p.getArgumentRange(context.currentParameterIndex)
        context.setupUIComponentPresentation(
            p.presentText,
            range.startOffset,
            range.endOffset,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor)
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
        val start = arguments.take(index).sumOf { it.length + 2 }
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
            val params = buildList {
                if (callInfo.selfParameter != null && call is RsCallExpr) {
                    add(callInfo.selfParameter)
                }
                addAll(callInfo.parameters.map {
                    buildString {
                        if (it.pattern != null) {
                            append("${it.pattern}: ")
                        }
                        append(it.renderType())
                    }
                })
            }
            return RsArgumentsDescription(params.toTypedArray())
        }
    }
}
