/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RustPsiImplUtil
import org.rust.lang.core.stubs.RsTypeAliasStub
import javax.swing.Icon

enum class RsTypeAliasRole {
    // Bump stub version if reorder fields
    FREE,
    TRAIT_ASSOC_TYPE,
    IMPL_ASSOC_TYPE
}

val RsTypeAlias.role: RsTypeAliasRole get() {
    val stub = stub
    if (stub != null) return stub.role
    return when (parent) {
        is RsItemsOwner -> RsTypeAliasRole.FREE
        is RsTraitItem -> RsTypeAliasRole.TRAIT_ASSOC_TYPE
        is RsImplItem -> RsTypeAliasRole.IMPL_ASSOC_TYPE
        else -> error("Unexpected parent of type alias: $parent")
    }
}

val RsTypeAlias.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi


abstract class RsTypeAliasImplMixin : RsStubbedNamedElementImpl<RsTypeAliasStub>, RsTypeAlias {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTypeAliasStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? = iconWithVisibility(flags, RsIcons.TYPE)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val isAbstract: Boolean get() = typeReference == null

    override val crateRelativePath: String? get() = RustPsiImplUtil.crateRelativePath(this)
}
