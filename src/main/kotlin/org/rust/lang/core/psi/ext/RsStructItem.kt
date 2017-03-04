package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes.UNION
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RustPsiImplUtil
import org.rust.lang.core.stubs.RsStructItemStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

val RsStructItem.union: PsiElement?
    get() = node.findChildByType(UNION)?.psi


enum class RsStructKind {
    STRUCT,
    UNION
}

val RsStructItem.kind: RsStructKind get() = when {
    union != null -> RsStructKind.UNION
    else -> RsStructKind.STRUCT
}

abstract class RsStructItemImplMixin : RsStubbedNamedElementImpl<RsStructItemStub>, RsStructItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsStructItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.STRUCT)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}
