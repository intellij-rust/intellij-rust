/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.macro.MacroBase
import com.intellij.openapi.util.text.StringUtil
import org.rust.lang.refactoring.RsNamesValidator

class RsCollectionElementNameMacro : MacroBase("rustCollectionElementName", "rustCollectionElementName()") {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext, quick: Boolean): Result? {
        var param = getCollectionExprStr(params, context) ?: return null

        val lastDot = param.lastIndexOf('.')
        if (lastDot >= 0) {
            param = param.substring(lastDot + 1)
        }

        val lastDoubleColon = param.lastIndexOf("::")
        if (lastDoubleColon > 0) {
            param = param.substring(0, lastDoubleColon)
        }

        if (param.endsWith(')')) {
            val lastParen = param.lastIndexOf('(')
            if (lastParen > 0) {
                param = param.substring(0, lastParen)
            }
        }

        val name = unpluralize(param) ?: return null
        if (RsNamesValidator().isIdentifier(name, context.project)) {
            return TextResult(name)
        } else {
            return null
        }
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext): Array<out LookupElement>? {
        val result = calculateResult(params, context) ?: return null
        val words = result.toString().split('_')
        if (words.size > 1) {
            val lookups = mutableListOf<LookupElement>()
            for (i in words.indices) {
                val element = words.subList(i, words.size).joinToString("_")
                lookups.add(LookupElementBuilder.create(element))
            }
            return lookups.toTypedArray()
        } else {
            return null
        }
    }

    companion object {
        private fun getCollectionExprStr(params: Array<out Expression>, context: ExpressionContext): String? =
            params.singleOrNull()?.calculateResult(context)?.toString()

        private fun unpluralize(name: String): String? {
            for (prefix in SUFFIXES) {
                if (name.endsWith(prefix)) {
                    return name.substring(0, name.length - prefix.length)
                }
            }
            return StringUtil.unpluralize(name)
        }

        private val SUFFIXES = arrayOf("_list", "_set")
    }
}
