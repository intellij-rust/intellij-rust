/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.rust.lang.core.psi.ext.RsNamedElement

class RsDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        return when (location) {
            is UsageViewNodeTextLocation -> (element as? RsNamedElement)?.name
            is UsageViewShortNameLocation -> (element as? RsNamedElement)?.name
            is UsageViewLongNameLocation -> (element as? RsNamedElement)?.name
            is UsageViewTypeLocation -> (element as? RsNamedElement)?.presentationInfo?.type
            is HighlightUsagesDescriptionLocation -> (element as? RsNamedElement)?.name
            else -> null
        }
    }
}
