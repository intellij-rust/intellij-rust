/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesDialog
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.IncorrectOperationException
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.getChildModule
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.toPsiFile

class RsMoveFilesOrDirectoriesDialog(
    private val project2: Project,  // BACKCOMPAT: 2019.3 rename to project
    private val elementsToMove: Array<PsiElement>,
    initialTargetDirectory: PsiDirectory?,
    private val moveCallback: MoveCallback?
) : MoveFilesOrDirectoriesDialog(project2, elementsToMove, initialTargetDirectory) {

    override fun performMove(targetDirectory: PsiDirectory) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project2, targetDirectory)) return
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project2, elementsToMove.toList(), true)) return

        try {
            for (element in elementsToMove) {
                if (element is RsFile) {
                    CopyFilesOrDirectoriesHandler.checkFileExist(targetDirectory, null, element, element.name, "Move")
                }
                MoveFilesOrDirectoriesUtil.checkMove(element, targetDirectory)
            }

            val doneCallback = Runnable { close(DialogWrapper.CANCEL_EXIT_CODE) }
            val searchForReferences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE

            doPerformMove(targetDirectory, searchForReferences, doneCallback)
        } catch (e: IncorrectOperationException) {
            CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.message, "refactoring.moveFile", project2)
        }
    }

    // also used by tests
    fun doPerformMove(targetDirectory: PsiDirectory, searchForReferences: Boolean, doneCallback: Runnable) {
        fun runDefaultProcessor() {
            MoveFilesOrDirectoriesProcessor(
                project2,
                elementsToMove,
                targetDirectory,
                /* searchForReferences = */ false,
                /* searchInComments = */ true,
                /* searchInNonJavaFiles = */ true,
                moveCallback,
                doneCallback
            ).run()
        }

        if (!searchForReferences) {
            runDefaultProcessor()
            return
        }

        val movedFile = elementsToMove.singleOrNull() as? RsFile
            ?: error("Currently move is supported only for single file")
        val crateRoot = movedFile.crateRoot
            ?: error("movedFile.crateRoot is checked for null in RsMoveFilesOrDirectoriesHandler::canMove")
        val newParentMod = targetDirectory.getOwningMod(crateRoot)
        when {
            newParentMod == null -> {
                if (askShouldMoveIfNoNewParentMod()) {
                    runDefaultProcessor()
                }
            }
            newParentMod.crateRoot != movedFile.crateRoot -> {
                // TODO: support move file to different crate
                runDefaultProcessor()
            }
            else -> {
                RsMoveFilesOrDirectoriesProcessor(
                    project2,
                    elementsToMove,
                    targetDirectory,
                    newParentMod,
                    moveCallback,
                    doneCallback
                ).run()
            }
        }
    }

    private fun askShouldMoveIfNoNewParentMod(): Boolean {
        val result = showOkCancelDialog(
            "Move",
            "File will not be included in module tree after move. Continue?",
            Messages.OK_BUTTON,
            project = project2
        )
        return result == Messages.OK
    }
}

private fun PsiDirectory.getOwningMod(crateRoot: RsMod): RsMod? {
    // TODO: if crateRoot is binary crate (main.rs) and directory is root (src/)
    //  Should we return binary crate or library crate (lib.rs)?
    if (crateRoot.getOwnedDirectory() == this) return crateRoot

    val mod = getOwningModAtDefaultLocation()
        ?: getOwningModWalkingFromCrateRoot(crateRoot)
        ?: crateRoot.cargoWorkspace?.let { getOwningModWalkingFromAllCrateRoots(it) }
        ?: return null

    return if (mod.getOwnedDirectory() == this) {
        mod
    } else {
        // e.g. because of #[path] attributes on mod declarations
        null
    }
}

private fun PsiDirectory.getOwningModAtDefaultLocation(): RsMod? {
    val file1 = findFile(RsConstants.MOD_RS_FILE) as? RsMod
    val file2 = parentDirectory?.findFile("$name.rs") as? RsMod
    return file1.takeIf { file2 == null }
        ?: file2.takeIf { file1 == null }
}

private fun PsiDirectory.getOwningModWalkingFromCrateRoot(crateRoot: RsMod): RsMod? {
    val crateRootDirectory = crateRoot.getOwnedDirectory() ?: return null
    val crateRootPath = crateRootDirectory.virtualFile.pathAsPath
    val path = virtualFile.pathAsPath
    if (!path.startsWith(crateRootPath)) return null

    var mod = crateRoot
    for (segmentPath in crateRootPath.relativize(path)) {
        val segment = segmentPath.toString()
        mod = mod.getChildModule(segment) ?: return null
    }
    return mod
}

private fun PsiDirectory.getOwningModWalkingFromAllCrateRoots(cargoWorkspace: CargoWorkspace): RsMod? {
    val cargoPackages = cargoWorkspace.packages.filter { it.origin == PackageOrigin.WORKSPACE }
    for (cargoPackage in cargoPackages) {
        for (cargoTarget in cargoPackage.targets) {
            val crateRoot = cargoTarget.crateRoot?.toPsiFile(project) as? RsMod ?: continue
            val owningMod = getOwningModWalkingFromCrateRoot(crateRoot)
            if (owningMod != null) return owningMod
        }
    }
    return null
}
