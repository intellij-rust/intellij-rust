package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.RustInnerAttrElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.queryAttributes
import org.rust.lang.core.stubs.elements.RustFnItemStub
import javax.swing.Icon

abstract class RustFnItemImplMixin : RustStubbedNamedElementImpl<RustFnItemStub>
                                   , RustFnItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustFnItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)


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
