package org.rust.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.impl.RustFileImpl

class RustStructureViewModel(editor: Editor?, file: RustFileImpl) :
        TextEditorBasedStructureViewModel(editor, file) {

    override fun getRoot(): StructureViewTreeElement {
        return RustFileTreeElement(psiFile)
    }

    override fun getPsiFile(): RustFileImpl {
        return super.getPsiFile() as RustFileImpl
    }
}


