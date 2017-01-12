package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsStructItemStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RustStructItemImplMixin : RustStubbedNamedElementImpl<RsStructItemStub>, RsStructItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsStructItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.STRUCT)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}

val RsStructItem.union: PsiElement?
    get() = node.findChildByType(RustTokenElementTypes.UNION)?.psi


enum class RustStructKind {
    STRUCT,
    UNION
}

val RsStructItem.kind: RustStructKind get() = when {
    union != null -> RustStructKind.UNION
    else -> RustStructKind.STRUCT
}
