package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustUseItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.stubs.RustItemStub

abstract class RustUseItemImplMixin : RustItemImpl, RustUseItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val boundElements: Collection<RustNamedElement>
        get() {
            val globs = viewPath.useGlobList
            return if (globs == null) {
                // use foo::bar;
                listOfNotNull(viewPath.alias ?: viewPath.pathPart)
            } else {
                // use foo::bar::{...};
                globs.useGlobList.mapNotNull { it.boundElement }
            }
        }
}
