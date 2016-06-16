package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.isStatic
import org.rust.lang.core.stubs.elements.RustImplMethodMemberStub
import javax.swing.Icon

abstract class RustImplMethodMemberImplMixin : RustFnImplMixin<RustImplMethodMemberStub>
                                             , RustImplMethodMemberElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustImplMethodMemberStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? {
        var icon = RustIcons.METHOD
        if (isStatic) {
            icon = icon.addStaticMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val isPublic: Boolean get() = stub?.isPublic ?: vis != null
}

