package org.rust.lang.core.psi.impl.mixin

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.psi.impl.RsStubbedElementImpl
import org.rust.lang.core.stubs.RsImplItemStub

abstract class RsImplItemImplMixin : RsStubbedElementImpl<RsImplItemStub>, RsImplItem {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsImplItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RsIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all

    override fun getPresentation(): ItemPresentation {
        val t = typeReference
        if (t is RsBaseType) {
            val pres = (t.path?.reference?.resolve() as? RsNamedElement)?.presentation
            if (pres != null) {
                return PresentationData(pres.presentableText, pres.locationString, RsIcons.IMPL, null)
            }
        }
        return PresentationData(typeReference?.text ?: "Impl", null, RsIcons.IMPL, null)
    }
}
