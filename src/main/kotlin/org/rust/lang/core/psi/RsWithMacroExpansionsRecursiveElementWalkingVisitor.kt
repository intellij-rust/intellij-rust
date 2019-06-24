/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.rust.lang.core.psi.ext.expansion

abstract class RsWithMacroExpansionsRecursiveElementWalkingVisitor : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
        if (element is RsMacroCall && element.macroArgument != null) {
            val expansion = element.expansion ?: return
            for (expandedElement in expansion.elements) {
                visitElement(expandedElement)
            }
        } else {
            super.visitElement(element)
        }
    }
}
