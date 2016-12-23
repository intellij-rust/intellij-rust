package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustTraitMethodMemberElement
import org.rust.lang.core.stubs.RustTraitMethodMemberElementStub


abstract class RustTraitMethodMemberImplMixin : RustFnImplMixin<RustTraitMethodMemberElementStub>,
                                                RustTraitMethodMemberElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustTraitMethodMemberElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParent(): PsiElement? = parentByStub

    override fun getIcon(flags: Int) = when {
        isStatic && isAbstract -> RustIcons.ABSTRACT_ASSOC_FUNCTION
        isStatic -> RustIcons.ASSOC_FUNCTION
        isAbstract -> RustIcons.ABSTRACT_METHOD
        else -> RustIcons.METHOD
    }

}

