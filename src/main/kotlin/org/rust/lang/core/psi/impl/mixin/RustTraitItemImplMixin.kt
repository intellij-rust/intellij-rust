package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustTraitItem
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon


abstract class RustTraitItemImplMixin : RustItemImpl, RustTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val declarations: Collection<RustDeclaringElement> get() = genericParams?.typeParamList.orEmpty()

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.TRAIT)
}
