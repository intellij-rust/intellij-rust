package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.elements.RustTraitItemElementStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon


abstract class RustTraitItemImplMixin : RustStubbedNamedElementImpl<RustTraitItemElementStub>, RustTraitItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustTraitItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.TRAIT)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}
