package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

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
