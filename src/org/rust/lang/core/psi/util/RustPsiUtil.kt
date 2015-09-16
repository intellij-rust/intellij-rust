package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

public object RustPsiUtil {}

//
// Extension points
//

fun PsiElement?.match(s: String?): Boolean {
    return this != null
        && s    != null
        && getText().equals(s);
}

val PsiElement.parentRelativeRange: TextRange?
    get() = this.getParent()?.let {
        TextRange(getStartOffsetInParent(), getStartOffsetInParent() + getTextLength())
    }
