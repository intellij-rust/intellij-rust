package org.rust.lang.core.psi

import com.intellij.psi.util.PsiTreeUtil

val RustModItem.items: List<RustItem>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustItem::class.java)
