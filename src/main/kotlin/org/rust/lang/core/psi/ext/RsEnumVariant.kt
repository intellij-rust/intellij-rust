/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.stubs.RsEnumVariantStub
import javax.swing.Icon

val RsEnumVariant.parentEnum: RsEnumItem get() = parent?.parent as RsEnumItem

abstract class RsEnumVariantImplMixin : RsStubbedNamedElementImpl<RsEnumVariantStub>, RsEnumVariant {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsEnumVariantStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = RsIcons.ENUM_VARIANT

    override val crateRelativePath: String? get() {
        val variantName = name ?: return null
        return parentEnum.crateRelativePath?.let { "$it::$variantName" }
    }
}

