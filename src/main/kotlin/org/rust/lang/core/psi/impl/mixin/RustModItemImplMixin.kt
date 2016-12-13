package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiDirectory
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.stubs.elements.RustModItemElementStub
import org.rust.lang.core.symbols.RustPath
import javax.swing.Icon

abstract class RustModItemImplMixin : RustStubbedNamedElementImpl<RustModItemElementStub>,
                                      RustModItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustModItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.MODULE)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this)

    override val `super`: RustMod get() = requireNotNull(parentOfType()) {
        "No parent mod for non-file mod at ${containingFile.virtualFile.path}:\n$text"
    }

    override val modName: String? get() = name

    override val crateRelativePath: RustPath? get() = RustPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean = true // Any inline nested mod owns a directory

    override val ownedDirectory: PsiDirectory? get() {
        val name = name ?: return null
        return `super`.ownedDirectory?.findSubdirectory(name)
    }

    override val isCrateRoot: Boolean = false

    override val innerAttrList: List<RustInnerAttrElement>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustInnerAttrElement::class.java)

    override val outerAttrList: List<RustOuterAttrElement>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustOuterAttrElement::class.java)

}
