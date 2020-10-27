/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle.message
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.rust.ide.annotator.fixes.MakePublicFix
import org.rust.ide.refactoring.move.common.RsMoveUtil.LOG
import org.rust.ide.refactoring.move.common.RsMoveUtil.containingModStrict
import org.rust.ide.refactoring.move.common.RsMoveUtil.convertFromPathOriginal
import org.rust.ide.refactoring.move.common.RsMoveUtil.isAbsolute
import org.rust.ide.refactoring.move.common.RsMoveUtil.isInsideMovedElements
import org.rust.ide.refactoring.move.common.RsMoveUtil.isSimplePath
import org.rust.ide.refactoring.move.common.RsMoveUtil.resolvesToAndAccessible
import org.rust.ide.refactoring.move.common.RsMoveUtil.startsWithSuper
import org.rust.ide.refactoring.move.common.RsMoveUtil.textNormalized
import org.rust.ide.refactoring.move.common.RsMoveUtil.toRsPath
import org.rust.ide.refactoring.move.common.RsMoveUtil.toRsPathInEmptyTmpMod
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.openapiext.runWithCancelableProgress

class ItemToMove(val item: RsItemElement) : ElementToMove()
class ModToMove(val mod: RsMod) : ElementToMove()

sealed class ElementToMove {
    val element: RsElement
        get() = when (this) {
            is ItemToMove -> item
            is ModToMove -> mod
        }

    companion object {
        fun fromItem(item: RsItemElement): ElementToMove = when (item) {
            is RsModItem -> ModToMove(item)
            else -> ItemToMove(item)
        }
    }
}

/**
 * Move refactoring supports moving files (to other directory) or top level items (to other file)
 *
 * ## High-level description
 * 1. Check conflicts (if new mod already has item with same name)
 * 2. Check visibility conflicts (target of any reference should remain accessible after move).
 *    We should check: `RsPath`, struct/enum field (struct literal, destructuring), struct/trait method call.
 *     - references from moved items (both to old mod and to other mods)
 *     - references to moved items (both from old mod and from other mods)
 * 3. Update `pub(in path)` visibility modifiers for moved items if necessary
 * 4. Move items to new mod
 *     - make moved items public if necessary
 *     - also make public items in old mod if necessary (TODO)
 * 5. Update outside references (from moved items - both to old mod and to other mods)
 *     - replace relative paths (which starts with `super::`)
 *     - add necessary imports (including trait imports - for trait methods)
 *         - usual imports (which already are in old mod)
 *         - imports to items in old mod (which are used by moved items)
 *     - replace paths which are still not resolved - e.g. previously path is absolute,
 *       but after move we should use path through reexports)
 * 6. Update inside references (to moved items - both from old mod and from other mods)
 *     - change existing imports
 *         - remove import if it is in new mod
 *     - fix unresolved paths (including trait methods)
 *
 * ## Implementation notes
 * Most important class is [RsMoveReferenceInfo].
 * It is used both for inside and outside references and for each [RsPath] provides new path to replace old path with.
 *
 * "Move file" and "Move items" has different processors (because of different UX).
 * So this class is created to be used by both processors.
 * It provides following methods:
 * 1. [findUsages] — just finds usages and convert them to our class [RsMoveUsageInfo]
 * 2. [preprocessUsages]
 *     - creates [RsMoveReferenceInfo] for all references
 *     - checks visibility conflicts
 * 3. [performRefactoring]
 *     - moves items/files
 *     - updates references using [RsMoveRetargetReferencesProcessor]
 */
