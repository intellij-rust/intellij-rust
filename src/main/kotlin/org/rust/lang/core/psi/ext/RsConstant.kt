package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.stubs.RsConstantStub

enum class RsConstantKind {
    STATIC,
    MUT_STATIC,
    CONST
}

val RsConstant.kind: RsConstantKind get() = when {
    mut != null -> RsConstantKind.MUT_STATIC
    const != null -> RsConstantKind.CONST
    else -> RsConstantKind.STATIC
}

enum class RsConstantRole {
    FREE,
    TRAIT_CONSTANT,
    IMPL_CONSTANT,
    FOREIGN
}

val RsConstant.role: RsConstantRole get() {
    return when (parent) {
        is RsItemsOwner -> RsConstantRole.FREE
        is RsTraitItem -> RsConstantRole.TRAIT_CONSTANT
        is RsImplItem -> RsConstantRole.IMPL_CONSTANT
        is RsForeignModItem -> RsConstantRole.FOREIGN
        else -> error("Unexpected constant parent: $parent")
    }
}

val RsConstant.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi


abstract class RsConstantImplMixin : RsStubbedNamedElementImpl<RsConstantStub>, RsConstant {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsConstantStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = iconWithVisibility(flags, when (kind) {
        RsConstantKind.CONST -> RsIcons.CONSTANT
        RsConstantKind.MUT_STATIC -> RsIcons.MUT_STATIC
        RsConstantKind.STATIC -> RsIcons.STATIC
    })

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)
}
