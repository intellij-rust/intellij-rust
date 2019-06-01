/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatField
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.types.type
import org.rust.openapiext.escaped

class RsExpressionTypeProvider : ExpressionTypeProvider<PsiElement>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): List<PsiElement> =
        pivot.ancestors
            .takeWhile { it !is RsItemElement }
            .filter { it is RsExpr || it is RsPat || it is RsPatField }
            .toList()

    override fun getInformationHint(element: PsiElement): String {
        val type = when (element) {
            is RsExpr -> element.type
            is RsPat -> element.type
            is RsPatField -> element.type
            else -> error("Unexpected element type: $element")
        }
        return type.toString().escaped
    }
}
