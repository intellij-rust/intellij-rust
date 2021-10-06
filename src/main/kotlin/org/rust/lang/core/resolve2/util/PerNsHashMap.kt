/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import com.intellij.util.ArrayUtil.EMPTY_BYTE_ARRAY
import gnu.trove.THash
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.*

/**
 * This is memory-optimized implementation of `HashMap<String, PerNs>`.
 * On real projects only few percents of [PerNs] has more than one [VisItem].
 * So almost all [PerNs] has exactly one [VisItem],
 * and we can try to use `HashMap<String, Any>`, where value is either [PerNs] or [VisItem].
 * Here we go even further, and inline [VisItem] right into [items] array.
 *
 * Deletion is not supported.
 */
class PerNsHashMap<K : Any>(
    private val containingMod: ModData,
    private val rootMod: ModData,
) : THashMapBase<K, PerNs>() {

    /**
     * Each value is either [PerNs] (when it has more than one [VisItem]),
     * or [ModPath] corresponding to [VisItem.path] (in this case other [VisItem] fields are stored in [masks])
     */
    private var items: Array<Any? /* ModPath or PerNs */> = THash.EMPTY_OBJECT_ARRAY

    /**
     * Stores [VisItem] data in encoded form:
     * - First two bits encode namespace and [VisItem.isModOrEnum]
     * - Next three bits encode [VisItem.visibility]
     * - Next bit encodes [VisItem.isFromNamedImport] flag
     * - Last two bits are unused
     * See [encodeMask] and `decode*`
     */
    private var masks: ByteArray = EMPTY_BYTE_ARRAY

    override fun getValueAtIndex(index: Int): PerNs? {
        val path = items[index] ?: return null
        if (path !is ModPath) return path as PerNs
        val mask = masks[index].toInt()
        val isModOrEnum = decodeIsModOrEnum(mask)
        val namespace = decodeNamespace(mask)
        val visibility = decodeVisibility(mask)
        val isFromNamedImport = decodeIsFromNamedImport(mask)
        val visItem = VisItem(path, visibility, isModOrEnum, isFromNamedImport)

        val visItemArray = arrayOf(visItem)
        return when (namespace) {
            Namespace.Types -> PerNs(types = visItemArray)
            Namespace.Values -> PerNs(values = visItemArray)
            Namespace.Macros -> PerNs(macros = visItemArray)
            else -> error("unreachable")
        }
    }

    override fun setValueAtIndex(index: Int, value: PerNs) {
        val (visItem, namespace) = value.asSingleVisItem() ?: run {
            items[index] = value
            return
        }
        val mask = encodeMask(visItem, namespace) ?: run {
            items[index] = value
            return
        }

        items[index] = visItem.path
        masks[index] = mask
    }

    override fun createNewArrays(capacity: Int) {
        items = arrayOfNulls(capacity)
        masks = ByteArray(capacity)
    }

    override fun rehash(newCapacity: Int) {
        val oldItems = items
        val oldMasks = masks
        rehashTemplate(newCapacity) { newIndex, oldIndex ->
            items[newIndex] = oldItems[oldIndex]
            masks[newIndex] = oldMasks[oldIndex]
        }
    }

    override val size: Int
        get() = _size

    private fun encodeMask(visItem: VisItem, namespace: Namespace): Byte? {
        /* This is relatively hot method, so we use === instead of == as an optimization */

        val namespaceMask = when {
            visItem.isModOrEnum -> 0
            namespace === Namespace.Types -> 1
            namespace === Namespace.Values -> 2
            namespace === Namespace.Macros -> 3
            else -> error("unreachable")
        }

        // We don't use when with subject because it will call [Intrinsics.areEqual].
        // And we need to use === for better performance.
        val visibility = visItem.visibility
        val visibilityMask = when {
            visibility === Visibility.CfgDisabled -> 0
            visibility === Visibility.Invisible -> 1
            visibility === Visibility.Public -> 2
            visibility is Visibility.Restricted -> {
                val scope = visibility.inMod
                when {
                    // private
                    scope === containingMod -> 3
                    // pub(crate)
                    scope.isCrateRoot -> 4
                    // pub(super)
                    scope === containingMod.parent -> 5
                    else -> return null
                }
            }
            else -> error("unreachable")
        }

        val isFromNamedImportMask = if (visItem.isFromNamedImport) 1 else 0

        val mask = namespaceMask or (visibilityMask shl 2) or (isFromNamedImportMask shl 5)
        return mask.toByte()
    }

    private fun decodeIsFromNamedImport(mask: Int): Boolean = mask and 0b1_000_00 != 0

    private fun decodeIsModOrEnum(mask: Int): Boolean = mask and 0b11 == 0

    private fun decodeNamespace(mask: Int): Namespace =
        when (mask and 0b11) {
            0 -> Namespace.Types
            1 -> Namespace.Types
            2 -> Namespace.Values
            3 -> Namespace.Macros
            else -> error("unreachable")
        }

    private fun decodeVisibility(mask: Int): Visibility =
        when ((mask shr 2) and 0b111) {
            0 -> Visibility.CfgDisabled
            1 -> Visibility.Invisible
            2 -> Visibility.Public
            3 -> containingMod.visibilityInSelf
            4 -> rootMod.visibilityInSelf
            5 -> {
                val parent = containingMod.parent ?: error("Inconsistent mask in PerNsHashMap")
                parent.visibilityInSelf
            }
            else -> error("unreachable")
        }
}

private fun PerNs.asSingleVisItem(): Pair<VisItem, Namespace>? {
    if (types.size + values.size + macros.size != 1) return null
    types.singleOrNull()?.let { return it to Namespace.Types }
    values.singleOrNull()?.let { return it to Namespace.Values }
    macros.singleOrNull()?.let { return it to Namespace.Macros }
    return null
}
