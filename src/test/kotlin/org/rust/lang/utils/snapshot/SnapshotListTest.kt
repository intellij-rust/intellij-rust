/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

import junit.framework.TestCase

class SnapshotListTest : TestCase() {

    private sealed class Value {
        object Value1 : Value()
        object Value2 : Value()
    }

    fun `test simple add`() {
        val list = SnapshotList<Value>()
        list.add(Value.Value1)
        check(list.toList() == listOf(Value.Value1))
    }

    fun `test snapshot-rollback add 1`() {
        val list = SnapshotList<Value>()
        val snapshot = list.startSnapshot()
        list.add(Value.Value1)
        snapshot.rollback()
        check(list.isEmpty())
    }

    fun `test snapshot-rollback add 2`() {
        val list = SnapshotList<Value>()
        list.add(Value.Value1)
        val snapshot = list.startSnapshot()
        list.add(Value.Value2)
        check(list.toList() == listOf(Value.Value1, Value.Value2))
        snapshot.rollback()
        check(list.toList() == listOf(Value.Value1))
    }

    private fun <E> SnapshotList<E>.toList(): List<E> =
        iterator().asSequence().toList()
}
