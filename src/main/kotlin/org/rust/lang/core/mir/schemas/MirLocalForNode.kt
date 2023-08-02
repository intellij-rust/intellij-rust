/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirLocalForNode {
    data class One(val local: MirLocal) : MirLocalForNode()
    data class ForGuard(val refForGuard: MirLocal, val forArmBody: MirLocal) : MirLocalForNode()
}
