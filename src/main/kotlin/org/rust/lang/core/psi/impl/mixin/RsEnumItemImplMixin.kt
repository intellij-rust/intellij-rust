package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsEnumItemStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon


abstract class RsEnumItemImplMixin : RsStubbedNamedElementImpl<RsEnumItemStub>, RsEnumItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsEnumItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? =
        iconWithVisibility(flags, RsIcons.ENUM)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}
