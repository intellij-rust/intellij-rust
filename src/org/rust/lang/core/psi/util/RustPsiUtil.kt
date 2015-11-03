package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPat


//
// Extension points
//

fun PsiElement?.match(s: String?): Boolean {
    return this != null
            && s != null
            && text.equals(s);
}

val PsiElement.parentRelativeRange: TextRange?
    get() = this.parent?.let {
        TextRange(startOffsetInParent, startOffsetInParent + textLength)
    }

val RustPat.boundElements: List<RustNamedElement>
    get() {
        val result = arrayListOf<RustNamedElement>()

        accept(object : RecursiveRustVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is RustNamedElement)
                    result.add(element)
                super.visitElement(element)
            }
        })
        return result
    }

fun PsiElement.isBefore(other: PsiElement): Boolean = textOffset < other.textOffset
