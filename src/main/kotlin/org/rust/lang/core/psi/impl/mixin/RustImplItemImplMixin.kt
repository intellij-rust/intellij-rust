package org.rust.lang.core.psi.impl.mixin

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustPathTypeElement
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.stubs.elements.RustImplItemElementStub

abstract class RustImplItemImplMixin : RustStubbedElementImpl<RustImplItemElementStub>, RustImplItemElement {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RustImplItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RustIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all

    override fun getPresentation(): ItemPresentation {
        val t = type
        if (t is RustPathTypeElement) {
            val pres = t.path?.reference?.resolve()?.presentation
            if (pres != null) {
                return PresentationData(pres.presentableText, pres.locationString, RustIcons.IMPL, null)
            }
        }
        return PresentationData(type?.text ?: "Impl", null, RustIcons.IMPL, null)
    }
}
