package org.rust.lang.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustFileImpl

class RustSymbolIndex : ScalarIndexExtension<String>() {

    override fun getName() = RUST_SYMBOL_INDEX

    override fun dependsOnFileContent() = true

    override fun getVersion() = INDEX_VERSION

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = INDEXER

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor()

    override fun getInputFilter(): FileBasedIndex.InputFilter = RUST_INPUT_FILTER


    companion object {
        private val RUST_SYMBOL_INDEX: ID<String, Void> = ID.create("RUST_SYMBOL_INDEX")
        private val INDEX_VERSION: Int = 0
        private val RUST_INPUT_FILTER = FileBasedIndex.InputFilter { file ->
            file.fileType == RustFileType
        }

        fun getNames(project: Project): Collection<String> =
            FileBasedIndex.getInstance().getAllKeys(RUST_SYMBOL_INDEX, project)

        fun getItemsByName(name: String, project: Project, scope: GlobalSearchScope): Collection<RustNamedElement> =
            FileBasedIndex.getInstance()
                .getContainingFiles(RUST_SYMBOL_INDEX, name, scope)
                .mapNotNull { PsiManager.getInstance(project).findFile(it) }
                .flatMap { getElements(it) }
                .filter { it.name == name }
                .toList()

    }

    private object INDEXER : DataIndexer<String, Void, FileContent> {
        override fun map(inputData: FileContent): Map<String, Void?> =
            getElements(inputData.psiFile)
                .mapNotNull { it.name }
                .toMap({ it }, { null })
    }
}

private fun getElements(file: PsiFile): List<RustNamedElement> =
    (file as? RustFileImpl)?.mod?.itemList.orEmpty()
