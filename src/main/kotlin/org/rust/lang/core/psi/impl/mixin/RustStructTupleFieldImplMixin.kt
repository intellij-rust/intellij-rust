package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustStructTupleField
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustStructTupleFieldImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                            , RustStructTupleField {
    override val isPublic: Boolean
        get() = vis != null
}
