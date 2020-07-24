/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsMoveFilesOrDirectoriesConflictsDetector(
    private val movedFile: RsFile,
    private val newParentMod: RsMod,
    private val insideReferencesMap: Map<RsPath, RsPath?>,
    private val outsideReferencesMap: Map<RsPath, RsElement>
) {

    var shouldMakeMovedFileModDeclarationPublic: Boolean = false
        private set

    fun detectVisibilityProblems(conflicts: MultiMap<PsiElement, String>) {
        detectInsideReferencesVisibilityProblems(conflicts)
        detectOutsideReferencesVisibilityProblems(conflicts)
    }

    private fun detectInsideReferencesVisibilityProblems(conflicts: MultiMap<PsiElement, String>) {
        shouldMakeMovedFileModDeclarationPublic = false
        for ((pathOld, pathNew) in insideReferencesMap) {
            if (pathNew == null) {
                addVisibilityConflict(conflicts, pathOld, movedFile)
            }

            val usageMod = pathOld.containingMod
            if (!usageMod.superMods.contains(newParentMod)) {
                shouldMakeMovedFileModDeclarationPublic = true
            }
        }
    }

    private fun detectOutsideReferencesVisibilityProblems(conflicts: MultiMap<PsiElement, String>) {
        for ((path, target) in outsideReferencesMap) {
            val isInsideVisRestriction = path.ancestors.first { it !is RsPath } is RsVisRestriction
            if (isInsideVisRestriction || target.crateRoot != movedFile.crateRoot) continue

            if (path.hasOnlySuperSegments()) {
                val pathParent = path.parent
                if (pathParent is RsPath && pathParent.hasOnlySuperSegments()) continue

                val targetMod = target as? RsMod ?: continue
                for (mod in targetMod.superMods) {
                    if (!mod.isVisibleFrom(newParentMod)) {
                        addVisibilityConflict(conflicts, path, target)
                        break
                    }
                }
            } else if (target is RsVisible) {
                checkVisibility(conflicts, path, target)
            }
        }

        detectPrivateStructFieldOutsideReferences(conflicts)
    }

    private fun detectPrivateStructFieldOutsideReferences(conflicts: MultiMap<PsiElement, String>) {
        fun checkStructFieldVisibility(fieldReference: RsMandatoryReferenceElement) {
            val field = fieldReference.reference.resolve() as? RsVisible ?: return
            if (!field.isInsideModSubtree(movedFile)) checkVisibility(conflicts, fieldReference, field)
        }

        loop@ for (element in movedFile.descendantsOfType<RsElement>()) {
            when (element) {
                is RsDotExpr -> {
                    val fieldReference = element.fieldLookup ?: element.methodCall ?: continue@loop
                    checkStructFieldVisibility(fieldReference)
                }
                is RsStructLiteralField -> {
                    val field = element.resolveToDeclaration() ?: continue@loop
                    if (!field.isInsideModSubtree(movedFile)) checkVisibility(conflicts, element, field)
                }
                is RsPatField -> {
                    val patBinding = element.patBinding ?: continue@loop
                    checkStructFieldVisibility(patBinding)
                }
                is RsPatTupleStruct -> {
                    // it is ok to use `resolve` and not `deepResolve` here
                    // because type aliases can't be used in destructuring tuple struct:
                    val struct = element.path.reference?.resolve() as? RsStructItem ?: continue@loop
                    if (struct.isInsideModSubtree(movedFile)) continue@loop
                    val fields = struct.tupleFields?.tupleFieldDeclList ?: continue@loop
                    if (!fields.all { it.isVisibleFrom(newParentMod) }) {
                        addVisibilityConflict(conflicts, element, struct)
                    }
                }
            }
        }
    }

    private fun checkVisibility(
        conflicts: MultiMap<PsiElement, String>,
        referenceElement: RsReferenceElement,
        target: RsVisible
    ) {
        if (!target.isVisibleFrom(newParentMod)) {
            addVisibilityConflict(conflicts, referenceElement, target)
        }
    }

    private fun addVisibilityConflict(
        conflicts: MultiMap<PsiElement, String>,
        reference: RsElement,
        target: RsElement
    ) {
        val referenceDescription = RefactoringUIUtil.getDescription(reference.containingMod, true)
        val targetDescription = RefactoringUIUtil.getDescription(target, true)
        val message = "$referenceDescription uses $targetDescription which will be inaccessible after move"
        conflicts.putValue(reference, CommonRefactoringUtil.capitalize(message))
    }
}
