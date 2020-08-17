/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.CachedValueImpl
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.lang.core.resolve.RsCachedTypeAlias
import org.rust.lang.core.stubs.RsTypeAliasStub
import org.rust.lang.core.types.RsPsiTypeImplUtil
import org.rust.lang.core.types.ty.Ty
import javax.swing.Icon

val RsTypeAlias.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi

abstract class RsTypeAliasImplMixin : RsStubbedNamedElementImpl<RsTypeAliasStub>, RsTypeAlias {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTypeAliasStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? {
        val owner = owner
        val baseIcon = when (owner) {
            RsAbstractableOwner.Free, RsAbstractableOwner.Foreign -> RsIcons.TYPE_ALIAS
            is RsAbstractableOwner.Trait -> if (isAbstract) RsIcons.ABSTRACT_ASSOC_TYPE_ALIAS else RsIcons.ASSOC_TYPE_ALIAS
            is RsAbstractableOwner.Impl -> RsIcons.ASSOC_TYPE_ALIAS
        }
        return if (owner.isImplOrTrait && !owner.isInherentImpl) baseIcon else iconWithVisibility(flags, baseIcon)
    }

    override val isAbstract: Boolean get() = typeReference == null

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    val cachedImplItem: CachedValue<RsCachedTypeAlias> = CachedValueImpl {
        CachedValueProvider.Result(RsCachedTypeAlias(this), project.rustStructureModificationTracker)
    }
}
