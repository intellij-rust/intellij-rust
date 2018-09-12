/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

import junit.framework.TestCase

class SnapshotSetTest : TestCase() {

    private sealed class Value {
        object Value1 : Value()
        object Value2 : Value()
    }

    fun `test simple add`() {
        val set = SnapshotSet<Value>()
        set.add(Value.Value1)
        set.add(Value.Value2)
        set.add(Value.Value1)
        check(set.size == 2)
        check(set.contains(Value.Value1))
        check(set.contains(Value.Value2))
    }

    fun `test snapshot-rollback add 1`() {
        val set = SnapshotSet<Value>()
        val snapshot = set.startSnapshot()
        set.add(Value.Value1)
        snapshot.rollback()
        check(set.isEmpty())
    }

    fun `test snapshot-rollback add 2`() {
        val set = SnapshotSet<Value>()
        set.add(Value.Value1)
        val snapshot = set.startSnapshot()
        set.add(Value.Value2)
        check(set.toSet() == setOf(Value.Value1, Value.Value2))
        snapshot.rollback()
        check(set.toSet() == setOf(Value.Value1))
    }

    fun `test snapshot-rollback add 3`() {
        val set = SnapshotSet<Value>()
        set.add(Value.Value1)
        val snapshot = set.startSnapshot()
        set.add(Value.Value1)
        check(set.toSet() == setOf(Value.Value1))
        snapshot.rollback()
        check(set.toSet() == setOf(Value.Value1))
    }

    private fun <E> SnapshotSet<E>.toSet(): Set<E> =
        iterator().asSequence().toSet()
}
