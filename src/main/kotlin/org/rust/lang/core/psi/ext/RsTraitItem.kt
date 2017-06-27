/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Condition
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.IStubElementType
import com.intellij.util.Query
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RustPsiImplUtil
import org.rust.lang.core.stubs.RsTraitItemStub
import org.rust.lang.core.types.BoundElement
import org.rust.lang.utils.filterIsInstanceQuery
import org.rust.lang.utils.filterQuery
import org.rust.lang.utils.mapQuery
import javax.swing.Icon

val BoundElement<RsTraitItem>.flattenHierarchy: Collection<BoundElement<RsTraitItem>> get() {
    val result = mutableSetOf<BoundElement<RsTraitItem>>()
    val visited = mutableSetOf<RsTraitItem>()
    fun dfs(boundTrait: BoundElement<RsTraitItem>) {
        if (boundTrait.element in visited) return
        visited += boundTrait.element
        result += boundTrait
        boundTrait.element.superTraits.forEach(::dfs)
    }
    dfs(this)

    return result
}

fun RsTraitItem.searchForImplementations(): Query<RsImplItem> {
    return ReferencesSearch.search(this, this.useScope)
        .mapQuery { it.element.parent?.parent }
        .filterIsInstanceQuery<RsImplItem>()
        .filterQuery(Condition { it.typeReference != null })
}

private val RsTraitItem.superTraits: Sequence<BoundElement<RsTraitItem>> get() {
    val bounds = typeParamBounds?.polyboundList.orEmpty().asSequence()
    return bounds.mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
}

abstract class RsTraitItemImplMixin : RsStubbedNamedElementImpl<RsTraitItemStub>, RsTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTraitItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.TRAIT)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: String? get() = RustPsiImplUtil.crateRelativePath(this)
}
