package org.rust.lang.search

import com.intellij.lang.HelpID
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.rust.lang.core.psi.RustPatBinding

class RustFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner() = RustWordScanner()

    override fun canFindUsagesFor(element: PsiElement) =
        element is RustPatBinding

    override fun getHelpId(element: PsiElement) = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement) =
        ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE)

    override fun getDescriptiveName(element: PsiElement) =
        ElementDescriptionUtil.getElementDescription(element, UsageViewLongNameLocation.INSTANCE)

    override fun getNodeText(element: PsiElement, useFullName: Boolean) =
        ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE)
}
