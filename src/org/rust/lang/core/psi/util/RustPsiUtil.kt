package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

public object RustPsiUtil {}

//
// Extension points
//

fun PsiElement.match(s: String): Boolean {
    return getText().equals(s);
}

val PsiElement.parenRelativeRange: TextRange?
    get() = this.getParent()?.let {
        TextRange(getStartOffsetInParent(), getStartOffsetInParent() + getTextLength())
    }
