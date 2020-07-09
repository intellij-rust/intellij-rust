/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.doc.psi.RsDocLink
import org.rust.lang.doc.psi.RsDocLinkLabel
import org.rust.lang.doc.psi.RsDocLinkReferenceDef

class RsDocLinkLabelReferenceImpl(
    element: RsDocLinkLabel
) : PsiReferenceBase<RsDocLinkLabel>(element, TextRange(1, element.textLength - 1), false) {
    override fun resolve(): PsiElement? {
        val parentLink = element.parent as RsDocLink
        val linkDestination = when (parentLink) {
            is RsDocLinkReferenceDef -> parentLink.linkDestination
            else -> parentLink.containingDoc.linkReferenceMap[element.markdownValue]?.linkDestination
        }

        return linkDestination?.reference?.resolve()
    }
}
