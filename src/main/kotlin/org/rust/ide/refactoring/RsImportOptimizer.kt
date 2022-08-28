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
        val first = file.childrenOfType<RsElement>()
            .firstOrNull { it !is RsInnerAttr } ?: return
        val externCrateItems = file.childrenOfType<RsExternCrateItem>()
        externCrateItems
            .sortedBy { it.referenceName }
            .mapNotNull { it.copy() as? RsExternCrateItem }
            .forEach { file.addBefore(it, first) }

        externCrateItems.forEach { it.delete() }
    }

    private fun optimizeAndReorderUseItems(file: RsFile) {
        file.forEachMod { mod, pathUsage ->
            val uses = mod.childrenOfType<RsUseItem>()
            replaceOrderOfUseItems(mod, uses, pathUsage)
        }
    }

    companion object {

        fun optimizeUseItems(file: RsFile) {
            val factory = RsPsiFactory(file.project)
            file.forEachMod { mod, pathUsage ->
                val uses = mod.childrenOfType<RsUseItem>()
                uses.forEach { optimizeUseItem(it, factory, pathUsage) }
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

        private fun replaceOrderOfUseItems(mod: RsMod, uses: Collection<RsUseItem>, pathUsage: PathUsageMap?) {
            // We should ignore all items before `{` in inline modules
            val offset = if (mod is RsModItem) mod.lbrace.textOffset + 1 else 0
            val first = mod.childrenOfType<RsElement>()
                .firstOrNull { it.textOffset >= offset && it !is RsExternCrateItem && it !is RsAttr } ?: return
            val psiFactory = RsPsiFactory(mod.project)
            val sortedUses = uses
                .asSequence()
                .map { UseItemWrapper(it) }
                .filter {
                    val useSpeck = it.useItem.useSpeck ?: return@filter false
                    optimizeUseSpeck(useSpeck, psiFactory, pathUsage)
                }
                .sorted()

            for ((useWrapper, nextUseWrapper) in sortedUses.withNext()) {
                val addedUseItem = mod.addBefore(useWrapper.useItem, first)
                mod.addAfter(psiFactory.createNewline(), addedUseItem)
                if (useWrapper.packageGroupLevel != nextUseWrapper?.packageGroupLevel) {
                    mod.addAfter(psiFactory.createNewline(), addedUseItem)
                }
            }
            uses.forEach {
                (it.nextSibling as? PsiWhiteSpace)?.delete()
                it.delete()
            }
        }
    }
}

private fun RsFile.forEachMod(callback: (RsMod, PathUsageMap?) -> Unit) {
    getAllModulesInFile()
        .filter { it.childOfType<RsUseItem>() != null }
        .map { it to getPathUsage(it) }
        .forEach { (mod, pathUsage) -> callback(mod, pathUsage) }
}

private fun RsFile.getAllModulesInFile(): List<RsMod> {
    val result = mutableListOf<RsMod>()
    fun go(mod: RsMod) {
        result += mod
        mod.childrenOfType<RsModItem>().forEach(::go)
    }
    go(this)
    return result
}

private fun getPathUsage(mod: RsMod): PathUsageMap? {
    if (!RsUnusedImportInspection.isEnabled(mod.project)) return null
    return mod.pathUsage
}
