package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPat
import org.rust.lang.core.psi.RustPatVar

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

val RustPat.boundVariables: List<RustPatVar>
    get() {
        val result = arrayListOf<RustPatVar>()
        accept(object : RecursiveRustVisitor() {
            override fun visitPatVar(o: RustPatVar) {
                result.add(o)
            }
        })
        return result
    }
