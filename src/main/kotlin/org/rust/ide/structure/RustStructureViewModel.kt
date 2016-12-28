package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile

class RustStructureViewModel(editor: Editor?, file: RustFile) : TextEditorBasedStructureViewModel(editor, file),
                                                                StructureViewModel.ElementInfoProvider {

    override fun getRoot() = RustModTreeElement(psiFile)

    override fun getPsiFile(): RustFile = super.getPsiFile() as RustFile

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = element.value is RustFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement) =
        when (element.value) {
            is RustFieldDeclElement,
            is RustFunctionElement,
            is RustImplMethodMemberElement,
            is RustModDeclItemElement,
            is RustStaticItemElement,
            is RustTypeItemElement -> true
            else -> false
        }
}
