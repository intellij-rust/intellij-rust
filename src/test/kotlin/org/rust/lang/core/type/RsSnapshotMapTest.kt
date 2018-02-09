/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import junit.framework.TestCase
import org.rust.lang.core.types.infer.SnapshotMap
import org.rust.lang.core.types.infer.UnificationTable

/**
 * [SnapshotMap] shares snapshot logic with [UnificationTable], so snapshot/rollback mechanics
 * mostly tested in [RsUnificationTableTest]
 */
class RsSnapshotMapTest : TestCase() {

    private object Key

    private sealed class Value {
        object Value1 : Value()
        object Value2 : Value()
    }

    fun `test simple insert`() {
        val map = SnapshotMap<Key, Value>()
        map.put(Key, Value.Value1)
        check(map.get(Key) == Value.Value1)
    }

    fun `test snapshot-rollback insert`() {
        val map = SnapshotMap<Key, Value>()
        val snapshot = map.startSnapshot()
        map.put(Key, Value.Value1)
        check(map.get(Key) == Value.Value1)
        snapshot.rollback()
        check(map.get(Key) == null)
    }

    fun `test snapshot-rollback overwrite`() {
        val map = SnapshotMap<Key, Value>()
        map.put(Key, Value.Value1)
        val snapshot = map.startSnapshot()
        map.put(Key, Value.Value2)
        check(map.get(Key) == Value.Value2)
        snapshot.rollback()
        check(map.get(Key) == Value.Value1)
    }
}
