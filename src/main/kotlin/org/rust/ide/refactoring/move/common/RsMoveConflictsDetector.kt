/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.move.common.RsMoveUtil.addInner
import org.rust.ide.refactoring.move.common.RsMoveUtil.isInsideMovedElements
import org.rust.ide.refactoring.move.common.RsMoveUtil.isSimplePath
import org.rust.ide.refactoring.move.common.RsMoveUtil.isTargetOfEachSubpathAccessible
import org.rust.ide.refactoring.move.common.RsMoveUtil.startsWithSelf
import org.rust.ide.utils.getTopmostParentInside
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsMoveConflictsDetector(
    private val conflicts: MultiMap<PsiElement, String>,
    private val elementsToMove: List<ElementToMove>,
    private val sourceMod: RsMod,
    private val targetMod: RsMod
) {

    val itemsToMakePublic: MutableSet<RsElement> = hashSetOf()

    fun detectInsideReferencesVisibilityProblems(insideReferences: List<RsMoveReferenceInfo>) {
        updateItemsToMakePublic(insideReferences)
        detectReferencesVisibilityProblems(insideReferences)
        detectPrivateFieldOrMethodInsideReferences()
    }

    private fun updateItemsToMakePublic(insideReferences: List<RsMoveReferenceInfo>) {
        for (reference in insideReferences) {
            val pathOld = reference.pathOld
            val target = reference.target
            val usageMod = pathOld.containingMod
            val isSelfReference = pathOld.isInsideMovedElements(elementsToMove)
            if (!isSelfReference && !usageMod.superMods.contains(targetMod)) {
                val itemToMakePublic = (target as? RsFile)?.declaration ?: target
                itemsToMakePublic.add(itemToMakePublic)
            }
        }
    }

    fun detectOutsideReferencesVisibilityProblems(outsideReferences: List<RsMoveReferenceInfo>) {
        detectReferencesVisibilityProblems(outsideReferences)
        detectPrivateFieldOrMethodOutsideReferences()
    }

    private fun detectReferencesVisibilityProblems(references: List<RsMoveReferenceInfo>) {
        for (reference in references) {
            val pathNew = reference.pathNewAccessible
            if (pathNew == null || !pathNew.isTargetOfEachSubpathAccessible()) {
                addVisibilityConflict(conflicts, reference.pathOld, reference.target)
            }
        }
    }

    /**
     * For now we check only references from [sourceMod],
     * because to check all references we should find them,
     * and it would be very slow when moving e.g. many files.
     */
    private fun detectPrivateFieldOrMethodInsideReferences() {
        val movedElementsShallowDescendants = movedElementsShallowDescendantsOfType<RsElement>(elementsToMove)
        val sourceFile = sourceMod.containingFile
        val elementsToCheck = sourceFile.descendantsOfType<RsElement>() - movedElementsShallowDescendants
        detectPrivateFieldOrMethodReferences(elementsToCheck.asSequence(), ::checkVisibilityForInsideReference)
    }

    /** We create temp child mod of [targetMod] and copy [target] to this temp mod */
    private fun checkVisibilityForInsideReference(referenceElement: RsElement, target: RsVisible) {
        if (target.visibility == RsVisibility.Public) return
        /**
         * Can't use `target.containingMod` directly,
         * because if [target] is expanded by macros,
         * then `containingMod` will return element from other file,
         * and [getTopmostParentInside] will throw exception.
         * We expect [target] to be one of [elementsToMove], so [target] should not be expanded by macros.
         */
        val targetContainingMod = target.ancestorStrict<RsMod>() ?: return
        if (targetContainingMod != sourceMod) return  // all moved items belong to `sourceMod`
        val item = target.getTopmostParentInside(targetContainingMod)
        // It is enough to check only references to descendants of moved items (not mods),
        // because if something in inner mod is private, then it was not accessible before move.
        if (elementsToMove.none { (it as? ItemToMove)?.item == item }) return

        val tempMod = RsPsiFactory(sourceMod.project).createModItem(TMP_MOD_NAME, "")
        tempMod.setContext(targetMod)

        target.putCopyableUserData(RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY, target)
        val itemInTempMod = tempMod.addInner(item)
        val targetInTempMod = itemInTempMod.descendantsOfType<RsVisible>()
            .singleOrNull { it.getCopyableUserData(RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY) == target }
            ?: return

        if (!targetInTempMod.isVisibleFrom(referenceElement.containingMod)) {
            addVisibilityConflict(conflicts, referenceElement, target)
        }
        target.putCopyableUserData(RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY, null)
    }

    private fun detectPrivateFieldOrMethodOutsideReferences() {
        fun checkVisibility(referenceElement: RsElement, target: RsVisible) {
            if (!target.isInsideMovedElements(elementsToMove) && !target.isVisibleFrom(targetMod)) {
                addVisibilityConflict(conflicts, referenceElement, target)
            }
        }

        // TODO: Consider using [PsiRecursiveElementVisitor]
        val elementsToCheck = movedElementsDeepDescendantsOfType<RsElement>(elementsToMove)
        detectPrivateFieldOrMethodReferences(elementsToCheck, ::checkVisibility)
    }

    private fun detectPrivateFieldOrMethodReferences(
        elementsToCheck: Sequence<RsElement>,
        checkVisibility: (RsElement, RsVisible) -> Unit
    ) {
        fun checkVisibility(reference: RsReferenceElement) {
            val target = reference.reference?.resolve() as? RsVisible ?: return
            checkVisibility(reference, target)
        }

        loop@ for (element in elementsToCheck) {
            when (element) {
                is RsDotExpr -> {
                    val fieldReference = element.fieldLookup ?: element.methodCall ?: continue@loop
                    checkVisibility(fieldReference)
                }
                is RsStructLiteralField -> {
                    val field = element.resolveToDeclaration() ?: continue@loop
                    checkVisibility(element, field)
                }
                is RsPatField -> {
                    val patBinding = element.patBinding ?: continue@loop
                    checkVisibility(patBinding)
                }
                is RsPatTupleStruct -> {
                    // It is ok to use `resolve` and not `deepResolve` here
                    // because type aliases can't be used in destructuring tuple struct:
                    val struct = element.path.reference?.resolve() as? RsStructItem ?: continue@loop
                    val fields = struct.tupleFields?.tupleFieldDeclList ?: continue@loop
                    for (field in fields) {
                        checkVisibility(element, field)
                    }
                }
                is RsPath -> {
                    // Conflicts for simple paths are handled using `pathNewAccessible`/`pathNewFallback` machinery
                    val isInsideSimplePath = element.ancestors
                        .takeWhile { it is RsPath }
                        .any { isSimplePath(it as RsPath) }
                    if (!isInsideSimplePath && !element.startsWithSelf()) {
                        // Here we handle e.g. UFCS paths: `Struct1::method1`
                        checkVisibility(element)
                    }
                }
            }
        }
    }

    companion object {
        val RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY: Key<RsElement> =
            Key("RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY")
    }
}

fun addVisibilityConflict(conflicts: MultiMap<PsiElement, String>, reference: RsElement, target: RsElement) {
    val referenceDescription = RefactoringUIUtil.getDescription(reference.containingMod, true)
    val targetDescription = RefactoringUIUtil.getDescription(target, true)
    val message = "$referenceDescription uses $targetDescription which will be inaccessible after move"
    conflicts.putValue(reference, CommonRefactoringUtil.capitalize(message))
}
