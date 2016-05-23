package org.rust.lang.core.names

import org.rust.lang.core.names.parts.RustNamePart
import java.io.*
import java.util.*

/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * Serves primarily as an URI for items inside the Rust's crates
 *
 * @name        Non-qualified name-part
 * @qualifier   Qualified name-part
 */
open class RustQualifiedName(open val part: RustNamePart, open val qualifier: RustQualifiedName? = null) : Serializable {

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

    override fun equals(other: Any?): Boolean = other is RustQualifiedName && other.qualifier == qualifier && other.part == part

    override fun hashCode(): Int = Objects.hash(qualifier, part)

    companion object {

        fun writeTo(out: DataOutput, name: RustQualifiedName) {
            val bos = ByteArrayOutputStream()
            ObjectOutputStream(bos).writeObject(name)

            val bs = bos.toByteArray()

            out.writeInt(bs.size)
            out.write(bs)
        }

        fun readFrom(`in`: DataInput): RustQualifiedName? {
            val size = `in`.readInt()
            val bs = ByteArray(size)

            `in`.readFully(bs)

            return ObjectInputStream(ByteArrayInputStream(bs)).readObject() as? RustQualifiedName
        }

    }
}
