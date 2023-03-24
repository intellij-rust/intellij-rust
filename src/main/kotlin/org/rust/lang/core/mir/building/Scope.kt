/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.needsDrop
import org.rust.lang.core.mir.schemas.MirLocal
import org.rust.lang.core.mir.schemas.MirSourceInfo

class Scope(val source: MirSourceInfo) {
    private val drops = mutableListOf<Drop>()
    var cachedUnwindDrop: DropTree.DropNode? = null
        private set

    fun setCachedUnwindDrop(dropNode: DropTree.DropNode) {
        cachedUnwindDrop = dropNode
    }

    fun reversedDrops(): Iterator<Drop> = drops.asReversed().iterator()

    fun drops(): Iterator<Drop> = drops.iterator()

    fun scheduleDrop(local: MirLocal, dropKind: Drop.Kind) {
        if (dropKind == Drop.Kind.VALUE && !local.ty.needsDrop) return
        drops.add(Drop(local, dropKind, source.end))
    }

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}
