/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.kind

class RsStringLiteralManipulator : AbstractElementManipulator<RsLitExpr>() {
    override fun handleContentChange(element: RsLitExpr, range: TextRange, newContent: String): RsLitExpr {
        if (range != getRangeInElement(element)) {
            // FIXME not supported for now
            return element
        }

        val oldText = element.text
        val newText = "${oldText.substring(0, range.startOffset)}$newContent${oldText.substring(range.endOffset)}"

        val newLitExpr = RsPsiFactory(element.project).createExpression(newText)
        return element.replace(newLitExpr) as RsLitExpr
    }

    override fun getRangeInElement(element: RsLitExpr): TextRange {
        return (element.kind as? RsLiteralKind.String)?.offsets?.value ?: super.getRangeInElement(element)
    }
}
