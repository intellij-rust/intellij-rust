/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.lang.HelpID
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsNamedElement

class RsFindUsagesProvider : FindUsagesProvider {
    // XXX: must return new instance of WordScanner here, because it is not thread safe
    override fun getWordsScanner(): RsWordScanner = RsWordScanner()

    override fun canFindUsagesFor(element: PsiElement): Boolean =
        element is RsNamedElement

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = ""
    override fun getDescriptiveName(element: PsiElement): String = ""
    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = ""
}
