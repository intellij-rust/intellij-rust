package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.RsNamedElement

open class RsBaseTreeElement(element: RsNamedElement) : PsiTreeElementBase<RsNamedElement>(element) {

    override fun getPresentableText(): String? = element?.presentationInfo?.projectStructureItemText

    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()

}
