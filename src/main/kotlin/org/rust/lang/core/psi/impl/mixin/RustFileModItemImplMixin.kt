package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiDirectory
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.util.crateRoots
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.core.psi.util.RustModules
import org.rust.lang.core.psi.util.module
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

    override val `super`: RustMod get() = super.`super`

    override val ownsDirectory: Boolean
        get() = containingFile.name == RustModules.MOD_RS || isCrateRoot

    override val ownedDirectory: PsiDirectory?
        get() = containingFile.originalFile.parent

    override val isCrateRoot: Boolean get() {
        val file = containingFile.originalFile.virtualFile ?: return false
        return file in (module?.crateRoots ?: emptyList())
    }

    override val isTopLevelInFile: Boolean = true

    override val modDecls: Collection<RustModDeclItem>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)
}

