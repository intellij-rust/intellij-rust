package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.rust.lang.core.psi.RustPat
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.RustPatVar

//
// Extension points
//

fun PsiElement?.match(s: String?): Boolean {
    return this != null
        && s    != null
        && text.equals(s);
}

val PsiElement.parentRelativeRange: TextRange?
    get() = this.parent?.let {
        TextRange(startOffsetInParent, startOffsetInParent + textLength)
    }

val RustPat.boundIdentifiers: List<RustPatVar>
    get() {
        val result = arrayListOf<RustPatVar>()
        accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun elementFinished(element: PsiElement?) {
                when (element) {
                    is RustPatVar ->
                        result.add(element)
                }
            }
        })
        return result
    }