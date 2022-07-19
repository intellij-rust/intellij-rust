/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import org.rust.lang.core.macros.decl.DeclMacroExpander
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.stdext.HashCode
import java.lang.ref.WeakReference

private const val RANGE_MAP_ATTRIBUTE_VERSION = 2

const val MACRO_STORAGE_VERSION: Int = 1 +  // self version
    DeclMacroExpander.EXPANDER_VERSION +
    ProcMacroExpander.EXPANDER_VERSION +
    RANGE_MAP_ATTRIBUTE_VERSION

/** We use [WeakReference] because uncached [loadRangeMap] is quite cheap */
private val MACRO_RANGE_MAP_CACHE_KEY: Key<WeakReference<RangeMap>> = Key.create("MACRO_RANGE_MAP_CACHE_KEY")
private val RANGE_MAP_ATTRIBUTE = FileAttribute(
    "org.rust.macro.RangeMap",
    RANGE_MAP_ATTRIBUTE_VERSION,
    /* fixedSize = */ true // don't allocate extra space for each record
)

fun VirtualFile.writeRangeMap(ranges: RangeMap) {
    checkWriteAccessAllowed()

    @Suppress("UnstableApiUsage")
    RANGE_MAP_ATTRIBUTE.writeFileAttribute(this).use {
        ranges.writeTo(it)
    }

    if (getUserData(MACRO_RANGE_MAP_CACHE_KEY)?.get() != null) {
        putUserData(MACRO_RANGE_MAP_CACHE_KEY, WeakReference(ranges))
    }
}

fun VirtualFile.loadRangeMap(): RangeMap? {
    checkReadAccessAllowed()

    getUserData(MACRO_RANGE_MAP_CACHE_KEY)?.get()?.let { return it }

    @Suppress("UnstableApiUsage")
    val data = RANGE_MAP_ATTRIBUTE.readFileAttribute(this) ?: return null
    val ranges = RangeMap.readFrom(data)
    putUserData(MACRO_RANGE_MAP_CACHE_KEY, WeakReference(ranges))
    return ranges
}

/** The second part is a stored [MACRO_STORAGE_VERSION] value */
fun VirtualFile.extractMixHashAndMacroStorageVersion(): Pair<HashCode, Int>? {
    val name = name
    val firstUnderscoreIndex = name.indexOf('_')
    if (firstUnderscoreIndex == -1) return null
    val lastUnderscoreIndex = name.indexOf('_', firstUnderscoreIndex + 1)
    if (lastUnderscoreIndex == -1) return null
    val mixHash = HashCode.fromHexString(name.substring(0, firstUnderscoreIndex))
    val version = name.substring(
        lastUnderscoreIndex + 1,
        name.length - 3 // ".rs".length
    ).toIntOrNull() ?: -1
    return mixHash to version
}
