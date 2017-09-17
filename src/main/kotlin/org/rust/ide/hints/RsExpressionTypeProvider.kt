/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.types.type


class RsExpressionTypeProvider : ExpressionTypeProvider<PsiElement>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): List<PsiElement> =
        pivot.ancestors
            .takeWhile { it !is RsItemElement }
            .filter { it is RsExpr || it is RsPatBinding }
            .toList()

    override fun getInformationHint(element: PsiElement): String {
        val type = when (element) {
            is RsExpr -> element.type
            is RsPatBinding -> element.type
            else -> error("Unexpected element type: $element")
        }
        return StringUtil.escapeXml(type.toString())
    }

}
