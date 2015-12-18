package org.rust.lang.core.names

import com.intellij.util.io.IOUtil
import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.names.parts.RustNamePart
import org.rust.lang.core.resolve.indexes.RustModulePath
import java.io.DataInput
import java.io.DataOutput
import java.util.stream.IntStream

/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * Serves primarily as an URI for items inside the Rust's crates
 *
 * @name        Non-qualified name-part
 * @qualifier   Qualified name-part
 */
open class RustQualifiedName(open val part: RustNamePart, open val qualifier: RustQualifiedName? = null) {

    override fun toString(): String =
        "${qualifier?.toString()}::${part.toString()}"

    val tip: RustQualifiedName?
        get() {
            var tip: RustQualifiedName? = this
            while (qualifier != null)
                tip = qualifier

            return tip
        }

    fun remove(head: RustQualifiedName): RustQualifiedName? {
        return when (!equals(head)) {
            true -> RustQualifiedName(part, qualifier?.remove(head))
            else -> null
        }
    }

}
