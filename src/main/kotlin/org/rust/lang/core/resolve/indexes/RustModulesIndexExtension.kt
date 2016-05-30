package org.rust.lang.core.resolve.indexes

import com.intellij.psi.PsiElement
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustVisitor
import java.io.DataInput
import java.io.DataOutput

class RustModulesIndexExtension : FileBasedIndexExtension<
    RustModulesIndexExtension.Key,
    RustModulesIndexExtension.Value>() {

    data class Key(val fileOrDirName: String) {
        object Descriptor : KeyDescriptor<Key> {
            override fun getHashCode(key: Key): Int = key.hashCode()
            override fun isEqual(key1: Key, key2: Key): Boolean = key1 == key2
            override fun save(out: DataOutput, key: Key) = out.writeUTF(key.fileOrDirName)
            override fun read(`in`: DataInput): Key = Key(`in`.readUTF())
        }
    }

    data class Value(val referenceOffset: Int) {
        object Externalizer : DataExternalizer<Value> {
            override fun save(out: DataOutput, value: Value) = out.writeInt(value.referenceOffset)
            override fun read(`in`: DataInput): Value = Value(`in`.readInt())
        }
    }

    override fun getVersion(): Int = 4

    override fun dependsOnFileContent(): Boolean = true

    override fun getName(): ID<Key, Value> = RustModulesIndex.ID

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(RustFileType)

    override fun getKeyDescriptor(): KeyDescriptor<Key> = Key.Descriptor

    override fun getValueExternalizer(): DataExternalizer<Value> = Value.Externalizer

    override fun getIndexer(): DataIndexer<Key, Value, FileContent> = Indexer

    object Indexer : DataIndexer<Key, Value, FileContent> {
        override fun map(inputData: FileContent): Map<Key, Value> {
            val result = mutableMapOf<Key, Value>()

            inputData.psiFile.accept(object : RustVisitor() {
                override fun visitElement(element: PsiElement) = element.acceptChildren(this)

                override fun visitModDeclItem(o: RustModDeclItem) {
                    val name = o.name ?: return
                    result += Key(name) to Value(o.textOffset)
                }
            })

            return result
        }
    }
}
