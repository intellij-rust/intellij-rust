/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.inspections.lints.PathUsageMap
import org.rust.ide.inspections.lints.RsUnusedImportInspection
import org.rust.ide.inspections.lints.isUsed
import org.rust.ide.inspections.lints.pathUsage
import org.rust.ide.utils.import.COMPARATOR_FOR_SPECKS_IN_USE_GROUP
import org.rust.ide.utils.import.UseItemWrapper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocComment
import org.rust.stdext.withNext

class RsImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is RsFile

    override fun processFile(file: PsiFile) = Runnable {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file)
        if (document != null) {
            documentManager.commitDocument(document)
        }
        optimizeAndReorderUseItems(file as RsFile)
        reorderExternCrates(file)
    }

    private fun reorderExternCrates(file: RsFile) {
        val first = file.firstItem ?: return
        val externCrateItems = file.childrenOfType<RsExternCrateItem>()
        externCrateItems
            .sortedBy { it.referenceName }
            .mapNotNull { it.copy() as? RsExternCrateItem }
            .forEach { file.addBefore(it, first) }

        externCrateItems.forEach { it.delete() }
    }

    private fun optimizeAndReorderUseItems(file: RsFile) {
        val factory = RsPsiFactory(file.project)
        file.forEachScope { scope, uses, pathUsage ->
            when (scope) {
                is RsMod -> replaceOrderOfUseItems(scope, uses, pathUsage)
                is RsBlock -> {
                    uses.forEach { optimizeUseItem(it, factory, pathUsage) }
                }
            }
        }
    }

    companion object {

        fun optimizeUseItems(file: RsFile) {
            val factory = RsPsiFactory(file.project)
            file.forEachScope { scope, uses, pathUsage ->
                if (scope is RsMod) {
                    uses.forEach { optimizeUseItem(it, factory, pathUsage) }
                }
            }
        }

        private fun optimizeUseItem(useItem: RsUseItem, factory: RsPsiFactory, pathUsage: PathUsageMap?) {
            val useSpeck = useItem.useSpeck ?: return
            val used = optimizeUseSpeck(useSpeck, factory, pathUsage)
            if (!used) {
                (useItem.nextSibling as? PsiWhiteSpace)?.delete()
                useItem.delete()
            }
        }

        /** Returns false if [useSpeck] is empty and should be removed */
        private fun optimizeUseSpeck(
            useSpeck: RsUseSpeck,
            factory: RsPsiFactory,
            // PSI changes during optimizing increments modification tracker
            // So we pass [pathUsage] so it will not be recalculated
            pathUsage: PathUsageMap?,
        ): Boolean {
            val useGroup = useSpeck.useGroup
            if (useGroup == null) {
                return if (pathUsage != null && !useSpeck.isUsed(pathUsage)) {
                    useSpeck.deleteWithSurroundingComma()
                    false
                } else {
                    true
                }
            } else {
                useGroup.useSpeckList.forEach { optimizeUseSpeck(it, factory, pathUsage) }
                if (removeUseSpeckIfEmpty(useSpeck)) return false
                if (removeCurlyBracesIfPossible(factory, useSpeck)) return true
                useGroup.sortUseSpecks()
                return true
            }
        }

        fun RsUseGroup.sortUseSpecks() {
            val sortedList = useSpeckList
                .sortedWith(COMPARATOR_FOR_SPECKS_IN_USE_GROUP)
                .map { it.copy() }
            useSpeckList.zip(sortedList).forEach { it.first.replace(it.second) }
        }

        /** Returns true if successfully removed, e.g. `use aaa::{bbb};` -> `use aaa::bbb;` */
        private fun removeCurlyBracesIfPossible(psiFactory: RsPsiFactory, useSpeck: RsUseSpeck): Boolean {
            val name = useSpeck.useGroup?.asTrivial?.text ?: return false
            val path = useSpeck.path?.text
            val tempPath = "${if (path != null) "$path::" else ""}$name"
            val newUseSpeck = psiFactory.createUseSpeck(tempPath)
            useSpeck.replace(newUseSpeck)
            return true
        }

        /**
         * Returns true if [useSpeck] is empty and was successfully removed,
         * e.g. `use aaa::{bbb::{}, ccc, ddd};` -> `use aaa::{ccc, ddd};`
         */
        private fun removeUseSpeckIfEmpty(useSpeck: RsUseSpeck): Boolean {
            val useGroup = useSpeck.useGroup ?: return false
            if (useGroup.useSpeckList.isNotEmpty()) return false
            if (useSpeck.parent is RsUseGroup) {
                useSpeck.deleteWithSurroundingComma()
            }
            // else can't delete useSpeck.parent if it is RsUseItem, because it will cause invalidation exception
            return true
        }

        private fun replaceOrderOfUseItems(scope: RsItemsOwner, uses: Collection<RsUseItem>, pathUsage: PathUsageMap?) {
            // We should ignore all items before `{` in inline modules
            val offset = if (scope is RsModItem) scope.lbrace.textOffset + 1 else 0
            val first = scope.childrenOfType<RsElement>()
                .firstOrNull { it.textOffset >= offset && it !is RsExternCrateItem && it !is RsAttr && it !is RsDocComment } ?: return
            val psiFactory = RsPsiFactory(scope.project)
            val sortedUses = uses
                .asSequence()
                .map { UseItemWrapper(it) }
                .filter {
                    val useSpeck = it.useItem.useSpeck ?: return@filter false
                    optimizeUseSpeck(useSpeck, psiFactory, pathUsage)
                }
                .sorted()

            for ((useWrapper, nextUseWrapper) in sortedUses.withNext()) {
                val addedUseItem = scope.addBefore(useWrapper.useItem, first)
                scope.addAfter(psiFactory.createNewline(), addedUseItem)
                val addNewLine = useWrapper.packageGroupLevel != nextUseWrapper?.packageGroupLevel
                    && (nextUseWrapper != null || scope is RsMod)
                if (addNewLine) {
                    scope.addAfter(psiFactory.createNewline(), addedUseItem)
                }
            }
            uses.forEach {
                (it.nextSibling as? PsiWhiteSpace)?.delete()
                it.delete()
            }
        }
    }
}

