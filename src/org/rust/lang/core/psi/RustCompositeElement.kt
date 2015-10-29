package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes

public interface RustCompositeElement : PsiElement
        , RustTokenElementTypes /* This is actually a hack to overcome GK limitations */ {
}
