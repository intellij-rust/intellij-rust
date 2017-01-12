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
import org.rust.lang.core.stubs.RsFunctionStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RustFunctionImplMixin : RustStubbedNamedElementImpl<RsFunctionStub>, RsFunction {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

    final override val innerAttrList: List<RsInnerAttr>
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

val RsFunction.isAbstract: Boolean get() = stub?.isAbstract ?: block == null
val RsFunction.isStatic: Boolean get() = stub?.isStatic ?: selfParameter == null
val RsFunction.isTest: Boolean get() = stub?.isTest ?: queryAttributes.hasAtomAttribute("test")


enum class RustFunctionRole {
    // Bump stub version if reorder fields
    FREE,
    TRAIT_METHOD,
    IMPL_METHOD,
    FOREIGN
}

val RsFunction.role: RustFunctionRole get() {
    val stub = stub
    if (stub != null) return stub.role
    return when (parent) {
        is RustItemsOwner -> RustFunctionRole.FREE
        is RsTraitItem -> RustFunctionRole.TRAIT_METHOD
        is RsImplItem -> RustFunctionRole.IMPL_METHOD
        is RsForeignModItem -> RustFunctionRole.FOREIGN
        else -> error("Unexpected function parent: $parent")
    }
}

val RsFunction.superMethod: RsFunction? get() {
    val rustImplItem = parentOfType<RsImplItem>() ?: return null
    val superTrait = rustImplItem.traitRef?.trait ?: return null

    return superTrait.functionList.find { it.name == this.name }
}

val RsFunction.valueParameters: List<RsValueParameter>
    get() = valueParameterList?.valueParameterList.orEmpty()

val RsFunction.selfParameter: RsSelfParameter?
    get() = valueParameterList?.selfParameter
