package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustInnerAttrElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import javax.swing.Icon

abstract class RustImplMethodMemberImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                            , RustImplMethodMemberElement {

    override val declarations: Collection<RustDeclaringElement> get() {
        val params = parameters ?: return emptyList()
        return listOfNotNull(params.selfArgument) + params.parameterList.orEmpty() + genericParams?.typeParamList.orEmpty()
    }

    override fun getIcon(flags: Int): Icon? {
        var icon = RustIcons.METHOD
        if (isStatic) {
            icon = icon.addStaticMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val innerAttrList: List<RustInnerAttrElement>
        get() = block?.innerAttrList.orEmpty()
}

val RustImplMethodMemberElement.isStatic: Boolean get() = parameters?.selfArgument == null
