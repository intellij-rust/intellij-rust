package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.stubs.RustFunctionElementStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RustFunctionImplMixin : RustStubbedNamedElementImpl<RustFunctionElementStub>, RustFunctionElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustFunctionElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

    final override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()

    override fun getIcon(flags: Int): Icon = when (role) {
        RustFunctionRole.FREE, RustFunctionRole.FOREIGN ->
            if (isTest) RustIcons.FUNCTION.addTestMark() else RustIcons.FUNCTION

        RustFunctionRole.TRAIT_METHOD, RustFunctionRole.IMPL_METHOD -> when {
            isStatic && isAbstract -> RustIcons.ABSTRACT_ASSOC_FUNCTION
            isStatic -> RustIcons.ASSOC_FUNCTION
            isAbstract -> RustIcons.ABSTRACT_METHOD
            else -> RustIcons.METHOD
        }
    }
}

val RustFunctionElement.isAbstract: Boolean get() = stub?.isAbstract ?: block == null
val RustFunctionElement.isStatic: Boolean get() = stub?.isStatic ?: parameters?.selfParameter == null
val RustFunctionElement.isTest: Boolean get() = stub?.isTest ?: queryAttributes.hasAtomAttribute("test")


enum class RustFunctionRole {
    // Bump stub version if reorder fields
    FREE,
    TRAIT_METHOD,
    IMPL_METHOD,
    FOREIGN
}

val RustFunctionElement.role: RustFunctionRole get() {
    val stub = stub
    if (stub != null) return stub.role
    return when (parent) {
        is RustItemsOwner -> RustFunctionRole.FREE
        is RustTraitItemElement -> RustFunctionRole.TRAIT_METHOD
        is RustImplItemElement -> RustFunctionRole.IMPL_METHOD
        is RustForeignModItemElement -> RustFunctionRole.FOREIGN
        else -> error("Unexpected function parent: $parent")
    }
}

val RustFunctionElement.superMethod: RustFunctionElement? get() {
    val rustImplItem = parentOfType<RustImplItemElement>() ?: return null
    val superTrait = rustImplItem.traitRef?.trait ?: return null

    return superTrait.functionList.find { it.name == this.name }
}
