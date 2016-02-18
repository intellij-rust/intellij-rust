package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustFnItemImplMixin : RustItemImpl
                                   , RustFnItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)


    override val declarations: Collection<RustDeclaringElement>
        get() = fnParams?.paramList.orEmpty().filterNotNull()

    override fun getIcon(flags: Int): Icon {
        var icon = RustIcons.FUNCTION
        if (isTest) {
            icon = icon.addTestMark()
        }
        return iconWithVisibility(flags, icon)
    }

    val isTest: Boolean get() = hasAttribute("test")

}

