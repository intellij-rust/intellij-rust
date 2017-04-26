package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RustPsiImplUtil
import org.rust.lang.core.stubs.RsTraitItemStub
import javax.swing.Icon

val RsTraitItem.superTraits: Sequence<RsTraitItem> get() {
    val bounds = typeParamBounds?.polyboundList.orEmpty().asSequence()
    return bounds.mapNotNull { it.bound.traitRef?.resolveToTrait }
}

val RsTraitItem.flattenHierarchy: Sequence<RsTraitItem> get() {
    val result = mutableSetOf<RsTraitItem>()
    fun dfs(trait: RsTraitItem) {
        if (trait in result) return
        result += trait
        trait.superTraits.forEach(::dfs)
    }
    dfs(this)

    return result.asSequence()
}

abstract class RsTraitItemImplMixin : RsStubbedNamedElementImpl<RsTraitItemStub>, RsTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTraitItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.TRAIT)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: String? get() = RustPsiImplUtil.crateRelativePath(this)
}
