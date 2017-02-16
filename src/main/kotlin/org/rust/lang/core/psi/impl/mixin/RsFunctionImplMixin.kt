package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.stubs.RsFunctionStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RsFunctionImplMixin : RsStubbedNamedElementImpl<RsFunctionStub>, RsFunction {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.crateRelativePath(this)

    final override val innerAttrList: List<RsInnerAttr>
        get() = block?.innerAttrList.orEmpty()

    override fun getIcon(flags: Int): Icon = when (role) {
        RsFunctionRole.FREE, RsFunctionRole.FOREIGN ->
            if (isTest) RsIcons.FUNCTION.addTestMark() else RsIcons.FUNCTION

        RsFunctionRole.TRAIT_METHOD, RsFunctionRole.IMPL_METHOD -> when {
            isAssocFn && isAbstract -> RsIcons.ABSTRACT_ASSOC_FUNCTION
            isAssocFn -> RsIcons.ASSOC_FUNCTION
            isAbstract -> RsIcons.ABSTRACT_METHOD
            else -> RsIcons.METHOD
        }
    }
}

val RsFunction.isAbstract: Boolean get() = stub?.isAbstract ?: (block == null)
val RsFunction.isAssocFn: Boolean get() = selfParameter == null
    && (role == RsFunctionRole.IMPL_METHOD || role == RsFunctionRole.TRAIT_METHOD)
val RsFunction.isTest: Boolean get() = stub?.isTest ?: queryAttributes.hasAtomAttribute("test")
val RsFunction.isInherentImpl: Boolean
    get() = (parent as? RsImplItem)?.let { return@let if (it.traitRef == null) it else null } != null

enum class RsFunctionRole {
    // Bump stub version if reorder fields
    FREE,
    TRAIT_METHOD,
    IMPL_METHOD,
    FOREIGN
}

val RsFunction.role: RsFunctionRole get() {
    val stub = stub
    if (stub != null) return stub.role
    return when (parent) {
        is RsItemsOwner -> RsFunctionRole.FREE
        is RsTraitItem -> RsFunctionRole.TRAIT_METHOD
        is RsImplItem -> RsFunctionRole.IMPL_METHOD
        is RsForeignModItem -> RsFunctionRole.FOREIGN
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

val RsFunction.default: PsiElement?
    get() = node.findChildByType(RsElementTypes.DEFAULT)?.psi

val RsFunction.title: String
    get() = when (role) {
        RsFunctionRole.TRAIT_METHOD ->
            if (selfParameter == null) "Trait function `$name`" else "Trait method `$name`"
        RsFunctionRole.IMPL_METHOD ->
            if (selfParameter == null) "Associated function `$name`" else "Method `$name`"
        RsFunctionRole.FOREIGN -> "Foreign function `$name`"
        else -> "Function `$name`"
    }
