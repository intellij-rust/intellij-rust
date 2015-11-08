package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.getNextNonPhantomSibling

public abstract class RustItemImplMixin(node: ASTNode)  : RustNamedElementImpl(node)
                                                        , RustItem {

    override fun getNavigationElement(): PsiElement? {
        if (vis != null)
            return vis
        else if (outerAttrList.isNotEmpty())
            return outerAttrList.last().getNextNonPhantomSibling()
        else
            return firstChild
    }

    override fun getBoundElements(): Collection<RustNamedElement> =
        listOf(this)
}

