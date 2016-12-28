package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustForeignModItemElement
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustInnerAttrElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.queryAttributes
import org.rust.lang.core.symbols.RustPath

abstract class RustFunctionImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustFunctionElement {
    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

    final override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()

    override val isAbstract: Boolean get() = block == null
    override val isStatic: Boolean get() = parameters?.selfArgument == null
    override val isTest: Boolean get() = queryAttributes.hasAtomAttribute("test")
}

val RustFunctionElement.isForeign: Boolean get() = parent is RustForeignModItemElement
