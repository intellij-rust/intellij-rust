package org.rust.lang.core.psi.impl.mixin

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.stubs.RsImplItemStub

abstract class RustImplItemImplMixin : RustStubbedElementImpl<RsImplItemStub>, RsImplItem {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsImplItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RustIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all

    override fun getPresentation(): ItemPresentation {
        val t = type
        if (t is RsBaseType) {
            val pres = (t.path?.reference?.resolve() as? RustNamedElement)?.presentation
            if (pres != null) {
                return PresentationData(pres.presentableText, pres.locationString, RustIcons.IMPL, null)
            }
        }
        return PresentationData(type?.text ?: "Impl", null, RustIcons.IMPL, null)
    }
}
