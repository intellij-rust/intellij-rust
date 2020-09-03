/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.move.common.RsMovePathHelper
import org.rust.ide.refactoring.move.common.RsMoveReferenceInfo
import org.rust.ide.refactoring.move.common.RsMoveRetargetReferencesProcessor
import org.rust.ide.refactoring.move.common.updateScopeIfNecessary
import org.rust.ide.utils.import.lastElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.runWithCancelableProgress

/**
 * Move refactoring currently supports moving a single file without submodules.
 * It consists of these steps:
 * - Check visibility conflicts (target of any reference should remain accessible after move)
 * - Update `pub(in path)` visibility modifiers in moved file if necessary
 * - Move mod-declaration to new parent mod
 * - Update necessary imports in other files
 * - Update necessary paths in other files (some usages could still remain invalid because of glob-imports)
 *     We replace path with absolute if there are few usages of this path in the file, otherwise add new import
 * - Update relative paths in moved file
 */
class RsMoveFilesOrDirectoriesProcessor(
    private val project: Project,
    private val elementsToMove: Array<PsiElement>,
    private val newParent: PsiDirectory,
    private val newParentMod: RsMod,
    moveCallback: MoveCallback?,
    doneCallback: Runnable
) : MoveFilesOrDirectoriesProcessor(
    project,
    elementsToMove,
    newParent,
    true,
    true,
    true,
    moveCallback,
    doneCallback
) {

    private val psiFactory = RsPsiFactory(project)
    private val movedFile: RsFile = elementsToMove[0] as RsFile
    private val oldParentMod: RsMod = movedFile.`super` ?: error("Can't find parent mod of moved file")

    // keys --- `RsPath`s which references moved file
    // insideReferencesMap[path] --- path to moved file after move
    // null means no accessible path found
    private lateinit var insideReferencesMap: Map<RsPath, RsPath?>

    // keys --- `RsPath`s inside movedFile
    // outsideReferencesMap[path] --- target element for path reference
    private lateinit var outsideReferencesMap: Map<RsPath, RsElement>
    private lateinit var conflictsDetector: RsMoveFilesOrDirectoriesConflictsDetector

    override fun doRun() {
        checkMove()
        super.doRun()
    }

    private fun checkMove() {
        // TODO: support move multiple files
        check(elementsToMove.size == 1)

        check(newParentMod.crateRoot == movedFile.crateRoot)
        movedFile.modName?.let {
            if (newParentMod.getChildModule(it) != null) {
                throw IncorrectOperationException("Cannot move. Mod with same crate relative path already exists")
            }
        }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = MultiMap<PsiElement, String>()

        val message = RefactoringBundle.message("detecting.possible.conflicts")
        val success = project.runWithCancelableProgress(message) {
            runReadAction {
                detectVisibilityProblems(usages, conflicts)
            }
        }
        if (!success) return false

        return showConflicts(conflicts, usages)
    }

    private fun detectVisibilityProblems(usages: Array<UsageInfo>, conflicts: MultiMap<PsiElement, String>) {
        insideReferencesMap = preprocessInsideReferences(usages)
        outsideReferencesMap = collectOutsideReferencesFromMovedFile()
        conflictsDetector = RsMoveFilesOrDirectoriesConflictsDetector(
            movedFile,
            newParentMod,
            insideReferencesMap,
            outsideReferencesMap
        )
        conflictsDetector.detectVisibilityProblems(conflicts)
    }

    private fun preprocessInsideReferences(usages: Array<UsageInfo>): Map<RsPath, RsPath?> {
        val pathHelper = RsMovePathHelper(project, newParentMod)
        return usages
            .mapNotNull { usage ->
                val path = usage.element as? RsPath
                val target = usage.reference?.resolve() as? RsQualifiedNamedElement
                if (path == null || target == null) return@mapNotNull null

                val pathNew = pathHelper.findPathAfterMove(path, target)
                path to pathNew
            }
            .toMap()
    }

    private fun collectOutsideReferencesFromMovedFile(): MutableMap<RsPath, RsElement> {
        val paths = movedFile.descendantsOfType<RsPath>()
        val outsideReferencesMap = mutableMapOf<RsPath, RsElement>()
        for (path in paths) {
            val target = path.reference?.resolve() ?: continue
            // ignore references from child modules of moved file
            if (target.isInsideModSubtree(movedFile)) continue

            outsideReferencesMap[path] = target
        }
        return outsideReferencesMap
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val (oldModDeclarations, useDirectiveUsages, otherUsages) = groupUsages(usages)

        updateReferencesInMovedFileBeforeMove()

        // step 1: move file and its mod-declaration
        moveModDeclaration(oldModDeclarations)
        super.performRefactoring(emptyArray())

        check(!movedFile.crateRelativePath.isNullOrEmpty())
        { "${movedFile.name} had correct crateRelativePath before moving mod-declaration, but empty/null after move" }

        // step 2:
        // a) update references in use-directives
        // b) some usages could still remain invalid (because of glob-imports)
        //    so we should add new import, or replace reference path with absolute one
        retargetUsages(useDirectiveUsages + otherUsages)

        // step 3: retarget references from moved file to outside
        updateReferencesInMovedFileAfterMove()
    }

    private fun groupUsages(usages: Array<out UsageInfo>): Triple<List<UsageInfo>, List<UsageInfo>, MutableList<UsageInfo>> {
        val oldModDeclarations = mutableListOf<UsageInfo>()
        val useDirectiveUsages = mutableListOf<UsageInfo>()
        val otherUsages = mutableListOf<UsageInfo>()
        for (usage in usages) {
            // ignore strange usages
            if (usage.element == null || usage.reference == null) continue

            when {
                usage.element is RsModDeclItem -> oldModDeclarations.add(usage)
                usage.element!!.parentOfType<RsUseItem>() != null -> useDirectiveUsages.add(usage)
                else -> otherUsages.add(usage)
            }
        }

        // files not included in module tree are filtered in RsMoveFilesOrDirectoriesHandler::canMove
        // by check file.crateRoot != null
        check(oldModDeclarations.isNotEmpty())
        if (oldModDeclarations.size > 1) {
            val message = "Can't move ${movedFile.name}.\nIt is declared in more than one parent modules"
            throw IncorrectOperationException(message)
        }

        return Triple(oldModDeclarations, useDirectiveUsages, otherUsages)
    }

    private fun moveModDeclaration(oldModDeclarations: List<UsageInfo>) {
        check(oldModDeclarations.size == 1)
        val oldModDeclaration = oldModDeclarations[0].element as RsModDeclItem

        when (oldModDeclaration.visibility) {
            is RsVisibility.Private -> {
                if (conflictsDetector.shouldMakeMovedFileModDeclarationPublic) {
                    oldModDeclaration.addAfter(psiFactory.createPub(), null)
                }
            }
            is RsVisibility.Restricted -> run {
                val visRestriction = oldModDeclaration.vis?.visRestriction ?: return@run
                visRestriction.updateScopeIfNecessary(psiFactory, newParentMod)
            }
        }
        val newModDeclaration = oldModDeclaration.copy()

        oldModDeclaration.delete()
        newParentMod.insertModDecl(psiFactory, newModDeclaration)
    }

    private fun retargetUsages(usages: List<UsageInfo>) {
        // if no accessible path found, then we use absolute path
        // (it happens only if user choose "Continue" in conflicts dialog)
        fun getPathNewFallback(pathOld: RsPath): RsPath? {
            val pathNewText = movedFile.qualifiedNameRelativeTo(pathOld.containingMod) ?: return null
            return psiFactory.tryCreatePath(pathNewText)
        }

        val references = usages.mapNotNull { usage ->
            val pathOld = usage.element as? RsPath ?: return@mapNotNull null
            val pathNewAccessible = insideReferencesMap[pathOld]
            val pathNewFallback = getPathNewFallback(pathOld)
            if (pathNewAccessible == null && pathNewFallback == null) return@mapNotNull null
            RsMoveReferenceInfo(pathOld, pathNewAccessible, pathNewFallback, movedFile)
        }
        RsMoveRetargetReferencesProcessor(project, oldParentMod, newParentMod).retargetReferences(references)
    }

    private fun updateReferencesInMovedFileBeforeMove() {
        for ((path, _) in outsideReferencesMap) {
            val visRestriction = path.parent as? RsVisRestriction ?: continue
            visRestriction.updateScopeIfNecessary(psiFactory, newParentMod)
        }
    }

    private fun updateReferencesInMovedFileAfterMove() {
        // we should update `super::...` paths in movedFile
        // but in direct submodules of movedFile we should update only `super::super::...` paths
        // and so on

        for ((path, target) in outsideReferencesMap) {
            val pathParent = path.parent
            if (pathParent is RsVisRestriction) continue
            if (pathParent is RsPath && pathParent.hasOnlySuperSegments()) continue
            if (!path.hasOnlySuperSegments()) continue

            val targetModPath = (target as? RsMod)?.crateRelativePath ?: continue
            val pathNew = psiFactory.tryCreatePath("crate$targetModPath") ?: continue
            path.replace(pathNew)
        }
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return MoveMultipleElementsViewDescriptor(elementsToMove, newParent.name)
    }
}

fun RsElement.isInsideModSubtree(mod: RsMod): Boolean = containingMod.superMods.contains(mod)

private fun RsMod.insertModDecl(psiFactory: RsPsiFactory, modDecl: PsiElement) {
    val anchor = childrenOfType<RsModDeclItem>().lastElement ?: childrenOfType<RsUseItem>().lastElement
    if (anchor != null) {
        addAfter(modDecl, anchor)
    } else {
        val firstItem = itemsAndMacros.firstOrNull { it !is RsAttr && it !is RsVis }
            ?: (this as? RsModItem)?.rbrace
        addBefore(modDecl, firstItem)
    }

    if (modDecl.nextSibling == null) {
        addAfter(psiFactory.createNewline(), modDecl)
    }
}

fun RsPath.hasOnlySuperSegments(): Boolean {
    if (`super` == null) return false
    return path?.hasOnlySuperSegments() ?: true
}
