/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.usageView.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.qualifiedName

class RsDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        return when (location) {
            is UsageViewNodeTextLocation,
            is UsageViewShortNameLocation,
            is UsageViewLongNameLocation,
            is HighlightUsagesDescriptionLocation -> defaultDescription(element)
            is UsageViewTypeLocation -> (element as? RsNamedElement)?.presentationInfo?.type
            is RefactoringDescriptionLocation -> refactoringDescription(element, location.includeParent())
            else -> null
        }
    }

    private fun defaultDescription(element: PsiElement): String? =
        when (element) {
            is RsMod -> element.modName
            is RsNamedElement -> element.name
            else -> null
        }

    private fun refactoringDescription(element: PsiElement, includeParent: Boolean): String? {
        val type = UsageViewUtil.getType(element)
        val elementName = defaultDescription(element) ?: return null

        val parent = element.parent
        val name = when {
            includeParent && element is RsMod -> element.qualifiedName
            includeParent && parent is RsMod -> parent.qualifiedName?.let { "$it::$elementName" }
            else -> elementName
        } ?: elementName
        return "$type ${CommonRefactoringUtil.htmlEmphasize(name)}"
    }

}
