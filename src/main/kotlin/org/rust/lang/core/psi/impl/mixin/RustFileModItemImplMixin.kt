package org.rust.lang.core.psi.impl.mixin

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.core.psi.util.RustModules
import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

public abstract class RustFileModItemImplMixin : RustModItemImpl
                                               , RustFileModItem {

    constructor(node: ASTNode?) : super(node)

    constructor(stub: RustItemStub?, nodeType: IStubElementType<*, *>?) : super(stub, nodeType)


    override fun getName(): String? =
        containingFile.let { file ->
            when (file.name) {
                RustModules.MOD_RS -> file.parent?.name
                else               -> file.name.removeSuffix(RustFileType.DEFAULTS.EXTENSION)
            }
        }

    override fun getIcon(flags: Int): Icon? =
        super.getIcon(flags)?.let {
            it.addVisibilityIcon(true)
        }
}

