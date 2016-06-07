package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustItemElementImpl
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustFnItemImplMixin : RustItemElementImpl
                                   , RustFnItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)


    override fun getIcon(flags: Int): Icon {
        var icon = RustIcons.FUNCTION
        if (isTest) {
            icon = icon.addTestMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()

}

val RustFnItemElement.isTest: Boolean get() = queryAttributes.hasAtomAttribute("test")
