/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showYesNoDialog
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.*

class RsFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean = element is RsNamedElement

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        val secondaryElements = if (!forHighlightUsages) findSecondaryElements(element) else emptyList()
        return RsFindUsagesHandler(element, secondaryElements)
    }

    companion object {
        private fun findSecondaryElements(element: PsiElement): List<PsiElement> {
            if (element !is RsAbstractable) return emptyList()

            if (element.owner is RsAbstractableOwner.Trait) {
                return findImplDeclarations(element)
            }

            val superItem = element.superItem ?: return emptyList()
            return if (askWhetherShouldSearchForUsagesOfSuperItem(element.project)) {
                findImplDeclarations(superItem) - element + superItem
            } else {
                emptyList()
            }
        }

        private fun findImplDeclarations(declaration: RsAbstractable): List<RsAbstractable> {
            val trait = (declaration.owner as? RsAbstractableOwner.Trait)?.trait ?: return emptyList()
            return trait.searchForImplementations().mapNotNull { it.findCorrespondingElement(declaration) }
        }

        private fun askWhetherShouldSearchForUsagesOfSuperItem(project: Project): Boolean =
            showYesNoDialog(
                "Find Usages",
                "Do you want to find usages of the base declaration?",
                project,
                icon = Messages.getQuestionIcon()
            )
    }
}
