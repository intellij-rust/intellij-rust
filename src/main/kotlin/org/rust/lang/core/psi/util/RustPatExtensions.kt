package org.rust.lang.core.psi.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatElement
import org.rust.lang.core.psi.RustPatTupElement
import org.rust.lang.core.psi.visitors.RustRecursiveElementVisitor


/**
 * Extracts all the names bound by this particular [RustPatElement]
 */
val RustPatElement.boundElements: List<RustNamedElement>
    get() {
        val result = arrayListOf<RustNamedElement>()

        accept(object : RustRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is RustNamedElement)
                    result.add(element)
                super.visitElement(element)
            }
        })
        return result
    }


/**
 * Seeks for an index of the given [pat] inside the embracing [RustPatTupElement]
 */
fun RustPatTupElement.indexOf(pat: RustPatElement): Int {
    check(pat.parent === this)

    return patList.indexOf(pat)
}
