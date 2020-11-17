/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import com.intellij.util.ArrayUtil.EMPTY_BYTE_ARRAY
import gnu.trove.THash
import gnu.trove.THashMap
import gnu.trove.TObjectHash
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.*
import java.util.*

/**
 * This is memory-optimized implementation of `HashMap<String, PerNs>`.
 * On real projects only few percents of [PerNs] has more then one [VisItem].
 * So almost all [PerNs] has exactly one [VisItem],
 * and we can try to use `HashMap<String, Any>`, where value is either [PerNs] or [VisItem].
 * Here we go even further, and inline [VisItem] right into [items] array.
 *
 * Deletion is not supported.
 */
class PerNsHashMap<K : Any>(
    private val containingMod: ModData,
    private val rootMod: ModData,
) : PerNsHashMapBase<K, PerNs>() {

    /**
     * Each value is either [PerNs] (when it has more then one [VisItem]),
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

    override fun setUp(initialCapacity: Int): Int {
        val capacity = super.setUp(initialCapacity)
        if (initialCapacity != THash.JUST_CREATED_CAPACITY) {
            items = arrayOfNulls(capacity)
            masks = ByteArray(capacity)
        }
        return capacity
    }

    override fun getValueAtIndex(index: Int): PerNs? {
        val path = items[index] ?: return null
        if (path !is ModPath) return path as PerNs
        val mask = masks[index].toInt()
        val isModOrEnum = decodeIsModOrEnum(mask)
        val namespace = decodeNamespace(mask)
        val visibility = decodeVisibility(mask)
        val isFromNamedImport = decodeIsFromNamedImport(mask)
        val visItem = VisItem(path, visibility, isModOrEnum, isFromNamedImport)
        // === as an optimization
        val types = visItem.takeIf { namespace === Namespace.Types }
        val values = visItem.takeIf { namespace === Namespace.Values }
        val macros = visItem.takeIf { namespace === Namespace.Macros }
        return PerNs(types, values, macros)
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

    override fun rehash(newCapacity: Int) {
        val oldItems = items
        val oldMasks = masks
        rehashTemplate(
            createNewArrays = {
                _set = arrayOfNulls(newCapacity)
                items = arrayOfNulls(newCapacity)
                masks = ByteArray(newCapacity)
            },
            moveValue = { newIndex, oldIndex ->
                items[newIndex] = oldItems[oldIndex]
                masks[newIndex] = oldMasks[oldIndex]
            }
        )
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
    if (values == null && macros == null) return types!! to Namespace.Types
    if (types == null && macros == null) return values!! to Namespace.Values
    if (types == null && values == null) return macros!! to Namespace.Macros
    return null
}

/**
 * Copy of [THashMap], abstracted over values array.
 * Deletion is not supported (we don't need it).
 */
@Suppress("UNCHECKED_CAST")
abstract class PerNsHashMapBase<K : Any, V : Any> : TObjectHash<K>(), MutableMap<K, V> {

    abstract fun getValueAtIndex(index: Int): V?
    abstract fun setValueAtIndex(index: Int, value: V)

    override val size: Int
        get() = _size

    override fun put(key: K, value: V): V? {
        var previous: V? = null
        var index = insertionIndex(key)
        val alreadyStored = index < 0
        if (alreadyStored) {
            index = -index - 1
            previous = getValueAtIndex(index)
        }
        _set[index] = key
        setValueAtIndex(index, value)
        if (!alreadyStored) {
            postInsertHook()
        }
        return previous
    }

    override fun putIfAbsent(key: K, value: V): V? {
        val index = insertionIndex(key)
        val alreadyStored = index < 0
        if (alreadyStored) return getValueAtIndex(-index - 1)

        _set[index] = key
        setValueAtIndex(index, value)
        postInsertHook()
        return null
    }

    /** Key deletion is not supported, that's why we `usedFreeSlot` is always true */
    private fun postInsertHook() = postInsertHook(true)

    protected inline fun rehashTemplate(createNewArrays: () -> Unit, moveValue: (Int, Int) -> Unit) {
        val oldCapacity = _set.size
        val oldKeys = _set
        createNewArrays()
        var i = oldCapacity
        while (i-- > 0) {
            if (oldKeys[i] != null) {
                val oldKey = oldKeys[i] as K
                val index = insertionIndex(oldKey)
                if (index < 0) {
                    throwObjectContractViolation(_set[-index - 1], oldKey)
                }
                _set[index] = oldKey
                moveValue(index, i)
            }
        }
    }

    override operator fun get(key: K): V? {
        val index = index(key)
        return if (index < 0) null else getValueAtIndex(index)
    }

    /** Unlike default implementation, doesn't call [containsKey] */
    override fun getOrDefault(key: K, defaultValue: V): V = get(key) ?: defaultValue

    override fun clear() = throw UnsupportedOperationException()

    override fun remove(key: K): V = throw UnsupportedOperationException()

    override fun removeAt(index: Int) = throw UnsupportedOperationException()

    override val values: MutableCollection<V>
        get() = ValueView()

    override val keys: MutableSet<K>
        get() = KeyView()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntryView()

    override fun containsValue(value: V): Boolean = throw UnsupportedOperationException()

    override fun containsKey(key: K): Boolean = contains(key)

    override fun putAll(from: Map<out K, V>) {
        ensureCapacity(from.size)
        for ((key, value) in from) {
            put(key, value)
        }
    }

    private inner class KeyView : AbstractSet<K>() {
        override fun iterator(): MutableIterator<K> {
            return object : THashIterator<K>() {
                override fun objectAtIndex(index: Int): K = _set[index] as K
            }
        }

        override val size: Int get() = _size

        override fun contains(element: K): Boolean = this@PerNsHashMapBase.contains(element)
    }

    private inner class ValueView : AbstractCollection<V>() {
        override fun iterator(): MutableIterator<V> {
            return object : THashIterator<V>() {
                override fun objectAtIndex(index: Int): V = getValueAtIndex(index)!!
            }
        }

        override val size: Int get() = _size

        override fun contains(element: V): Boolean = throw UnsupportedOperationException()
    }

    private inner class EntryView : AbstractSet<MutableMap.MutableEntry<K, V>>() {
        override fun iterator(): MutableIterator<Entry<K, V>> {
            return object : THashIterator<Entry<K, V>>() {
                override fun objectAtIndex(index: Int): Entry<K, V> {
                    return Entry(_set[index] as K, getValueAtIndex(index)!!)
                }
            }
        }

        override val size: Int get() = _size

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean = throw UnsupportedOperationException()
    }

    private class Entry<K : Any, V : Any>(override val key: K, override val value: V) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V = throw UnsupportedOperationException()
    }

    private abstract inner class THashIterator<V> : MutableIterator<V> {

        private var index: Int = capacity()

        /**
         * Returns the object at the specified index.
         * Subclasses should implement this to return the appropriate object for the given index.
         */
        protected abstract fun objectAtIndex(index: Int): V

        override operator fun hasNext(): Boolean = nextIndex() >= 0

        override fun next(): V {
            index = nextIndex()
            if (index < 0) throw NoSuchElementException()
            return objectAtIndex(index)
        }

        /**
         * Returns the index of the next value in the data structure
         * or a negative value if the iterator is exhausted.
         */
        private fun nextIndex(): Int {
            val set = _set
            var i = index
            @Suppress("ControlFlowWithEmptyBody")
            while (i-- > 0 && set[i] == null);
            return i
        }

        override fun remove() = throw UnsupportedOperationException()
    }
}
