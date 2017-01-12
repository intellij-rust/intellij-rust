package org.rust.ide

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.rust.lang.core.psi.RsPatBinding

class RustDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        return when (location) {
            is UsageViewNodeTextLocation -> getNodeText(element)
            is UsageViewShortNameLocation -> getShortName(element)
            is UsageViewLongNameLocation -> getLongName(element)
            is UsageViewTypeLocation -> getType(element)
            is HighlightUsagesDescriptionLocation -> getLongName(element)
            else -> null
        }
    }

    private fun getNodeText(element: PsiElement): String? {
        return when (element) {
            is RsPatBinding -> element.identifier.text
            else -> null
        }
    }

    private fun getShortName(element: PsiElement): String? {
        return when (element) {
            is RsPatBinding -> element.identifier.text
            else -> null
        }
    }

    private fun getLongName(element: PsiElement): String? {
        return when (element) {
            is RsPatBinding -> getShortName(element)
            else -> null
        }
    }

    private fun getType(element: PsiElement): String? {
        return when (element) {
            is RsPatBinding -> "variable"
            else -> null
        }
    }
}
