package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.stubs.elements.RustImplMethodMemberElementStub
import javax.swing.Icon

abstract class RustImplMethodMemberImplMixin : RustFnImplMixin<RustImplMethodMemberElementStub>,
                                               RustImplMethodMemberElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustImplMethodMemberElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParent(): PsiElement? = parentByStub

    override fun getIcon(flags: Int): Icon? {
        var icon = RustIcons.METHOD
        if (isStatic) {
            icon = icon.addStaticMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this)
}

val RustImplMethodMemberElement.superMethod: RustNamedElement?
    get() {
        val rustImplItem = parentOfType<RustImplItemElement>() ?: return null
        val superTrait = rustImplItem.traitRef?.trait ?: return null

        return superTrait.traitMethodMemberList.find { it.name == this.name }
    }
