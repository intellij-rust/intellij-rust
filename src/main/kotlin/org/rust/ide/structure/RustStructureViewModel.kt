package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.RustStructDeclField
import org.rust.lang.core.psi.impl.RustFile

class RustStructureViewModel(editor: Editor?, file: RustFile) : TextEditorBasedStructureViewModel(editor, file)
                                                              , StructureViewModel.ElementInfoProvider {

    override fun getRoot() = RustFileTreeElement(psiFile)

    override fun getPsiFile(): RustFile = super.getPsiFile() as RustFile

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) =
        when(element.value) {
            is RustFile -> true
            else        -> false
        }

    override fun isAlwaysLeaf(element: StructureViewTreeElement) =
        when(element.value) {
            is RustFnItem           -> true
            is RustImplMethod       -> true
            is RustStructDeclField  -> true
            else                    -> false
        }
}


