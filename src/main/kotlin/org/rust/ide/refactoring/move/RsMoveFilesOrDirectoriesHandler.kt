/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.isEdition2018
import org.rust.stdext.mapToSet

class RsMoveFilesOrDirectoriesHandler : MoveFilesOrDirectoriesHandler() {

    override fun supportsLanguage(language: Language): Boolean = language.`is`(RsLanguage)

    override fun adjustTargetForMove(dataContext: DataContext?, targetContainer: PsiElement?): PsiElement? =
        (targetContainer as? PsiFile)?.containingDirectory ?: targetContainer

    override fun adjustForMove(
        project: Project?,
        elements: Array<out PsiElement>,
        targetElement: PsiElement?
    ): Array<PsiElement>? {
        // When moving file `foo.rs`, we should add directory `foo`
        // When moving directory `foo`, we should add file `foo.rs`
        val elementsWithRelated = elements
            .flatMap {
                val file = it.adjustForMove()
                val directory = file?.getOwnedDirectory()
                if (file != null && directory != null) {
                    listOf(file, directory)
                } else {
                    listOf(it)
                }
            }
            .toSet()
            .toTypedArray()
        return super.adjustForMove(project, elementsWithRelated, targetElement)
            ?.filterNotNull()?.toTypedArray()
    }

    override fun canMove(
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        reference: PsiReference?
    ): Boolean {
        val files = PsiTreeUtil.filterAncestors(elements)
            .map { it.adjustForMove() ?: return false }
        if (files.isEmpty()) return false
        if (!files.all { it.canBeMoved() }) return false

        if (files.mapToSet { it.`super` }.size != 1) return false

        val adjustedTargetContainer = adjustTargetForMove(null, targetContainer)
        return super.canMove(elements, adjustedTargetContainer, reference)
    }

    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        moveCallback: MoveCallback?
    ) {
        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, elements.toList(), true)) return

        val adjustedTargetContainer = adjustTargetForMove(null, targetContainer)
        val adjustedElements = adjustForMove(project, elements, adjustedTargetContainer) ?: return

        val targetDirectory = MoveFilesOrDirectoriesUtil.resolveToDirectory(project, adjustedTargetContainer)
        // adjustedTargetContainer will be null if refactoring is performed by action (F6),
        // otherwise (when refactoring is performed using cut & paste in file tree) it should be some PsiDirectory
        // resolveToDirectory will return adjustedTargetContainer unchanged if it is PsiDirectory
        // so this return could happen only in rare cases when adjustedTargetContainer is PsiDirectoryContainer
        if (adjustedTargetContainer != null && targetDirectory == null) return
        val initialTargetDirectory = MoveFilesOrDirectoriesUtil.getInitialTargetDirectory(targetDirectory, elements)

        RsMoveFilesOrDirectoriesDialog(project, adjustedElements, initialTargetDirectory, moveCallback).show()
    }

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?
    ): Boolean {
        if (!canMove(arrayOf(element), null, reference)) return false

        return super.tryToMove(element, project, dataContext, reference, editor)
    }

    companion object {
        fun PsiElement.adjustForMove(): RsFile? =
            when (this) {
                is PsiDirectory -> getOwningModAtDefaultLocation()
                is RsFile -> this
                else -> null
            }
    }
}

private fun RsFile.canBeMoved(): Boolean =
    modName != null
        && crateRoot != null
        && crateRelativePath != null
        && !isCrateRoot
        && isEdition2018  // TODO: support 2015 edition
        && pathAttribute == null  // TODO: support path attribute on mod declaration
