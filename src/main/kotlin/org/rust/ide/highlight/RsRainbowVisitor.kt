/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType

class RsRainbowVisitor : RainbowVisitor() {
    override fun suitableForFile(file: PsiFile): Boolean = file is RsFile

    override fun clone(): HighlightVisitor = RsRainbowVisitor()

    override fun visit(function: PsiElement) {
        if (function !is RsFunction) return

        fun addInfo(ident: PsiElement, colorTag: String) {
            addInfo(getInfo(function, ident, colorTag, null))
        }

        val bindingToUniqueName: Map<RsPatBinding, String> = run {
            val allBindings = function.descendantsOfType<RsPatBinding>().filter { it.name != null }
            val byName = allBindings.groupBy { it.name }
            allBindings
                .map { it to "${it.name}#${byName[it.name]!!.indexOf(it)}" }
                .toMap()
        }

        for ((binding, name) in bindingToUniqueName) {
            addInfo(binding.referenceNameElement, name)
        }

        for (path in function.descendantsOfType<RsPath>()) {
            val target = path.reference.resolve() as? RsPatBinding ?: continue
            val colorTag = bindingToUniqueName[target] ?: return
            addInfo(path.referenceNameElement, colorTag)
        }
    }
}
