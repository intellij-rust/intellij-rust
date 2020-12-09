/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.project.Project
import org.rust.ide.refactoring.RsImportOptimizer
import org.rust.ide.refactoring.move.common.RsMoveUtil.LOG
import org.rust.ide.refactoring.move.common.RsMoveUtil.containingModStrict
import org.rust.ide.refactoring.move.common.RsMoveUtil.isAbsolute
import org.rust.ide.refactoring.move.common.RsMoveUtil.resolvesToAndAccessible
import org.rust.ide.refactoring.move.common.RsMoveUtil.startsWithSuper
import org.rust.ide.refactoring.move.common.RsMoveUtil.textNormalized
import org.rust.ide.refactoring.move.common.RsMoveUtil.toRsPath
import org.rust.ide.utils.import.insertUseItem
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsMoveRetargetReferencesProcessor(
    project: Project,
    private val sourceMod: RsMod,
    private val targetMod: RsMod
) {

    private val psiFactory: RsPsiFactory = RsPsiFactory(project)
    private val codeFragmentFactory: RsCodeFragmentFactory = RsCodeFragmentFactory(project)

    private val useSpecksToOptimize: MutableList<RsUseSpeck> = mutableListOf()
    private val filesToOptimizeImports: MutableSet<RsFile> =
        mutableSetOf(sourceMod.containingFile as RsFile, targetMod.containingFile as RsFile)

    fun retargetReferences(referencesAll: List<RsMoveReferenceInfo>) {
        val (referencesDirectly, referencesOther) = referencesAll
            .partition { it.isInsideUseDirective || it.forceReplaceDirectly }

        for (reference in referencesDirectly) {
            retargetReferenceDirectly(reference)
        }
        for (reference in referencesOther) {
            val pathOld = reference.pathOld
            if (pathOld.resolvesToAndAccessible(reference.target)) continue
            val success = !pathOld.isAbsolute() && tryRetargetReferenceKeepExistingStyle(reference)
            if (!success) {
                retargetReferenceDirectly(reference)
            }
        }
    }

    private fun retargetReferenceDirectly(reference: RsMoveReferenceInfo) {
        val pathNew = reference.pathNew ?: return
        replacePathOld(reference, pathNew)
    }

    // "keep existing style" means that
    // - if `pathOld` is `foo` then we keep it and add import for `foo`
    // - if `pathOld` is `mod1::foo`, then we change it to `mod2::foo` and add import for `mod2`
    // - etc for `outer1::mod1::foo`
    private fun tryRetargetReferenceKeepExistingStyle(reference: RsMoveReferenceInfo): Boolean {
        val pathOld = reference.pathOld
        val pathNew = reference.pathNew ?: return false

        val pathOldSegments = pathOld.textNormalized.split("::")
        val pathNewSegments = pathNew.textNormalized.split("::")

        val pathNewShortNumberSegments = adjustPathNewNumberSegments(reference, pathOldSegments.size)
        return doRetargetReferenceKeepExistingStyle(reference, pathNewSegments, pathNewShortNumberSegments)
    }

    private fun doRetargetReferenceKeepExistingStyle(
        reference: RsMoveReferenceInfo,
        pathNewSegments: List<String>,
        pathNewShortNumberSegments: Int
    ): Boolean {
        if (pathNewShortNumberSegments >= pathNewSegments.size) return false
        val pathNewShortText = pathNewSegments
            .takeLast(pathNewShortNumberSegments)
            .joinToString("::")
        val usePath = pathNewSegments
            .take(pathNewSegments.size - pathNewShortNumberSegments + 1)
            .joinToString("::")

        val containingMod = reference.pathOldOriginal.containingMod
        val pathNewShort = pathNewShortText.toRsPath(codeFragmentFactory, containingMod) ?: return false
        val containingModHasSameNameInScope = pathNewShort.basePath().reference?.resolve()
            .let {
                val elementToImport = usePath.toRsPath(codeFragmentFactory, containingMod)?.reference?.resolve()
                it != null && it != elementToImport && elementToImport != null
            }
        if (containingModHasSameNameInScope) {
            return doRetargetReferenceKeepExistingStyle(
                reference,
                pathNewSegments,
                pathNewShortNumberSegments + 1
            )
        }

        addImport(reference.pathOldOriginal, usePath)
        replacePathOld(reference, pathNewShort)
        return true
    }

    // if `target` is struct/enum/... then we add import for this item
    // if `target` is function then we add import for its `containingMod`
    // https://doc.rust-lang.org/book/ch07-04-bringing-paths-into-scope-with-the-use-keyword.html#creating-idiomatic-use-paths
    private fun adjustPathNewNumberSegments(reference: RsMoveReferenceInfo, numberSegments: Int): Int {
        val pathOldOriginal = reference.pathOldOriginal
        val target = reference.target

        // it is unclear how to replace relative reference starting with `super::` to keep its style
        // so lets always add full import for such references
        if (reference.pathOld.startsWithSuper()) {
            return if (target is RsFunction) 2 else 1
        }

        if (numberSegments != 1 || target !is RsFunction) return numberSegments
        val isReferenceBetweenElementsInSourceMod =
            // from item in source mod to moved item
            pathOldOriginal.containingMod == sourceMod && target.containingModStrict == targetMod
                // from moved item to item in source mod
                || pathOldOriginal.containingMod == targetMod && target.containingModStrict == sourceMod
        return if (isReferenceBetweenElementsInSourceMod) 2 else numberSegments
    }

    private fun replacePathOld(reference: RsMoveReferenceInfo, pathNew: RsPath) {
        val pathOld = reference.pathOld
        val pathOldOriginal = reference.pathOldOriginal

        if (pathOldOriginal !is RsPath) {
            replacePathOldInPatIdent(pathOldOriginal as RsPatIdent, pathNew)
            return
        }

        if (tryReplacePathOldInUseGroup(pathOldOriginal, pathNew)) return

        if (pathOld.textNormalized == pathNew.textNormalized) return
        if (pathOld != pathOldOriginal) run {
            if (!pathOldOriginal.textNormalized.startsWith(pathOld.textNormalized)) {
                LOG.error("Expected '${pathOldOriginal.text}' to start with '${pathOld.text}'")
                return@run
            }
            if (replacePathOldWithTypeArguments(pathOldOriginal, pathNew.textNormalized)) return
        }

        // when moving `fn foo() { use crate::mod2::bar; ... }` to `mod2`, we can just delete this import
        if (pathOld.parent is RsUseSpeck && pathOld.parent.parent is RsUseItem && !pathNew.hasColonColon) {
            pathOld.parent.parent.delete()
            return
        }
        pathOldOriginal.replace(pathNew)
    }

    private fun replacePathOldInPatIdent(pathOldOriginal: RsPatIdent, pathNew: RsPath) {
        if (pathNew.coloncolon != null) {
            LOG.error("Expected paths in patIdent to be one-segment, got: '${pathNew.text}'")
            return
        }
        val referenceName = pathNew.referenceName ?: error("Generated paths can't be incomplete")
        val patBindingNew = psiFactory.createIdentifier(referenceName)
        pathOldOriginal.patBinding.identifier.replace(patBindingNew)
    }

    private fun replacePathOldWithTypeArguments(pathOldOriginal: RsPath, pathNewText: String): Boolean {
        // `mod1::func::<T>`
        //  ^~~~~~~~~^ pathOld - replace by pathNewText
        //  ^~~~~~~~~~~~~~^ pathOldOriginal
        // we should accurately replace `pathOld`, so that all `PsiElement`s related to `T` remain valid
        // because T can contains `RsPath`s which also should be replaced later

        with(pathOldOriginal) {
            check(typeArgumentList != null)
            path?.delete()
            coloncolon?.delete()
            referenceNameElement?.delete()
            check(typeArgumentList != null)
        }

        val pathNew = pathNewText.toRsPath(psiFactory) ?: return false
        val elements = listOfNotNull(pathNew.path, pathNew.coloncolon, pathNew.referenceNameElement)
        for (element in elements.asReversed()) {
            pathOldOriginal.addAfter(element, null)
        }
        if (!pathOldOriginal.textNormalized.startsWith(pathNewText)) {
            LOG.error("Expected '${pathOldOriginal.text}' to start with '${pathNewText}'")
        }
        return true
    }

    // consider we move `foo1` and `foo2` from `mod1` to `mod2`
    // this methods replaces `use mod1::{foo1, foo2};` to `use mod2::{foo1, foo2};`
    //
    // TODO: improve for complex use groups, e.g.:
    //  `use mod1::{foo1, foo2::{inner1, inner2}};` (currently we create two imports)
    private fun tryReplacePathOldInUseGroup(pathOld: RsPath, pathNew: RsPath): Boolean {
        val useSpeck = pathOld.ancestorStrict<RsUseSpeck>() ?: return false
        val useGroup = useSpeck.parent as? RsUseGroup ?: return false
        val useItem = useGroup.ancestorStrict<RsUseItem>() ?: return false

        // Before:
        //   `use prefix::{mod1::foo::inner::{inner1, inner2}, other1, other2};`
        //                ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^ useGroup
        //                 ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^ useSpeck
        //                 ^~~~~~~~^ pathOld
        // After:
        //   `use prefix::{other1, other2};`
        //   `use prefix::mod2::foo::inner::{inner1, inner2};`
        pathOld.replace(pathNew)
        val useSpeckText = useSpeck.text
        // deletion should be before `insertUseItem`, otherwise `useSpeck` may be invalidated
        useSpeck.deleteWithSurroundingComma()
        if (useSpeckText.contains("::")) {
            insertUseItemAndCopyAttributes(useSpeckText, useItem)
        }

        // TODO: group paths by RsUseItem and handle together ?
        // consider pathOld == `foo1` in `use mod1::{foo1, foo2};`
        // we just removed `foo1` from old use group and added new import: `use mod1::{foo2}; use mod2::foo1;`
        // we can't optimize use speck right now,
        // because otherwise `use mod1::{foo2};` becomes `use mod1::foo2;` which destroys `foo2` reference
        useGroup.parentUseSpeck.let {
            useSpecksToOptimize += it
            filesToOptimizeImports += it.containingFile as RsFile
        }
        return true
    }

    private fun insertUseItemAndCopyAttributes(useSpeckText: String, existingUseItem: RsUseItem) {
        val containingMod = existingUseItem.containingMod
        if (existingUseItem.outerAttrList.isEmpty() && existingUseItem.vis == null) {
            containingMod.insertUseItem(psiFactory, useSpeckText)
        } else {
            val useItem = existingUseItem.copy() as RsUseItem
            val useSpeck = psiFactory.createUseSpeck(useSpeckText)
            useItem.useSpeck!!.replace(useSpeck)
            containingMod.addAfter(useItem, existingUseItem)
        }
        filesToOptimizeImports.add(containingMod.containingFile as RsFile)
    }

    private fun addImport(context: RsElement, usePath: String) {
        addImport(psiFactory, context, usePath)
        filesToOptimizeImports.add(context.containingFile as RsFile)
    }

    fun optimizeImports() {
        useSpecksToOptimize.forEach { RsImportOptimizer.optimizeUseSpeck(psiFactory, it) }
        filesToOptimizeImports.forEach { RsImportOptimizer().executeForUseItem(it) }
    }
}
