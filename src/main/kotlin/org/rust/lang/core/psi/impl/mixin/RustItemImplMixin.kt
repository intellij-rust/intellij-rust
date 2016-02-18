package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.impl.usefulName
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustItemImplMixin : RustStubbedNamedElementImpl<RustItemStub>
                                 , RustItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val boundElements: Collection<RustNamedElement>
        get() = listOf(this)

    override val isPublic: Boolean
        get() = vis != null

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getLocationString(): String? = "(in ${containingFile.usefulName})"

        override fun getIcon(unused: Boolean): Icon? = this@RustItemImplMixin.getIcon(0)

        override fun getPresentableText(): String? = name
    }
}


fun RustItem.hasAttribute(name: String): Boolean =
    outerAttrList.any { it.metaItem?.identifier?.text.equals(name) }