private fun RsFile.forEachScope(callback: (RsItemsOwner, List<RsUseItem>, PathUsageMap?) -> Unit) {
    val usesByScope = descendantsOfType<RsUseItem>()
        .filterNot { it.isReexportOfLegacyMacro() }
        .groupBy { it.parent }
    for ((scope, uses) in usesByScope) {
        if (scope !is RsMod && scope !is RsBlock) continue
        val pathUsage = getPathUsage(scope as RsItemsOwner)
        callback(scope, uses, pathUsage)
    }
}

private fun getPathUsage(scope: RsItemsOwner): PathUsageMap? {
    if (!RsUnusedImportInspection.isEnabled(scope.project)) return null
    return scope.pathUsage
}

// `macro_rules! foo { () => {} }`
// `pub(crate) use foo_ as foo;`
// `pub(crate) use foo;`
private fun RsUseItem.isReexportOfLegacyMacro(): Boolean {
    val useSpeck = useSpeck ?: return false
    val useGroup = useSpeck.useGroup
    return if (useGroup == null) {
        useSpeck.isReexportOfLegacyMacro()
    } else {
        useSpeck.coloncolon == null && useGroup.useSpeckList.any { it.isReexportOfLegacyMacro() }
    }
}

private fun RsUseSpeck.isReexportOfLegacyMacro(): Boolean {
    val path = path ?: return false
    return path.coloncolon == null
        // TODO: Check not null when we will support resolving legacy macro in `use foo as bar;`
        && path.reference?.resolve().let { it is RsMacro || it == null && alias != null }
        && !isStarImport
        && useGroup == null
}
