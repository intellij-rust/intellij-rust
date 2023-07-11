/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.openapi.util.NlsContexts.DialogMessage
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
import org.rust.RsBundle
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.refactoring.move.RsMoveFilesOrDirectoriesHandler.Companion.adjustForMove
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.getChildModule
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.toPsiFile

class RsMoveFilesOrDirectoriesDialog(
    project: Project,
    private val filesOrDirectoriesToMove: Array<out PsiElement /* [PsiDirectory] or [RsFile] */>,
    initialTargetDirectory: PsiDirectory?,
    private val moveCallback: MoveCallback?
) : MoveFilesOrDirectoriesDialog(project, filesOrDirectoriesToMove, initialTargetDirectory) {

    init {
        check(!isUnitTestMode)
        title = RsBundle.message("dialog.title.move.rust")
    }

    override fun performMove(targetDirectory: PsiDirectory) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, targetDirectory)) return
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, filesOrDirectoriesToMove.toList(), true)) return

        try {
            for (element in filesOrDirectoriesToMove) {
                if (element is RsFile) {
                    if (element.parent == targetDirectory) {
                        showError(RsBundle.message("dialog.message.please.choose.target.directory.different.from.current"))
                        return
                    }
                    CopyFilesOrDirectoriesHandler.checkFileExist(targetDirectory, null, element, element.name, RsBundle.message("dialog.title.move"))
                }
                MoveFilesOrDirectoriesUtil.checkMove(element, targetDirectory)
            }

            val doneCallback = Runnable { close(DialogWrapper.OK_EXIT_CODE) }
            val searchForReferences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE

            doPerformMove(
                project,
                filesOrDirectoriesToMove,
                moveCallback,
                targetDirectory,
                searchForReferences,
                doneCallback
            )
        } catch (e: Exception) {
            if (e !is IncorrectOperationException) {
                logger<RsMoveFilesOrDirectoriesDialog>().error(e)
            }
            showError(e.message)
        }
    }

    private fun showError(@Suppress("UnstableApiUsage") @DialogMessage message: String?) {
        val title = RefactoringBundle.message("error.title")
        CommonRefactoringUtil.showErrorMessage(title, message, "refactoring.moveFile", project)
    }

    companion object {
        // also used by tests
        fun doPerformMove(
            project: Project,
            filesOrDirectoriesToMove: Array<out PsiElement>,
            moveCallback: MoveCallback?,
            targetDirectory: PsiDirectory,
            searchForReferences: Boolean,
            doneCallback: Runnable
        ) {
            fun runDefaultProcessor() {
                MoveFilesOrDirectoriesProcessor(
                    project,
                    filesOrDirectoriesToMove,
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

            val crateRoot = filesOrDirectoriesToMove.first().adjustForMove()?.crateRoot
                ?: error("One of moved file is not included in module tree")
            val targetMod = targetDirectory.getOwningMod(crateRoot)
            if (targetMod == null) {
                if (askShouldMoveIfNoNewParentMod(project)) {
                    runDefaultProcessor()
                }
                return
            }

            RsMoveFilesOrDirectoriesProcessor(
                project,
                filesOrDirectoriesToMove,
                targetDirectory,
                targetMod,
                moveCallback,
                doneCallback
            ).run()
        }

        private fun askShouldMoveIfNoNewParentMod(project: Project): Boolean {
            val result = showOkCancelDialog(
                RsBundle.message("dialog.title.move"),
                RsBundle.message("dialog.message.file.will.not.be.included.in.module.tree.after.move.continue"),
                Messages.getOkButton(),
                project = project
            )
            return result == Messages.OK
        }
    }
}

fun PsiDirectory.getOwningMod(crateRoot: RsMod?): RsMod? {
    // TODO: if crateRoot is binary crate (main.rs) and directory is root (src/)
    //  Should we return binary crate or library crate (lib.rs)?
    if (crateRoot != null && crateRoot.getOwnedDirectory() == this) return crateRoot

    val mod = getOwningModAtDefaultLocation()
        ?: crateRoot?.let { getOwningModWalkingFromCrateRoot(it) }
        ?: crateRoot?.cargoWorkspace?.let { getOwningModWalkingFromAllCrateRoots(it) }
        ?: return null

    return if (mod.getOwnedDirectory() == this) {
        mod
    } else {
        // e.g. because of #[path] attributes on mod declarations
        null
    }
}

fun PsiDirectory.getOwningModAtDefaultLocation(): RsFile? {
    val file1 = findFile(RsConstants.MOD_RS_FILE) as? RsFile
    val file2 = parentDirectory?.findFile("$name.rs") as? RsFile
    return file1.takeIf { file2 == null }
        ?: file2.takeIf { file1 == null }
}

private fun PsiDirectory.getOwningModWalkingFromCrateRoot(crateRoot: RsMod): RsMod? {
    val crateRootDirectory = crateRoot.getOwnedDirectory() ?: return null
    val crateRootPath = crateRootDirectory.virtualFile.pathAsPath
    val path = virtualFile.pathAsPath
    if (!path.startsWith(crateRootPath)) return null
    if (this == crateRootDirectory) return crateRoot

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
            val crateRoot = cargoTarget.crateRoot?.toPsiFile(project) as? RsFile ?: continue
            val owningMod = getOwningModWalkingFromCrateRoot(crateRoot)
            if (owningMod != null) return owningMod
        }
    }
    return null
}
