package org.rust.lang.core.modules

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.containers.HashMap
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.RustFileType
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.canonicalName
import org.rust.lang.core.psi.util.modDecls
import java.io.DataInput
import java.io.DataOutput

class RustModulesIndexExtension : FileBasedIndexExtension<VirtualFile, RustQualifiedName>() {

    override fun getVersion(): Int = 1

    override fun getName(): ID<VirtualFile, RustQualifiedName> = RustModulesIndex.indexID

    override fun dependsOnFileContent(): Boolean = true

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        DefaultFileTypeSpecificInputFilter(RustFileType)

    override fun getKeyDescriptor(): KeyDescriptor<VirtualFile> = keyDescriptor

    override fun getValueExternalizer(): DataExternalizer<RustQualifiedName> = valueExternalizer

    override fun getIndexer(): DataIndexer<VirtualFile, RustQualifiedName, FileContent> = dataIndexer

    companion object {
        val keyDescriptor = object: KeyDescriptor<VirtualFile> {

            override fun save(out: DataOutput, file: VirtualFile?) {
                file?.let{ IOUtil.writeUTF(out, it.canonicalPath!!) }
            }

            override fun read(`in`: DataInput): VirtualFile? {
                val fs = StandardFileSystems.local()

                val s = IOUtil.readUTF(`in`)
                return when (s) {
                    null -> null
                    else -> fs.findFileByPath(s)
                }
            }

            override fun isEqual(one: VirtualFile?, other: VirtualFile?): Boolean =
                one?.canonicalPath == other?.canonicalPath

            override fun getHashCode(value: VirtualFile?): Int = value?.hashCode() ?: -1
        }

        val valueExternalizer = object: DataExternalizer<RustQualifiedName> {

            override fun save(out: DataOutput, value: RustQualifiedName?) {
                value?.let { IOUtil.writeUTF(out, RustQualifiedName.toString(it)) }
            }

            override fun read(`in`: DataInput): RustQualifiedName? {
                return RustQualifiedName.parse(IOUtil.readUTF(`in`))
            }

        }

        val dataIndexer =
            DataIndexer<VirtualFile, RustQualifiedName, FileContent> {
                val map = HashMap<VirtualFile, RustQualifiedName>()

                process(it.psiFile).entrySet().forEach {
                    val qualName = it.key
                    it.value.forEach {
                        map.put(it, qualName)
                    }
                }

                map
            }

        private fun process(f: PsiFile): MultiMap<RustQualifiedName?, VirtualFile> {
            val raw = MultiMap<RustQualifiedName?, VirtualFile>()

            f.accept(object: RustVisitor() {

                // TODO(kudinkin): fix in `RustVisitor`
                override fun visitFile(file: PsiFile?) {
                    (file as? RustFileImpl)?.let {
                        it.mod?.accept(this)
                    }
                }

                override fun visitModItem(m: RustModItem) {
                    var cached: RustQualifiedName? = null

                    m.modDecls.forEach { decl ->
                        decl.reference?.let { ref ->
                            (ref.resolve() as RustModItem?)?.let { mod ->
                                if (cached == null)
                                    cached = m.canonicalName

                                // inclusionMap.put(mod.containingFile.virtualFile, cached)
                                raw.put(cached, listOf(mod.containingFile.virtualFile))
                            }
                        }
                    }

                    m.acceptChildren(this)
                }
            })

            return raw
        }
    }
}
