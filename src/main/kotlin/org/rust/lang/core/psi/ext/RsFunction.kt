/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsFunctionStub
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import javax.swing.Icon

val RsFunction.isAssocFn: Boolean get() = selfParameter == null
    && (role == RsFunctionRole.IMPL_METHOD || role == RsFunctionRole.TRAIT_METHOD)

val RsFunction.isTest: Boolean get() {
    val stub = stub
    return stub?.isTest ?: queryAttributes.hasAtomAttribute("test")
}

val RsFunction.isInherentImpl: Boolean get() {
    val parent = parent
    return parent is RsImplItem && parent.traitRef == null
}

val RsFunction.isConst: Boolean get() {
    val stub = stub
    return stub?.isConst ?: (const != null)
}

val RsFunction.isUnsafe: Boolean get() {
    val stub = stub
    return stub?.isUnsafe ?: (unsafe != null)
}

val RsFunction.isExtern: Boolean get() {
    val stub = stub
    return stub?.isExtern ?: (abi != null)
}

val RsFunction.abiName: String? get() {
    val stub = stub
    return stub?.abiName ?: abi?.stringLiteral?.text
}

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
    val superTrait = rustImplItem.traitRef?.resolveToTrait ?: return null

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

val RsFunction.returnType: Ty get() {
    val retType = retType ?: return TyUnit
    return retType.typeReference?.type ?: TyUnknown
}

val RsFunction.abi: RsExternAbi? get() = externAbi ?: (parent as? RsForeignModItem)?.externAbi

abstract class RsFunctionImplMixin : RsStubbedNamedElementImpl<RsFunctionStub>, RsFunction {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)

    override val isAbstract: Boolean get() = stub?.isAbstract ?: (block == null)

    override val crateRelativePath: String? get() = RustPsiImplUtil.crateRelativePath(this)

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
