package org.rust.ide

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.rust.ide.utils.presentationInfo
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
