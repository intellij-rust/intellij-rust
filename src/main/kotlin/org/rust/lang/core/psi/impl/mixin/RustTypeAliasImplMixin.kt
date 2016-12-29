package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.RustTypeAliasElementStub
import javax.swing.Icon

abstract class RustTypeAliasImplMixin : RustStubbedNamedElementImpl<RustTypeAliasElementStub>, RustTypeAliasElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustTypeAliasElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? = iconWithVisibility(flags, RustIcons.TYPE)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)
}

enum class RustTypeAliasRole {
    FREE,
    TRAIT_ASSOC_TYPE,
    IMPL_ASSOC_TYPE
}

val RustTypeAliasElement.role: RustTypeAliasRole get() = when (parent) {
    is RustItemsOwner -> RustTypeAliasRole.FREE
    is RustTraitItemElement -> RustTypeAliasRole.TRAIT_ASSOC_TYPE
    is RustImplItemElement -> RustTypeAliasRole.IMPL_ASSOC_TYPE
    else -> error("Unexpected parent of type alias: $parent")
}
