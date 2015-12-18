package org.rust.lang.core.names

import com.intellij.util.io.IOUtil
import org.rust.lang.core.names.parts.RustNamePart
import java.io.*

/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * Serves primarily as an URI for items inside the Rust's crates
 *
 * @name        Non-qualified name-part
 * @qualifier   Qualified name-part
 */
open class RustQualifiedName(open val part: RustNamePart, open val qualifier: RustQualifiedName? = null)
    : java.io.Serializable {

    override fun toString(): String =
        "${qualifier?.toString()}::${part.toString()}"

    val tip: RustQualifiedName?
        get() {
            var tip: RustQualifiedName? = this
            while (qualifier != null)
                tip = qualifier

            return tip
        }

    fun remove(head: RustQualifiedName): RustQualifiedName? =
        when (!equals(head)) {
            true -> RustQualifiedName(part, qualifier?.remove(head))
            else -> null
        }

    fun put(head: RustQualifiedName?): RustQualifiedName =
        when (qualifier) {
            null -> RustQualifiedName(part, head)
            else -> RustQualifiedName(part, qualifier?.put(head))
        }

    companion object {

        fun writeTo(out: DataOutput, name: RustQualifiedName) {
            val bos = ByteArrayOutputStream()
            ObjectOutputStream(bos).writeObject(name)

            IOUtil.writeUTF(out, String(bos.toByteArray(), Charsets.UTF_8))
        }

        fun readFrom(`in`: DataInput): RustQualifiedName? {
            val bs = IOUtil.readUTF(`in`)
            return ObjectInputStream(bs.byteInputStream()).readObject() as? RustQualifiedName
        }

    }
}
