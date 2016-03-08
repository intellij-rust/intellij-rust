package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.util.containers.HashMap
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.rustMod
import java.io.DataInput
import java.io.DataOutput

data class VirtualFileUrl(private val url: String) {
    constructor(file: VirtualFile) : this(file.url)

    fun resolve(): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl(url)

    fun writeTo(out: DataOutput) {
        IOUtil.writeUTF(out, url)
    }

    companion object {
        fun readFrom(`in`: DataInput): VirtualFileUrl? =
            IOUtil.readUTF(`in`)?.let { VirtualFileUrl(it) }
    }
}

// Maps child mod virtual file to parent mod virtual file
//
// Example
//
// ```
// # src/main.rs
// mod foo;
//
// # src/foo/mod.rs
// fn hello() {}
// ```
// will result in a mapping "src/foo/mod.rs" -> "src/main.rs"
class RustModulesIndexExtension : FileBasedIndexExtension<VirtualFileUrl, VirtualFileUrl>() {

    override fun getVersion(): Int = 3

    override fun dependsOnFileContent(): Boolean = true

    override fun getName(): ID<VirtualFileUrl, VirtualFileUrl> = RustModulesIndex.ID

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        DefaultFileTypeSpecificInputFilter(RustFileType)

    override fun getKeyDescriptor(): KeyDescriptor<VirtualFileUrl> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<VirtualFileUrl> = myValueExternalizer

    override fun getIndexer(): DataIndexer<VirtualFileUrl, VirtualFileUrl, FileContent> = myDataIndexer

    private object myKeyDescriptor : KeyDescriptor<VirtualFileUrl> {

        override fun save(out: DataOutput, url: VirtualFileUrl?) {
            url?.writeTo(out)
        }

        override fun read(`in`: DataInput): VirtualFileUrl? =
            VirtualFileUrl.readFrom(`in`)

        override fun isEqual(one: VirtualFileUrl?, other: VirtualFileUrl?): Boolean =
            one == other

        override fun getHashCode(value: VirtualFileUrl?): Int = value?.hashCode() ?: -1
    }

    private object myValueExternalizer : DataExternalizer<VirtualFileUrl> {

        override fun save(out: DataOutput, url: VirtualFileUrl?) {
            url?.writeTo(out)
        }

        override fun read(`in`: DataInput): VirtualFileUrl? =
            VirtualFileUrl.readFrom(`in`)
    }

    private object myDataIndexer : DataIndexer<VirtualFileUrl, VirtualFileUrl, FileContent> {
        override fun map(inputData: FileContent): Map<VirtualFileUrl, VirtualFileUrl> {
            val parentUrl = VirtualFileUrl(inputData.file)
            val map = HashMap<VirtualFileUrl, VirtualFileUrl>()

            // Something dodgy is going on here. Ideally, we would use `inputData.psiFile`, but
            // `inputData.psiFile.virtualFile` is `null`, which breaks code in `ResolveEngine`.
            // `FileContentImpl` stores virtualFile in a `IndexingDataKeys.VIRTUAL_FILE` user data key,
            // but it is not used anywhere. So lets fetch a psiFile from PsiManager.
            //
            // TODO: bypass the issue by not hooking into ResolveEngine at all?
            val psiFile = PsiManager.getInstance(inputData.project).findFile(inputData.file)

            psiFile?.rustMod?.acceptChildren(object : RustVisitor() {
                override fun visitModDeclItem(mod: RustModDeclItem) {
                    val vFile = mod.reference?.resolve()?.containingFile?.virtualFile ?: return
                    val childUrl = VirtualFileUrl(vFile)
                    map += childUrl to parentUrl
                }
            })
            return map
        }
    }

}
