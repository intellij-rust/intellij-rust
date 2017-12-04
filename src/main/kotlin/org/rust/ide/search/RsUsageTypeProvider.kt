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
    override fun getUsageType(element: PsiElement?): UsageType? = getUsageType(element, UsageTarget.EMPTY_ARRAY)

    override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? {
        val parent = element?.goUp<RsPath>() ?: return null
        if (parent is RsBaseType) {
            val context = parent.goUp<RsBaseType>()
            return when (context) {
                is RsTypeReference -> {
                    when (context.parent) {
                        is RsImplItem -> UsageType("impl")
                        else -> UsageType("type reference")
                    }
                }
                else -> null
            }
        }
        if (parent is RsPathExpr) {
            val context = parent.goUp<RsPathExpr>()
            return when (context) {
                is RsDotExpr -> UsageType("dot expr")
                is RsCallExpr -> UsageType("function call")
                is RsValueArgumentList -> UsageType("argument")
                is RsFormatMacroArg -> UsageType("macro argument")
                is RsExpr -> UsageType("expr")
                else -> null
            }
        }
        return when (parent) {
            is RsUseSpeck -> UsageType("use")
            is RsStructLiteral -> UsageType("init struct")
            is RsStructLiteralField -> UsageType("init field")
            is RsTraitRef -> UsageType("trait ref")
            is RsMethodCall -> UsageType("method call")
            is RsMetaItem -> UsageType("meta item")
            is RsFieldLookup -> UsageType("field")
            else -> {
                val context = parent.parent
                when (context) {
                    is RsModDeclItem -> UsageType("mod")
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
