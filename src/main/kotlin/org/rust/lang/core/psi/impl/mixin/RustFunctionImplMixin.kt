package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustForeignModItemElement
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.stubs.RustFunctionElementStub
import org.rust.lang.core.symbols.RustPath

abstract class RustFunctionImplMixin : RustFnImplMixin<RustFunctionElementStub>, RustFunctionElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustFunctionElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

//    override fun getIcon(flags: Int): Icon {
//        var icon = RustIcons.FUNCTION
//        if (isTest) {
//            icon = icon.addTestMark()
//        }
//        return iconWithVisibility(flags, icon)
//    }
//
//    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)
//
//    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

}

val RustFunctionElement.isForeign: Boolean get() = parent is RustForeignModItemElement
