package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.stubs.RustFunctionElementStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RustFunctionImplMixin : RustFnImplMixin<RustFunctionElementStub>, RustFunctionElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustFunctionElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

    override fun getIcon(flags: Int): Icon = when (kind) {
        RustFunctionKind.FREE, RustFunctionKind.FOREIGN ->
            if (isTest) RustIcons.FUNCTION.addTestMark() else RustIcons.FUNCTION

        RustFunctionKind.TRAIT_METHOD, RustFunctionKind.IMPL_METHOD -> when {
            isStatic && isAbstract -> RustIcons.ABSTRACT_ASSOC_FUNCTION
            isStatic -> RustIcons.ASSOC_FUNCTION
            isAbstract -> RustIcons.ABSTRACT_METHOD
            else -> RustIcons.METHOD
        }
    }
}

enum class RustFunctionKind {
    FREE, FOREIGN, TRAIT_METHOD, IMPL_METHOD
}

val RustFunctionElement.kind: RustFunctionKind get() = when (parent) {
    is RustItemsOwner -> RustFunctionKind.FREE
    is RustForeignModItemElement -> RustFunctionKind.FOREIGN
    is RustTraitItemElement -> RustFunctionKind.TRAIT_METHOD
    is RustImplItemElement -> RustFunctionKind.IMPL_METHOD
    else -> error("Unexpected function parent: $parent")
}

val RustFunctionElement.superMethod: RustFunctionElement? get() {
    val rustImplItem = parentOfType<RustImplItemElement>() ?: return null
    val superTrait = rustImplItem.traitRef?.trait ?: return null

    return superTrait.functionList.find { it.name == this.name }
}
