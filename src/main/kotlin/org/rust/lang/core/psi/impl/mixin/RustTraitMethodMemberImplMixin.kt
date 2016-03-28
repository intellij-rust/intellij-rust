package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustTraitMethodMember
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import javax.swing.Icon


abstract class RustTraitMethodMemberImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                             , RustTraitMethodMember {
    override val declarations: Collection<RustDeclaringElement>
        get() = listOfNotNull(selfArgument) + anonParams?.anonParamList.orEmpty()

    override fun getIcon(flags: Int): Icon {
        var icon = if (isAbstract) RustIcons.ABSTRACT_METHOD else RustIcons.METHOD
        if (isStatic)
            icon = icon.addStaticMark()

        return icon
    }

    val isAbstract: Boolean
        get() = block == null

}

val RustTraitMethodMember.isStatic: Boolean get() = selfArgument == null
