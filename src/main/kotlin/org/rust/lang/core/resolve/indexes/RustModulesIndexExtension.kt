package org.rust.lang.core.resolve.indexes

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.HashMap
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.RustFileType
import org.rust.lang.core.names.RustFileModuleId
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.canonicalNameInFile
import org.rust.lang.core.psi.util.modDecls
import java.io.DataInput
import java.io.DataOutput

class RustModulesIndexExtension : FileBasedIndexExtension<RustModulePath, RustQualifiedName>() {

    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true

    override fun getName(): ID<RustModulePath, RustQualifiedName> = RustModulesIndex.ID

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        DefaultFileTypeSpecificInputFilter(RustFileType)

    override fun getKeyDescriptor(): KeyDescriptor<RustModulePath> = keyDescriptor

    override fun getValueExternalizer(): DataExternalizer<RustQualifiedName> = valueExternalizer

    override fun getIndexer(): DataIndexer<RustModulePath, RustQualifiedName, FileContent> = dataIndexer

    companion object {

        val keyDescriptor = object: KeyDescriptor<RustModulePath> {

            override fun save(out: DataOutput, path: RustModulePath?) {
                path?.let {
                    RustModulePath.writeTo(out, it)
                }
            }

            override fun read(`in`: DataInput): RustModulePath? =
                RustModulePath.readFrom(`in`)

            override fun isEqual(one: RustModulePath?, other: RustModulePath?): Boolean =
                one?.equals(other) ?: one == other

            override fun getHashCode(value: RustModulePath?): Int = value?.hashCode() ?: -1
        }

        val valueExternalizer = object: DataExternalizer<RustQualifiedName> {

            override fun save(out: DataOutput, name: RustQualifiedName?) {
                name?.let({
                    val tip = it as RustFileModuleId

                    RustModulePath      .writeTo(out, tip.part.path)
                    RustQualifiedName   .writeTo(out, name.remove(tip)!!)
                })
            }

            override fun read(`in`: DataInput): RustQualifiedName? {
                val path = RustModulePath   .readFrom(`in`)
                val name = RustQualifiedName.readFrom(`in`)

                return name?.put(path?.let { RustFileModuleId(it) })
            }

        }

        val dataIndexer =
            DataIndexer<RustModulePath, RustQualifiedName, FileContent> {
                val map = HashMap<RustModulePath, RustQualifiedName>()

                PsiManager.getInstance(it.project).findFile(it.file)?.let {
                    for ((qualName, targets) in process(it)) {
                        targets.forEach {
                            map.put(RustModulePath.devise(it), qualName)
                        }
                    }
                }

                map
            }

        private fun process(f: PsiFile): Map<RustQualifiedName, List<PsiFile>> {
            val raw = HashMap<RustQualifiedName, List<PsiFile>>()

            f.accept(object: RustVisitor() {

                //
                // TODO(kudinkin): move this to `RustVisitor`
                //
                override fun visitFile(file: PsiFile?) {
                    (file as? RustFileImpl)?.let {
                        it.mod?.accept(this)
                    }
                }

                override fun visitModItem(m: RustModItem) {
                    val resolved = arrayListOf<PsiFile>()

                    m.modDecls.forEach { decl ->
                        decl.reference?.let { ref ->
                            (ref.resolve() as RustModItem?)?.let { mod ->
                                resolved.add(mod.containingFile)
                            }
                        }
                    }

                    if (resolved.size > 0)
                        m.canonicalNameInFile?.let { raw.put(it, resolved) }

                    m.acceptChildren(this)
                }
            })

            return raw
        }
    }
}
