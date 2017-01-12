package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsEnumVariantStub
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment
import javax.swing.Icon


abstract class RsEnumVariantImplMixin : RsStubbedNamedElementImpl<RsEnumVariantStub>, RsEnumVariant {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsEnumVariantStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = RsIcons.ENUM_VARIANT

    override val crateRelativePath: RustPath.CrateRelative? get() {
        val variantName = name ?: return null
        return parentEnum.crateRelativePath?.join(RustPathSegment.withoutGenerics(variantName))
    }
}

val RsEnumVariant.parentEnum: RsEnumItem get() = parent?.parent as RsEnumItem

fun foo(f: (String, String) -> Unit) {
    f("", "")
}
