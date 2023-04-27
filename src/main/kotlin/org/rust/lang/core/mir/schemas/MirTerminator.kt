/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Ty

sealed interface MirTerminator<out BB : MirBasicBlock> {
    val source: MirSourceInfo

    fun isDummy(): Boolean = this === dummy

    data class Return(
        override val source: MirSourceInfo,
    ) : MirTerminator<Nothing>

    data class Resume(
        override val source: MirSourceInfo,
    ) : MirTerminator<Nothing>

    data class Assert<BB : MirBasicBlock>(
        val cond: MirOperand,
        val expected: Boolean,
        val target: BB,
        val msg: MirAssertKind,
        val unwind: BB?,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    data class Goto<BB : MirBasicBlock>(
        val target: BB,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    data class SwitchInt<BB : MirBasicBlock>(
        val discriminant: MirOperand,
        val switchTy: Ty,
        val targets: MirSwitchTargets<BB>,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    data class FalseUnwind<BB : MirBasicBlock>(
        val realTarget: BB,
        val unwind: BB?,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    data class Unreachable(
        override val source: MirSourceInfo,
    ) : MirTerminator<Nothing>

    companion object {
        /**
         * This is singleton because it is identified using reference identity (===)
         */
        val dummy = Resume(MirSourceInfo.fake)
    }
}
