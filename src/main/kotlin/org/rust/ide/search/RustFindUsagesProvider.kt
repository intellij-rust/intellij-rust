package org.rust.ide.search

import com.intellij.lang.HelpID
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPatBindingElement

class RustFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner() = RustWordScanner()

    override fun canFindUsagesFor(element: PsiElement) =
        element is RustPatBindingElement

    override fun getHelpId(element: PsiElement) = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement) = ""
    override fun getDescriptiveName(element: PsiElement) = ""
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = ""
}
