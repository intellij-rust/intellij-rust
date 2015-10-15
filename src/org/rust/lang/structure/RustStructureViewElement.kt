package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import javax.swing.Icon

class RustStructureViewElement(val element: RustNamedElementImpl) : StructureViewTreeElement {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getLocationString(): String? = null

            override fun getIcon(unused: Boolean): Icon? = element.getIcon(0)

            override fun getPresentableText(): String? = element.name
        }
    }

    override fun getChildren(): Array<out TreeElement> {
        return arrayOf();
    }

    override fun canNavigate(): Boolean {
        return true
    }

    override fun canNavigateToSource(): Boolean {
        return true
    }

    override fun navigate(requestFocus: Boolean) {
        element.navigate(requestFocus)
    }

    override fun getValue(): Any? {
        return element
    }

}