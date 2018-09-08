/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

import junit.framework.TestCase

/**
 * [SnapshotMap] shares snapshot logic with [ org.rust.lang.core.type.RsUnificationTableTest],
 * so snapshot/rollback mechanics mostly tested in [org.rust.lang.core.type.RsUnificationTableTest]
 */
class SnapshotMapTest : TestCase() {

    private object Key

    private sealed class Value {
        object Value1 : Value()
        object Value2 : Value()
    }

    fun `test simple insert`() {
        val map = SnapshotMap<Key, Value>()
        map[Key] = Value.Value1
        check(map[Key] == Value.Value1)
    }

    fun `test snapshot-rollback insert`() {
        val map = SnapshotMap<Key, Value>()
        val snapshot = map.startSnapshot()
        map[Key] = Value.Value1
        check(map[Key] == Value.Value1)
        snapshot.rollback()
        check(map[Key] == null)
    }

    fun `test snapshot-rollback overwrite`() {
        val map = SnapshotMap<Key, Value>()
        map[Key] = Value.Value1
        val snapshot = map.startSnapshot()
        map[Key] = Value.Value2
        check(map[Key] == Value.Value2)
        snapshot.rollback()
        check(map[Key] == Value.Value1)
    }
}
