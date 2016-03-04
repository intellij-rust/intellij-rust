package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.core.psi.util.RustModules
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustFileModItemImplMixin : RustModItemImpl
                                        , RustFileModItem {

    constructor(node: ASTNode?) : super(node)

    constructor(stub: RustItemStub?, nodeType: IStubElementType<*, *>?) : super(stub, nodeType)


    override fun getName(): String? =
        containingFile.let { file ->
            when (file.name) {
                RustModules.MOD_RS -> file.parent?.name
                else               -> FileUtilRt.getNameWithoutExtension(file.name)
            }
        }

    override fun getIcon(flags: Int): Icon = RustIcons.MODULE
}

