/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("CanSealedSubClassBeObject")

package org.rust.lang.core.mir.schemas.impls

import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.schemas.MirTerminator.Companion.dummy
import org.rust.lang.core.types.ty.TyBool

class MirBasicBlockImpl private constructor(
    override val statements: MutableList<MirStatement>,
    override var terminator: MirTerminator<MirBasicBlockImpl>,
    override val unwind: Boolean
) : MirBasicBlock {
    constructor(unwind: Boolean) : this(mutableListOf(), dummy, unwind)

    /**
     * Sometimes it is needed to specify terminator source later than
     * actual terminator appears, in these cases this property comes in play.
     * In compiler this is done be specifying some dummy terminator and
     * later changing its kind.
     */
    private var terminatorSource: MirSourceInfo? = null

    fun setTerminatorSource(source: MirSourceInfo) {
        terminatorSource = source
    }

    fun pushAssign(place: MirPlace, rvalue: MirRvalue, source: MirSourceInfo): MirBasicBlockImpl {
        return push(MirStatement.Assign(place, rvalue, source))
    }

    fun pushStorageLive(local: MirLocal, source: MirSourceInfo): MirBasicBlockImpl {
        return push(MirStatement.StorageLive(local, source))
    }

    fun pushStorageDead(local: MirLocal, source: MirSourceInfo): MirBasicBlockImpl {
        return push(MirStatement.StorageDead(local, source))
    }

    fun terminateWithReturn(source: MirSourceInfo?) {
        terminator = MirTerminator.Return(getTerminatorSource(source))
    }

    fun terminateWithAssert(
        cond: MirOperand,
        expected: Boolean,
        block: MirBasicBlockImpl,
        source: MirSourceInfo?,
        msg: MirAssertKind,
    ) {
        terminator = MirTerminator.Assert(
            cond = cond,
            expected = expected,
            target = block,
            msg = msg,
            unwind = null,
            source = getTerminatorSource(source),
        )
    }

    fun terminateWithGoto(target: MirBasicBlockImpl, source: MirSourceInfo?) {
        terminator = MirTerminator.Goto(target, getTerminatorSource(source))
    }

    fun terminateWithIf(
        cond: MirOperand,
        thenBlock: MirBasicBlockImpl,
        elseBlock: MirBasicBlockImpl,
        source: MirSourceInfo?,
    ) {
        terminator = MirTerminator.SwitchInt(
            discriminant = cond,
            switchTy = TyBool.INSTANCE,
            targets = MirSwitchTargetsImpl.`if`(0, elseBlock, thenBlock),
            source = getTerminatorSource(source),
        )
    }

    private fun getTerminatorSource(source: MirSourceInfo?): MirSourceInfo {
        require((source == null) xor (terminatorSource == null)) {
            if (source != null) "Source can't be specified when terminator source is specified"
            else "Source must be specified if terminator source is not specified"
        }
        return source ?: terminatorSource!!
    }

    private fun push(statement: MirStatement): MirBasicBlockImpl = apply {
        statements.add(statement)
    }

    fun resume(source: MirSourceInfo?) {
        terminator = MirTerminator.Resume(getTerminatorSource(source))
    }

    fun unwindTerminatorTo(block: MirBasicBlockImpl) {
        val terminator = terminator
        when {
            terminator.isDummy() -> error("Terminator is expected to be specified by this moment")
            terminator is MirTerminator.Assert -> this.terminator = terminator.copy(unwind = block)
            else -> error("Terminator is not unwindable")
        }
    }
}
