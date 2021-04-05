/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is RsFile

    override fun processFile(file: PsiFile) = Runnable {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file)
        if (document != null) {
            documentManager.commitDocument(document)
        }
        executeForUseItem(file as RsFile)
        executeForExternCrate(file)
    }

    private fun executeForExternCrate(file: RsFile) {
        val first = file.childrenOfType<RsElement>()
            .firstOrNull { it !is RsInnerAttr } ?: return
        val externCrateItems = file.childrenOfType<RsExternCrateItem>()
        externCrateItems
            .sortedBy { it.referenceName }
            .mapNotNull { it.copy() as? RsExternCrateItem }
            .forEach { file.addBefore(it, first) }

        externCrateItems.forEach { it.delete() }
    }

    fun executeForUseItem(mod: RsMod) {
        val uses = mod.childrenOfType<RsUseItem>()
        if (uses.isNotEmpty()) {
            replaceOrderOfUseItems(mod, uses)
        }
        val mods = mod.childrenOfType<RsMod>()
        mods.forEach { executeForUseItem(it) }
    }

    companion object {

        /** Returns false if [useSpeck] is empty and should be removed */
        fun optimizeUseSpeck(psiFactory: RsPsiFactory, useSpeck: RsUseSpeck): Boolean {
            val useGroup = useSpeck.useGroup ?: return true
            useGroup.useSpeckList.forEach { optimizeUseSpeck(psiFactory, it) }
            if (removeUseSpeckIfEmpty(useSpeck)) return false
            if (removeCurlyBracesIfPossible(psiFactory, useSpeck)) return true

            val sortedList = useGroup.useSpeckList
                .sortedWith(compareBy<RsUseSpeck> { it.path?.self == null }.thenBy { it.pathText })
                .map { it.copy() }
            useGroup.useSpeckList.zip(sortedList).forEach { it.first.replace(it.second) }
            return true
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

        private fun replaceOrderOfUseItems(mod: RsMod, uses: Collection<RsUseItem>) {
            // We should ignore all items before `{` in inline modules
            val offset = if (mod is RsModItem) mod.lbrace.textOffset + 1 else 0
            val first = mod.childrenOfType<RsElement>()
                .firstOrNull { it.textOffset >= offset && it !is RsExternCrateItem && it !is RsAttr } ?: return
            val psiFactory = RsPsiFactory(mod.project)
            val sortedUsesGroups = uses
                .asSequence()
                .map { UseItemWrapper(it) }
                .filter {
                    val useSpeck = it.useItem.useSpeck ?: return@filter false
                    optimizeUseSpeck(psiFactory, useSpeck)
                }
                .groupBy({ it.packageGroupLevel }, { it })
                .map { (groupLevel, useItemWrapper) ->
                    val useItems = useItemWrapper
                        .sortedBy { it.useSpeckText }
                        .mapNotNull { it.useItem.copy() as? RsUseItem }
                    groupLevel to useItems
                }
                .sortedBy { it.first }

            for ((_, sortedUses) in sortedUsesGroups) {
                var lastAddedUseItem: PsiElement? = null
                for (importPath in sortedUses) {
                    lastAddedUseItem = mod.addBefore(importPath, first)
                    mod.addAfter(psiFactory.createNewline(), lastAddedUseItem)
                }
                mod.addAfter(psiFactory.createNewline(), lastAddedUseItem)
            }
            uses.forEach {
                (it.nextSibling as? PsiWhiteSpace)?.delete()
                it.delete()
            }
        }
    }
}

private val RsUseSpeck.pathText get() = path?.text?.toLowerCase()

private class UseItemWrapper(val useItem: RsUseItem) {
    private val basePath: RsPath? = useItem.useSpeck?.path?.basePath()

    val useSpeckText: String? = useItem.useSpeck?.pathText

    // `use` order:
    // 1. Standard library (stdlib)
    // 2. Related third party (extern crate)
    // 3. Local
    //    - otherwise
    //    - crate::
    //    - super::
    //    - self::
    val packageGroupLevel: Int = when {
        basePath?.self != null -> 6
        basePath?.`super` != null -> 5
        basePath?.crate != null -> 4
        else -> when (basePath?.reference?.resolve()?.containingCrate?.origin) {
            PackageOrigin.WORKSPACE -> 3
            PackageOrigin.DEPENDENCY -> 2
            PackageOrigin.STDLIB, PackageOrigin.STDLIB_DEPENDENCY -> 1
            null -> 3
        }
    }
}
