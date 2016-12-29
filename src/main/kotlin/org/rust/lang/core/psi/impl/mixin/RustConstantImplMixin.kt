package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustConstantElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.RustConstantElementStub

abstract class RustConstantImplMixin : RustStubbedNamedElementImpl<RustConstantElementStub>, RustConstantElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RustConstantElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = iconWithVisibility(flags, when (kind) {
        RustConstantKind.CONST -> RustIcons.CONSTANT
        RustConstantKind.MUT_STATIC -> RustIcons.MUT_STATIC
        RustConstantKind.STATIC -> RustIcons.STATIC
    })

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)
}

enum class RustConstantKind {
    STATIC,
    MUT_STATIC,
    CONST
}

val RustConstantElement.kind: RustConstantKind get() = when {
    mut != null -> RustConstantKind.MUT_STATIC
    const != null -> RustConstantKind.CONST
    else -> RustConstantKind.STATIC
}
