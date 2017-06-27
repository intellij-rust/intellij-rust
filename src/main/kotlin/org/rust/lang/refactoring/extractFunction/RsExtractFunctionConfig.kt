/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.findStatementsInRange
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.psi.ext.selfParameter

class RsExtractFunctionConfig(val file: PsiFile, start: Int, end: Int) {
    var implType = RsWrapperType.Function
    var anchor: RsFunction? = null
    var elements: List<PsiElement> = emptyList()
    var name = ""
    var visibilityLevelPublic = false

    init {
        init(start, end)
    }

    private fun init(start: Int, end: Int) {
        elements = findStatementsInRange(file, start, end).asList()
        if (elements.isEmpty()) return
        val first = elements.first()
        val last = elements.last()

        // check element should be a part of one block
        val parentOfFirst = first.parentOfType<RsFunction>() ?: return
        anchor = last.parentOfType<RsFunction>() ?: return
        if (parentOfFirst != anchor) {
            return
        }

        // find wrapper parent type of selection: fun / method / trait impl method
        val impl = parentOfFirst.parent as? RsImplItem?
        if (impl != null) {
            if (impl.traitRef != null) {
                if (parentOfFirst.selfParameter == null) {
                    implType = RsWrapperType.TraitFunction
                } else {
                    implType = RsWrapperType.TraitMethod
                }
            } else {
                if (parentOfFirst.selfParameter == null) {
                    implType = RsWrapperType.ImplFunction
                } else {
                    implType = RsWrapperType.ImplMethod
                }
            }
        }

        //TODO: Find possible input and output parameter
    }

    fun isMethod() =
        implType == RsWrapperType.TraitMethod || implType == RsWrapperType.ImplMethod

}
