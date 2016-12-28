package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustForeignModItemElement
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.stubs.RustFnItemElementStub
import org.rust.lang.core.stubs.RustFunctionElementStub
import org.rust.lang.core.symbols.RustPath

abstract class RustFunctionImplMixin : RustFnImplMixin<RustFunctionElementStub>, RustFunctionElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustFunctionElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)
}

val RustFunctionElement.isForeign: Boolean get() = parent is RustForeignModItemElement
