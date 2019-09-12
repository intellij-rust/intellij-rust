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
import org.rust.ide.formatter.processors.asTrivial
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

    private fun executeForUseItem(mod: RsMod) {
        val uses = mod.childrenOfType<RsUseItem>()
        if (uses.isNotEmpty()) {
            replaceOrderOfUseItems(mod, uses)
        }
        val mods = mod.childrenOfType<RsMod>()
        mods.forEach { executeForUseItem(it) }
    }

    companion object {

        private fun optimizeUseSpeck(psiFactory: RsPsiFactory, useSpeck: RsUseSpeck) {
            if (removeCurlyBraces(psiFactory, useSpeck)) return
            val useSpeckList = useSpeck.useGroup?.useSpeckList ?: return
            if (useSpeckList.size < 2) return
            useSpeckList.forEach { optimizeUseSpeck(psiFactory, it) }
            val sortedList = useSpeckList
                .sortedWith(compareBy<RsUseSpeck> { it.path?.self == null }.thenBy { it.pathText })
                .map { it.copy() }

            useSpeckList.zip(sortedList).forEach { it.first.replace(it.second) }
        }

        private fun removeCurlyBraces(psiFactory: RsPsiFactory, useSpeck: RsUseSpeck): Boolean {
            val name = useSpeck.useGroup?.asTrivial?.text ?: return false
            val path = useSpeck.path?.text
            val tempPath = "${if (path != null) "$path::" else ""}$name"
            val newUseSpeck = psiFactory.createUseSpeck(tempPath)
            useSpeck.replace(newUseSpeck)
            return true
        }

        private fun replaceOrderOfUseItems(mod: RsMod, uses: Collection<RsUseItem>) {
            // We should ignore all items before `{` in inline modules
            val offset = if (mod is RsModItem) mod.lbrace.textOffset + 1 else 0
            val first = mod.childrenOfType<RsElement>()
                .firstOrNull { it.textOffset >= offset && it !is RsExternCrateItem && it !is RsAttr } ?: return
            val psiFactory = RsPsiFactory(mod.project)
            val sortedUsesGroups = uses
                .map { UseItemWrapper(it) }
                .groupBy({ it.packageGroupLevel }, { it })
                .map { (groupLevel, useItemWrapper) ->
                    val useItems = useItemWrapper
                        .sortedBy { it.useSpeckText }
                        .mapNotNull { it.useItem.copy() as? RsUseItem }
                    groupLevel to useItems
                }
                .sortedBy { it.first }
            sortedUsesGroups.forEach { (_, group) ->
                group
                    .mapNotNull { it.useSpeck }
                    .forEach { optimizeUseSpeck(psiFactory, it) }
            }

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
        else -> when (basePath?.reference?.resolve()?.containingCargoPackage?.origin) {
            PackageOrigin.WORKSPACE -> 3
            PackageOrigin.TRANSITIVE_DEPENDENCY -> 2
            PackageOrigin.DEPENDENCY -> 2
            PackageOrigin.STDLIB -> 1
            else -> 3
        }
    }
}
