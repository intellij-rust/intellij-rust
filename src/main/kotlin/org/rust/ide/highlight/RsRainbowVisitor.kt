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
import org.rust.lang.core.psi.ext.parentOfType

class RsRainbowVisitor : RainbowVisitor() {
    override fun suitableForFile(file: PsiFile): Boolean = file is RsFile

    override fun clone(): HighlightVisitor = RsRainbowVisitor()

    override fun visit(element: PsiElement) {
        val visitor = object : RsVisitor() {
            override fun visitFunction(fn: RsFunction) = processFunctionVisitor(fn)
        }
        element.accept(visitor)
    }

    private fun processFunctionVisitor(fn: RsFunction) {
        val patBindings = fn.descendantsOfType<RsPatBinding>()
        val bindings = patBindings
            .map { b -> b to patBindings.filter { it.name == b.name }.indexOf(b) }
        val paths = fn.descendantsOfType<RsPath>()
            .filter { it.parent !is RsCallExpr }
        bindings.forEach { (first, second) ->
            addInfo(first, first.referenceNameElement, second)
        }
        for ((first, second) in paths.map { it to it.reference.resolve() as? RsPatBinding }) {
            val binding = second ?: continue
            val num = bindings.find { it.first == binding }?.second ?: continue
            addInfo(binding, first, num)
        }
    }

    private fun addInfo(ref: RsPatBinding, rainbowElement: PsiElement, num: Int) {
        addInfo(getInfo(ref, rainbowElement, "${ref.name}_$num", null))
    }
}
