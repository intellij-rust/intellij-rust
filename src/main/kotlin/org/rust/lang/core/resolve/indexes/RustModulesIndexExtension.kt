package org.rust.lang.core.resolve.indexes

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.HashMap
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.RustFileType
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.rustMod
import java.io.DataInput
import java.io.DataOutput

class RustModulesIndexExtension : FileBasedIndexExtension<RustCratePath, RustQualifiedName>() {

    override fun getVersion(): Int = 2

    override fun dependsOnFileContent(): Boolean = true

    override fun getName(): ID<RustCratePath, RustQualifiedName> = RustModulesIndex.ID

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        DefaultFileTypeSpecificInputFilter(RustFileType)

    override fun getKeyDescriptor(): KeyDescriptor<RustCratePath> = Companion.keyDescriptor

    override fun getValueExternalizer(): DataExternalizer<RustQualifiedName> = Companion.valueExternalizer

    override fun getIndexer(): DataIndexer<RustCratePath, RustQualifiedName, FileContent> = Companion.dataIndexer

    companion object {

        val keyDescriptor = object: KeyDescriptor<RustCratePath> {

            override fun save(out: DataOutput, path: RustCratePath?) {
                path?.let {
                    RustCratePath.writeTo(out, it)
                }
            }

            override fun read(`in`: DataInput): RustCratePath? =
                RustCratePath.readFrom(`in`)

            override fun isEqual(one: RustCratePath?, other: RustCratePath?): Boolean =
                one?.equals(other) ?: false

            override fun getHashCode(value: RustCratePath?): Int = value?.hashCode() ?: -1
        }

        val valueExternalizer = object: DataExternalizer<RustQualifiedName> {

            override fun save(out: DataOutput, name: RustQualifiedName?) {
                name?.let { RustQualifiedName.writeTo(`out`, it) }
            }

            override fun read(`in`: DataInput): RustQualifiedName? {
                return RustQualifiedName.readFrom(`in`)
            }

        }

        val dataIndexer =
            DataIndexer<RustCratePath, RustQualifiedName, FileContent> {
                val map = HashMap<RustCratePath, RustQualifiedName>()
                val file = it.file

                PsiManager.getInstance(it.project).findFile(file)?.let {
                    for ((qualName, targets) in process(it)) {
                        targets.forEach {
                            map.put(RustCratePath.devise(it), qualName)
                        }
                    }
                }

                map
            }

        private fun process(f: PsiFile): Map<RustQualifiedName, List<PsiFile>> {
            val raw = HashMap<RustQualifiedName, List<PsiFile>>()

            f.accept(object : RustVisitor() {

                //
                // TODO(kudinkin): move this to `RustVisitor`
                //
                override fun visitFile(file: PsiFile) {
                    file.rustMod?.let { visitMod(it) }
                }

                override fun visitModItem(m: RustModItem) {
                    visitMod(m)
                }

                private fun visitMod(m: RustMod) {
                    val resolved = arrayListOf<PsiFile>()

                    m.modDecls.forEach { decl ->
                        decl.reference?.let { ref ->
                            (ref.resolve() as RustMod?)?.let { mod ->
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