class RsMoveCommonProcessor(
    private val project: Project,
    private var elementsToMove: List<ElementToMove>,
    private val targetMod: RsMod
) {

    private val psiFactory: RsPsiFactory = RsPsiFactory(project)
    private val codeFragmentFactory: RsCodeFragmentFactory = RsCodeFragmentFactory(project)

    private val sourceMod: RsMod = elementsToMove
        .also { if (it.isEmpty()) throw IncorrectOperationException("No items to move") }
        .map { it.element.containingModStrict }
        .distinct()
        .singleOrNull()
        ?: error("Elements to move must belong to single parent mod")

    private val pathHelper: RsMovePathHelper = RsMovePathHelper(project, targetMod)
    private lateinit var conflictsDetector: RsMoveConflictsDetector

    init {
        if (targetMod == sourceMod) {
            throw IncorrectOperationException("Source and destination modules should be different")
        }
    }

    fun findUsages(): Array<RsMoveUsageInfo> {
        return elementsToMove
            .flatMap { ReferencesSearch.search(it.element, GlobalSearchScope.projectScope(project)) }
            .filterNotNull()
            .mapNotNull { createMoveUsageInfo(it) }
            // sorting is needed for stable results in tests
            .sortedWith(compareBy({ it.element.containingMod.crateRelativePath }, { it.element.startOffset }))
            .toTypedArray()
    }

    private fun createMoveUsageInfo(reference: PsiReference): RsMoveUsageInfo? {
        val element = reference.element
        val target = reference.resolve() ?: return null

        // `use path1::{path2, path3}`
        //              ~~~~~  ~~~~~ TODO: don't ignore such paths
        if (element.ancestorStrict<RsUseGroup>() != null) return null

        return when {
            element is RsModDeclItem && target is RsFile -> RsModDeclUsageInfo(element, target)
            element is RsPath && target is RsQualifiedNamedElement -> RsPathUsageInfo(element, reference, target)
            else -> null
        }
    }

    fun preprocessUsages(usages: Array<UsageInfo>, conflicts: MultiMap<PsiElement, String>): Boolean {
        val title = message("refactoring.preprocess.usages.progress")
        return try {
            /**
             * We need to use [computeWithCancelableProgress] and not [runWithCancelableProgress],
             * because otherwise any exceptions will be silently ignored.
             * (exception will only be written to log, `runWithCancelableProgress` will return `true`)
             */
            project.computeWithCancelableProgress(title) {
                runReadAction {
                    // TODO: two threads
                    val outsideReferences = collectOutsideReferences()
                    val insideReferences = preprocessInsideReferences(usages)

                    if (!isUnitTestMode) {
                        ProgressManager.getInstance().progressIndicator.text = message("detecting.possible.conflicts")
                    }
                    conflictsDetector = RsMoveConflictsDetector(conflicts, elementsToMove, sourceMod, targetMod)
                    conflictsDetector.detectOutsideReferencesVisibilityProblems(outsideReferences)
                    conflictsDetector.detectInsideReferencesVisibilityProblems(insideReferences)
                }
            }
            true
        } catch (e: ProcessCanceledException) {
            false
        }
    }

    private fun collectOutsideReferences(): List<RsMoveReferenceInfo> {
        // we should collect:
        // - absolute references (starts with "::", "crate" or some crate name) to source or target crate
        // - references which starts with "super"
        // - references from old mod scope:
        //     - to items in old mod
        //     - to something which is imported in old mod

        val references = mutableListOf<RsMoveReferenceInfo>()
        for (path in movedElementsDeepDescendantsOfType<RsPath>(elementsToMove)) {
            if (path.parent is RsVisRestriction) continue
            if (path.containingMod != sourceMod  // path inside nested mod of moved element
                && !path.isAbsolute()
                && !path.startsWithSuper()
            ) continue
            if (!isSimplePath(path)) continue
            // TODO: support references to macros
            //  is is complicated: https://doc.rust-lang.org/reference/macros-by-example.html#scoping-exporting-and-importing
            //  also RsImportHelper currently does not work for macros: https://github.com/intellij-rust/intellij-rust/issues/4073
            val macroCall = path.ancestorStrict<RsMacroCall>()
            if (macroCall != null && macroCall.path.isAncestorOf(path)) continue

            // `use path1::{path2, path3}`
            //              ~~~~~  ~~~~~ TODO: don't ignore such paths
            if (path.ancestorStrict<RsUseGroup>() != null) continue

            val target = path.reference?.resolve() as? RsQualifiedNamedElement ?: continue
            // ignore relative references from child modules of moved file
            // because we handle them as inside references (in `preprocessInsideReferences`)
            val isSelfReference = target.isInsideMovedElements(elementsToMove)
            if (isSelfReference) continue

            val reference = createOutsideReferenceInfo(path, target) ?: continue
            path.putCopyableUserData(RS_MOVE_REFERENCE_INFO_KEY, reference)
            references += reference
        }
        for (patIdent in movedElementsShallowDescendantsOfType<RsPatIdent>(elementsToMove)) {
            val target = patIdent.patBinding.reference.resolve() as? RsQualifiedNamedElement ?: continue
            if (target !is RsStructItem && target !is RsEnumVariant && target !is RsConstant) continue
            val reference = createOutsideReferenceInfo(patIdent, target) ?: continue
            patIdent.putCopyableUserData(RS_MOVE_REFERENCE_INFO_KEY, reference)
            references += reference
        }
        return references
    }

    private fun createOutsideReferenceInfo(
        pathOriginal: RsElement,
        target: RsQualifiedNamedElement
    ): RsMoveReferenceInfo? {
        val path = convertFromPathOriginal(pathOriginal, codeFragmentFactory)

        if (path.isAbsolute()) {
            // when moving from binary to library crate, we should change path `library_crate::...` to `crate::...`
            // when moving from one library crate to another, we should change path `crate::...` to `first_library::...`
            val basePathTarget = path.basePath().reference?.resolve() as? RsMod
            if (basePathTarget != null
                && basePathTarget.crateRoot != sourceMod.crateRoot
                && basePathTarget.crateRoot != targetMod.crateRoot
            ) return null  // not needed to change path

            // ideally this check is enough and above check is not needed
            // but for some paths (e.g. `base64::decode`) `pathNew.reference.resolve()` is null,
            // though actually path will be resolved correctly after move
            val pathNew = path.textNormalized.toRsPath(codeFragmentFactory, targetMod)
            if (pathNew != null && pathNew.resolvesToAndAccessible(target)) return null  // not needed to change path
        }

        val pathNewFallback = if (path.containingMod == sourceMod) {
            // after move `path` will belong to `targetMod`
            target.qualifiedNameRelativeTo(targetMod)
                ?.toRsPath(codeFragmentFactory, targetMod)
        } else {
            target.qualifiedNameInCrate(targetMod)
                ?.toRsPathInEmptyTmpMod(codeFragmentFactory, psiFactory, targetMod)
        }
        val pathNewAccessible = RsImportHelper.findPath(targetMod, target)
            ?.toRsPathInEmptyTmpMod(codeFragmentFactory, psiFactory, targetMod)

        return RsMoveReferenceInfo(path, pathOriginal, pathNewAccessible, pathNewFallback, target)
    }

    private fun preprocessInsideReferences(usages: Array<UsageInfo>): List<RsMoveReferenceInfo> {
        val pathUsages = usages.filterIsInstance<RsPathUsageInfo>()
        for (usage in pathUsages) {
            usage.referenceInfo = createInsideReferenceInfo(usage.element, usage.target)
        }

        val originalReferences = pathUsages.map { it.referenceInfo }
        for (usage in pathUsages) {
            usage.referenceInfo = convertToFullReference(usage.referenceInfo) ?: usage.referenceInfo

            val target = usage.referenceInfo.target
            target.putCopyableUserData(RS_TARGET_BEFORE_MOVE_KEY, target)

            val pathOldOriginal = usage.referenceInfo.pathOldOriginal
            if (pathOldOriginal.isInsideMovedElements(elementsToMove)) {
                pathOldOriginal.putCopyableUserData(RS_PATH_OLD_BEFORE_MOVE_KEY, pathOldOriginal)
            }
        }
        return originalReferences
    }

    private fun createInsideReferenceInfo(pathOriginal: RsPath, target: RsQualifiedNamedElement): RsMoveReferenceInfo {
        val path = convertFromPathOriginal(pathOriginal, codeFragmentFactory)

        val isSelfReference = pathOriginal.isInsideMovedElements(elementsToMove)
        if (isSelfReference) {
            // after move path will be in `targetMod`
            // so we can refer to moved item just with its name
            check(target.containingModStrict == sourceMod)  // any inside reference is reference to moved item
            if (path.containingMod == sourceMod) {
                val pathNew = target.name?.toRsPath(codeFragmentFactory, targetMod)
                if (pathNew != null) return RsMoveReferenceInfo(path, pathOriginal, pathNew, pathNew, target)
            }
        }

        val pathNewAccessible = pathHelper.findPathAfterMove(path, target)
        val pathNewFallback = run {
            val targetModPath = targetMod.qualifiedNameRelativeTo(path.containingMod) ?: return@run null
            val targetName = target.name ?: return@run null
            val pathNewFallbackText = "$targetModPath::$targetName"
            pathNewFallbackText.toRsPath(codeFragmentFactory, context = path.context as? RsElement ?: path)
        }
        return RsMoveReferenceInfo(path, pathOriginal, pathNewAccessible, pathNewFallback, target)
    }

    /**
     * This method is needed in order to work with references to [RsItemElement]s, and not with references to [RsMod]s.
     * It is needed when one of moved elements is [RsMod].
     */
    private fun convertToFullReference(reference: RsMoveReferenceInfo): RsMoveReferenceInfo? {
        // Examples:
        // `mod1::mod2::mod3::Struct::<T>::func::<R>();`
        //  ^~~~~~~~~^ reference.pathOld
        //  ^~~~~~~~~~~~~~~~~~~~~~~~~~~~^ pathOldOriginal
        //  ^~~~~~~~~~~~~~~~~~~~~~~^ pathOld
        //
        // `use mod1::mod2::mod3;`
        //      ^~~~~~~~~^ reference.pathOld
        //      ^~~~~~~~~~~~~~~^ pathOldOriginal == pathOld

        if (isSimplePath(reference.pathOld) || reference.isInsideUseDirective) return null
        val pathOldOriginal = reference.pathOldOriginal.ancestors
            .takeWhile { it is RsPath }
            .map { it as RsPath }
            .firstOrNull { isSimplePath(it) }
            ?: return null
        val pathOld = convertFromPathOriginal(pathOldOriginal, codeFragmentFactory)
        if (!pathOld.textNormalized.startsWith(reference.pathOld.textNormalized)) {
            LOG.error("Expected '${pathOld.text}' to start with '${reference.pathOld.text}'")
            return null
        }

        if (pathOld.containingFile is DummyHolder) LOG.error("Path '${pathOld.text}' is inside dummy holder")
        val target = pathOld.reference?.resolve() as? RsQualifiedNamedElement ?: return null

        fun convertPathToFull(path: RsPath): RsPath? {
            val pathFullText = pathOld.textNormalized
                .replaceFirst(reference.pathOld.textNormalized, path.textNormalized)
            val pathFull = pathFullText.toRsPath(codeFragmentFactory, path) ?: return null
            if (pathFull.containingFile is DummyHolder) LOG.error("Path '${pathFull.text}' is inside dummy holder")
            return pathFull
        }

        val pathNewAccessible = reference.pathNewAccessible?.let { convertPathToFull(it) }
        val pathNewFallback = reference.pathNewFallback?.let { convertPathToFull(it) }

        return RsMoveReferenceInfo(pathOld, pathOldOriginal, pathNewAccessible, pathNewFallback, target)
    }

    fun performRefactoring(usages: Array<out UsageInfo>, moveElements: () -> List<ElementToMove>) {
        updateOutsideReferencesInVisRestrictions()

        elementsToMove = moveElements()

        val retargetReferencesProcessor = RsMoveRetargetReferencesProcessor(project, sourceMod, targetMod)
        val outsideReferences = restoreOutsideReferenceInfosAfterMove()
        retargetReferencesProcessor.retargetReferences(outsideReferences)

        val insideReferences = usages
            .filterIsInstance<RsPathUsageInfo>()
            .map { it.referenceInfo }
        updateInsideReferenceInfosIfNeeded(insideReferences)
        retargetReferencesProcessor.retargetReferences(insideReferences)
    }

    private fun updateOutsideReferencesInVisRestrictions() {
        for (visRestriction in movedElementsDeepDescendantsOfType<RsVisRestriction>(elementsToMove)) {
            visRestriction.updateScopeIfNecessary(psiFactory, targetMod)
        }
    }

    /**
     * Each outside reference is associated with some [RsPath] inside moved items.
     * After move this [RsPath] is invalidated and new [RsPath] is created.
     * We store [RsMoveReferenceInfo] for outside reference in copyable user data for this [RsPath].
     */
    private fun restoreOutsideReferenceInfosAfterMove(): List<RsMoveReferenceInfo> {
        return movedElementsDeepDescendantsOfType<RsElement>(elementsToMove)
            .mapNotNullTo(mutableListOf()) { pathOldOriginal ->
                val reference = pathOldOriginal.getCopyableUserData(RS_MOVE_REFERENCE_INFO_KEY)
                    ?: return@mapNotNullTo null
                pathOldOriginal.putCopyableUserData(RS_MOVE_REFERENCE_INFO_KEY, null)
                // because after move new `RsElement`s are created
                reference.pathOldOriginal = pathOldOriginal
                reference.pathOld = convertFromPathOriginal(pathOldOriginal, codeFragmentFactory)
                reference
            }
    }

    /**
     * After move old items are invalidated and new items ([RsElement]s) are created.
     * Thus we have to change `target` for inside references and change `pathOld` for self references.
     */
    private fun updateInsideReferenceInfosIfNeeded(references: List<RsMoveReferenceInfo>) {
        fun <T : RsElement> createMapping(key: Key<T>, aClass: Class<T>): Map<T, T> {
            return movedElementsShallowDescendantsOfType(elementsToMove, aClass)
                .mapNotNull { element ->
                    val elementOld = element.getCopyableUserData(key) ?: return@mapNotNull null
                    element.putCopyableUserData(key, null)
                    elementOld to element
                }
                .toMap()
        }

        val pathMapping = createMapping(RS_PATH_OLD_BEFORE_MOVE_KEY, RsElement::class.java)
        val targetMapping = createMapping(RS_TARGET_BEFORE_MOVE_KEY, RsQualifiedNamedElement::class.java)
        for (reference in references) {
            pathMapping[reference.pathOldOriginal]?.let { pathOldOriginal ->
                reference.pathOldOriginal = pathOldOriginal
                reference.pathOld = convertFromPathOriginal(pathOldOriginal, codeFragmentFactory)
            }

            val targetRestored = targetMapping[reference.target]
            // it is ok if `targetRestored` is null when target was inside a file which is a submodule of moved items
            // (so after move existing `target` remains valid)
            if (targetRestored != null) {
                reference.target = targetRestored
            } else if (reference.target.containingFile is DummyHolder) {
                LOG.error("Can't restore target ${reference.target}" +
                    " for reference '${reference.pathOldOriginal.text}' after move")
            }
        }
    }

    fun updateMovedItemVisibility(item: RsItemElement) {
        when (item.visibility) {
            is RsVisibility.Private -> {
                if (conflictsDetector.itemsToMakePublic.contains(item)) {
                    val itemName = item.name
                    val containingFile = item.containingFile
                    if (item !is RsNameIdentifierOwner) {
                        LOG.error("Unexpected item to make public: $item")
                        return
                    }
                    MakePublicFix(item, itemName, withinOneCrate = false)
                        .invoke(project, null, containingFile)
                }
            }
            is RsVisibility.Restricted -> run {
                val visRestriction = item.vis?.visRestriction ?: return@run
                visRestriction.updateScopeIfNecessary(psiFactory, targetMod)
            }
            RsVisibility.Public -> Unit  // already public, keep as is
        }
    }

    companion object {
        /**
         * Used for outside references (excluding references from nested moved files).
         * We store [RsMoveReferenceInfo] in `copyableUserData` for [RsPath] corresponding to reference,
         * so after move we could restore [RsMoveReferenceInfo].
         * (We have to do it because after move new psi elements are created)
         */
        private val RS_MOVE_REFERENCE_INFO_KEY: Key<RsMoveReferenceInfo> = Key.create("RS_MOVE_REFERENCE_INFO_KEY")

        /**
         * Used by self references (we treat them as inside references).
         * We store `pathOld` of such reference in `copyableUserData` for `pathOld` itself.
         * So after move we can create mapping between original [RsPath] in [sourceMod] and its copy in [targetMod],
         * and use this mapping to update `pathOld` in [RsMoveReferenceInfo].
         * (Type is [RsElement] and not [RsPath] because path to nullary enum variant in bindings is [RsPatIdent])
         */
        private val RS_PATH_OLD_BEFORE_MOVE_KEY: Key<RsElement> = Key.create("RS_PATH_OLD_BEFORE_MOVE_KEY")

        /**
         * Used by inside references (to descendants of moved items - but only in same file as [sourceMod]).
         * We store `target` of such reference in `copyableUserData` for `target` itself.
         * So after move we can create mapping between original [RsElement] in [sourceMod] and its copy in [targetMod],
         * and use this mapping to update `target` in [RsMoveReferenceInfo].
         */
        private val RS_TARGET_BEFORE_MOVE_KEY: Key<RsQualifiedNamedElement> = Key.create("RS_TARGET_BEFORE_MOVE_KEY")
    }
}
