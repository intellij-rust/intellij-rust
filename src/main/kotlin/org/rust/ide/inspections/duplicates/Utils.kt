package org.rust.ide.inspections.duplicates

import com.intellij.util.SmartList
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustReferenceElement
import org.rust.lang.core.psi.referenceName
import java.util.*

fun<T: RustNamedElement> Collection<T>.findDuplicates(): Collection<T> =
    findDuplicatesBy(this, { it.name })

fun<T: RustReferenceElement> Collection<T>.findDuplicateReferences(): Collection<T> =
    findDuplicatesBy(this, { it.referenceName })


private fun <T : RustCompositeElement> findDuplicatesBy(items: Collection<T>, key: (T) -> String?): Collection<T> {
    val names = HashSet<String>()
    val result = SmartList<T>()
    for (item in items) {
        val name = key(item) ?: continue
        if (name in names) {
            result += item
        }

        names += name
    }

    return result
}
