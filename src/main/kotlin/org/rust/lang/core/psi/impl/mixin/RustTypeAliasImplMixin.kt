package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsTypeAliasStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RustTypeAliasImplMixin : RustStubbedNamedElementImpl<RsTypeAliasStub>, RsTypeAlias {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTypeAliasStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? = iconWithVisibility(flags, RustIcons.TYPE)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}

enum class RustTypeAliasRole {
    // Bump stub version if reorder fields
    FREE,
    TRAIT_ASSOC_TYPE,
    IMPL_ASSOC_TYPE
}

val RsTypeAlias.role: RustTypeAliasRole get() {
    val stub = stub
    if (stub != null) return stub.role
    return when (parent) {
        is RustItemsOwner -> RustTypeAliasRole.FREE
        is RsTraitItem -> RustTypeAliasRole.TRAIT_ASSOC_TYPE
        is RsImplItem -> RustTypeAliasRole.IMPL_ASSOC_TYPE
        else -> error("Unexpected parent of type alias: $parent")
    }
}

val RsTypeAlias.default: PsiElement?
    get() = node.findChildByType(RustTokenElementTypes.DEFAULT)?.psi
