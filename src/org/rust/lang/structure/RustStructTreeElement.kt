package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.RustStructDeclField
import org.rust.lang.core.psi.RustStructItem

class RustStructTreeElement(element: RustStructItem) : PsiTreeElementBase<RustStructItem>(element) {

    override fun getPresentableText(): String? = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = arrayListOf<StructureViewTreeElement>()

        for (field in getDeclFields().orEmpty())
            result += (RustStructDeclFieldTreeElement(field))

        return result
    }

    fun getDeclFields(): Collection<RustStructDeclField>? {
        return element?.structDeclArgs?.structDeclFieldList
    }
}
