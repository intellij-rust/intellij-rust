/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

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
        val targets: MirSwitchTargets<BB>,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    data class FalseEdge<BB : MirBasicBlock>(
        val realTarget: BB,
        val imaginaryTarget: BB?,
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

    data class Call<BB : MirBasicBlock>(
        val callee: MirOperand,
        val args: List<MirOperand>,
        val destination: MirPlace,
        val target: BB?,
        val unwind: BB?,
        val fromCall: Boolean,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    data class Drop<BB : MirBasicBlock>(
        val place: MirPlace,
        val target: BB,
        val unwind: BB?,
        override val source: MirSourceInfo,
    ) : MirTerminator<BB>

    val successors: List<MirBasicBlock>
        get() = when (this) {
            is Return, is Resume, is Unreachable -> emptyList()
            is Assert -> listOfNotNull(target, unwind)
            is Goto -> listOf(target)
            is SwitchInt -> targets.targets
            is FalseUnwind -> listOfNotNull(realTarget, unwind)
            is Call -> listOfNotNull(target, unwind)
            is Drop -> listOfNotNull(target, unwind)
            is FalseEdge -> listOfNotNull(realTarget, imaginaryTarget)
        }

    companion object {
        /**
         * This is singleton because it is identified using reference identity (===)
         */
        val dummy = Resume(MirSourceInfo.fake)
    }
}
