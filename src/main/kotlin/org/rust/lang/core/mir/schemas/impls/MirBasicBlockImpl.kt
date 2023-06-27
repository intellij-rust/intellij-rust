/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas.impls

import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.schemas.MirTerminator.Companion.dummy

class MirBasicBlockImpl(
    override val index: Int,
    override val unwind: Boolean,
    override val statements: MutableList<MirStatement> = mutableListOf(),
    override var terminator: MirTerminator<MirBasicBlockImpl> = dummy,
) : MirBasicBlock {

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

    fun pushAssignConstant(place: MirPlace, constant: MirConstant, source: MirSourceInfo): MirBasicBlockImpl {
        return pushAssign(place, MirRvalue.Use(MirOperand.Constant(constant)), source)
    }

    fun pushStorageLive(local: MirLocal, source: MirSourceInfo): MirBasicBlockImpl {
        return push(MirStatement.StorageLive(local, source))
    }

    fun pushStorageDead(local: MirLocal, source: MirSourceInfo): MirBasicBlockImpl {
        return push(MirStatement.StorageDead(local, source))
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/cfg.rs#L81
    fun pushFakeRead(
        cause: MirStatement.FakeRead.Cause,
        place: MirPlace,
        source: MirSourceInfo,
    ): MirBasicBlockImpl {
        return push(MirStatement.FakeRead(cause, place, source))
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

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/cfg.rs#L121
    fun terminateWithGoto(target: MirBasicBlockImpl, source: MirSourceInfo?) {
        terminator = MirTerminator.Goto(target, getTerminatorSource(source))
    }

    fun terminateWithSwitchInt(
        discriminant: MirOperand,
        targets: MirSwitchTargets<MirBasicBlockImpl>,
        source: MirSourceInfo?,
    ) {
        terminator = MirTerminator.SwitchInt(
            discriminant = discriminant,
            targets = targets,
            source = getTerminatorSource(source),
        )
    }

    fun terminateWithIf(
        cond: MirOperand,
        thenBlock: MirBasicBlockImpl,
        elseBlock: MirBasicBlockImpl,
        source: MirSourceInfo?,
    ) {
        val targets = MirSwitchTargetsImpl.`if`(0, elseBlock, thenBlock)
        terminateWithSwitchInt(cond, targets, getTerminatorSource(source))
    }

    fun terminateWithFalseUnwind(realTarget: MirBasicBlockImpl, unwind: MirBasicBlockImpl?, source: MirSourceInfo?) {
        terminator = MirTerminator.FalseUnwind(
            realTarget = realTarget,
            unwind = unwind,
            source = getTerminatorSource(source),
        )
    }

    /**
     * Creates a false edge to [imaginaryTarget] and a real edge to [realTarget].
     * If [imaginaryTarget] is null, or is the same as the real target,
     * a Goto is generated instead to simplify the generated MIR.
     */
    fun terminateWithFalseEdges(
        realTarget: MirBasicBlockImpl,
        imaginaryTarget: MirBasicBlockImpl?,
        source: MirSourceInfo?
    ) {
        if (imaginaryTarget != null && imaginaryTarget != realTarget) {
            terminator = MirTerminator.FalseEdge(
                realTarget = realTarget,
                imaginaryTarget = imaginaryTarget,
                source = getTerminatorSource(source),
            )
        } else {
            terminateWithGoto(realTarget, source)
        }
    }

    fun terminateWithUnreachable(source: MirSourceInfo?) {
        terminator = MirTerminator.Unreachable(getTerminatorSource(source))
    }

    fun terminateWithResume(source: MirSourceInfo?) {
        terminator = MirTerminator.Resume(getTerminatorSource(source))
    }

    fun terminateWithCall(
        callee: MirOperand,
        args: List<MirOperand>,
        destination: MirPlace,
        target: MirBasicBlockImpl?,
        unwind: MirBasicBlockImpl?,
        fromCall: Boolean,
        source: MirSourceInfo?
    ) {
        terminator = MirTerminator.Call(
            callee,
            args,
            destination,
            target,
            unwind,
            fromCall,
            getTerminatorSource(source)
        )
    }

    fun terminateWithDrop(
        place: MirPlace,
        target: MirBasicBlockImpl,
        unwind: MirBasicBlockImpl?,
        source: MirSourceInfo?
    ) {
        terminator = MirTerminator.Drop(
            place,
            target,
            unwind,
            getTerminatorSource(source)
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

    fun unwindTerminatorTo(block: MirBasicBlockImpl) {
        val terminator = terminator
        when {
            terminator.isDummy() -> error("Terminator is expected to be specified by this moment")
            terminator is MirTerminator.Assert -> this.terminator = terminator.copy(unwind = block)
            terminator is MirTerminator.FalseUnwind -> this.terminator = terminator.copy(unwind = block)
            terminator is MirTerminator.Call -> this.terminator = terminator.copy(unwind = block)
            terminator is MirTerminator.Drop -> this.terminator = terminator.copy(unwind = block)
            else -> error("Terminator is not unwindable")
        }
    }
}
