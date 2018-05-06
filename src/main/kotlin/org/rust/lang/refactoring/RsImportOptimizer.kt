/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiFile
import org.rust.ide.formatter.processors.asTrivial
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.childrenOfType

class RsImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile?): Boolean = file is RsFile

    override fun processFile(file: PsiFile?) = Runnable {
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

        private fun replaceOrderOfUseItems(file: RsMod, uses: Collection<RsUseItem>) {
            val first = file.childrenOfType<RsElement>()
                .firstOrNull { it !is RsExternCrateItem && it !is RsAttr } ?: return
            val psiFactory = RsPsiFactory(file.project)
            val sortedUses = uses
                .sortedBy { it.useSpeck?.pathText }
                .mapNotNull { it.copy() as? RsUseItem }
            sortedUses
                .mapNotNull { it.useSpeck }
                .forEach { optimizeUseSpeck(psiFactory, it) }
            for (importPath in sortedUses) {
                file.addBefore(importPath, first)
            }
            uses.forEach { it.delete() }
        }
    }
}

private val RsUseSpeck.pathText get() = path?.text?.toLowerCase()
