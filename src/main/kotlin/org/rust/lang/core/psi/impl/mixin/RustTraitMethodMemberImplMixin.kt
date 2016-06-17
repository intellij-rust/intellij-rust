package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustTraitMethodMemberElement
import org.rust.lang.core.psi.isStatic
import org.rust.lang.core.stubs.elements.RustTraitMethodMemberElementStub
import javax.swing.Icon


abstract class RustTraitMethodMemberImplMixin : RustFnImplMixin<RustTraitMethodMemberElementStub>
                                              , RustTraitMethodMemberElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustTraitMethodMemberElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon {
        var icon = if (block == null) RustIcons.ABSTRACT_METHOD else RustIcons.METHOD
        if (isStatic) {
            icon = icon.addStaticMark()
        }
        return icon
    }

}

