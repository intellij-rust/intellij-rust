/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

interface MirSwitchTargets<BB : MirBasicBlock> {
    val values: List<Long>
    val targets: List<BB>
}
