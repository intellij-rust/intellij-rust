package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsTraitItemStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon


abstract class RsTraitItemImplMixin : RsStubbedNamedElementImpl<RsTraitItemStub>, RsTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTraitItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.TRAIT)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}
