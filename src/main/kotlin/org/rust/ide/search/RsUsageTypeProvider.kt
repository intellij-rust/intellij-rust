/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.rust.lang.core.psi.*

object RsUsageTypeProvider : UsageTypeProviderEx {
    // Instantiate each UsageType only once, so that the equality check in UsageTypeGroup.equals() works correctly
    private val IMPL = UsageType("impl")

    private val TYPE_REFERENCE = UsageType("type reference")
    private val TRAIT_REFERENCE = UsageType("trait reference")

    private val EXPR = UsageType("expr")
    private val DOT_EXPR = UsageType("dot expr")

    private val FUNCTION_CALL = UsageType("function call")
    private val METHOD_CALL = UsageType("method call")
    private val ARGUMENT = UsageType("argument")

    private val MACRO_CALL = UsageType("macro call")
    private val MACRO_ARGUMENT = UsageType("macro argument")

    private val INIT_STRUCT = UsageType("init struct")
    private val INIT_FIELD = UsageType("init field")

    private val FIELD = UsageType("field")

    private val META_ITEM = UsageType("meta item")

    private val USE = UsageType("use")
    private val MOD = UsageType("mod")

    override fun getUsageType(element: PsiElement?): UsageType? = getUsageType(element, UsageTarget.EMPTY_ARRAY)

    override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? {
        val parent = element?.goUp<RsPath>() ?: return null
        if (parent is RsBaseType) {
            val context = parent.goUp<RsBaseType>()
            return when (context) {
                is RsTypeReference -> {
                    when (context.parent) {
                        is RsImplItem -> IMPL
                        else -> TYPE_REFERENCE
                    }
                }
                else -> null
            }
        }
        if (parent is RsPathExpr) {
            val context = parent.goUp<RsPathExpr>()
            return when (context) {
                is RsDotExpr -> DOT_EXPR
                is RsCallExpr -> FUNCTION_CALL
                is RsValueArgumentList -> ARGUMENT
                is RsFormatMacroArg -> MACRO_ARGUMENT
                is RsExpr -> EXPR
                else -> null
            }
        }
        return when (parent) {
            is RsUseSpeck -> USE
            is RsStructLiteral -> INIT_STRUCT
            is RsStructLiteralField -> INIT_FIELD
            is RsTraitRef -> TRAIT_REFERENCE
            is RsMethodCall -> METHOD_CALL
            is RsMetaItem -> META_ITEM
            is RsFieldLookup -> FIELD
            is RsMacroCall -> MACRO_CALL
            else -> {
                val context = parent.parent
                when (context) {
                    is RsModDeclItem -> MOD
                    else -> null
                }
            }
        }
    }

    private inline fun <reified T : PsiElement> PsiElement.goUp(): PsiElement {
        var context = this
        while (context is T) {
            context = context.parent
        }
        return context
    }
}
