/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
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
    @Suppress("UnstableApiUsage") private val conflicts: MultiMap<PsiElement, @DialogMessage String>,
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
            val pathOld = reference.pathOldOriginal
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
                addVisibilityConflict(conflicts, reference.pathOldOriginal, reference.target)
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

    /**
     * Rules for inherent impls:
     * - An implementing type must be defined within the same crate as the original type definition
     * - https://doc.rust-lang.org/reference/items/implementations.html#inherent-implementations
     * - https://doc.rust-lang.org/error-index.html#E0116
     * We should check:
     * - When moving inherent impl: check that implementing type is also moved
     * - When moving struct/enum: check that all inherent impls are also moved
     * TODO: Check impls for trait objects: `impl dyn Trait { ... }`
     *
     * Rules for trait impls:
     * - Orphan rules: https://doc.rust-lang.org/reference/items/implementations.html#orphan-rules
     * - https://doc.rust-lang.org/error-index.html#E0117
     * We should check (denote impls as `impl<P1..=Pn> Trait<T1..=Tn> for T0`):
     * - When moving trait impl:
     *     - either implemented trait is in target crate
     *     - or at least one of the types `T0..=Tn` is a local type
     * - When moving trait: for each impl of this trait which remains in source crate:
     *     - at least one of the types `T0..=Tn` is a local type
     * - Uncovering is not checking, because it is complicated
     */
    fun checkImpls() {
        if (sourceMod.crateRoot == targetMod.crateRoot) return

        val structsToMove = movedElementsDeepDescendantsOfType<RsStructOrEnumItemElement>(elementsToMove).toSet()
        val implsToMove = movedElementsDeepDescendantsOfType<RsImplItem>(elementsToMove)
        val (inherentImplsToMove, traitImplsToMove) = implsToMove
            .partition { it.traitRef == null }
            .run { first.toSet() to second.toSet() }

        checkStructIsMovedTogetherWithInherentImpl(structsToMove, inherentImplsToMove)
        checkInherentImplIsMovedTogetherWithStruct(structsToMove, inherentImplsToMove)

        traitImplsToMove.forEach(::checkTraitImplIsCoherentAfterMove)
    }

    // https://doc.rust-lang.org/reference/items/implementations.html#trait-implementation-coherence
    private fun checkTraitImplIsCoherentAfterMove(impl: RsImplItem) {
        fun RsElement.isLocalAfterMove(): Boolean =
            crateRoot == targetMod.crateRoot || isInsideMovedElements(elementsToMove)

        if (!checkOrphanRules(impl, RsElement::isLocalAfterMove)) {
            conflicts.putValue(impl, "Orphan rules check failed for trait implementation after move")
        }
    }

    private fun checkStructIsMovedTogetherWithInherentImpl(
        structsToMove: Set<RsStructOrEnumItemElement>,
        inherentImplsToMove: Set<RsImplItem>
    ) {
        for (impl in inherentImplsToMove) {
            val struct = impl.implementingType?.item ?: continue
            if (struct !in structsToMove) {
                val structDescription = RefactoringUIUtil.getDescription(struct, true)
                val message = "Inherent implementation should be moved together with $structDescription"
                conflicts.putValue(impl, message)
            }
        }
    }

    private fun checkInherentImplIsMovedTogetherWithStruct(
        structsToMove: Set<RsStructOrEnumItemElement>,
        inherentImplsToMove: Set<RsImplItem>
    ) {
        for ((file, structsToMoveInFile) in structsToMove.groupBy { it.containingFile }) {
            // not working if there is inherent impl in other file
            // but usually struct and its inherent impls belong to same file
            val structInherentImpls = file.descendantsOfType<RsImplItem>()
                .filter { it.traitRef == null }
                .groupBy { it.implementingType?.item }
            for (struct in structsToMoveInFile) {
                val impls = structInherentImpls[struct] ?: continue
                for (impl in impls.filter { it !in inherentImplsToMove }) {
                    val structDescription = RefactoringUIUtil.getDescription(struct, true)
                    val message = "Inherent implementation should be moved together with $structDescription"
                    conflicts.putValue(impl, message)
                }
            }
        }
    }

    companion object {
        val RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY: Key<RsElement> =
            Key("RS_ELEMENT_FOR_CHECK_INSIDE_REFERENCES_VISIBILITY")
    }
}

fun addVisibilityConflict(
    @Suppress("UnstableApiUsage") conflicts: MultiMap<PsiElement, @DialogMessage String>,
    reference: RsElement,
    target: RsElement
) {
    val referenceDescription = RefactoringUIUtil.getDescription(reference.containingMod, true)
    val targetDescription = RefactoringUIUtil.getDescription(target, true)
    val message = "$referenceDescription uses $targetDescription which will be inaccessible after move"
    conflicts.putValue(reference, StringUtil.capitalize(message))
}
