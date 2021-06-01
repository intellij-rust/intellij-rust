/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import org.rust.lang.core.macros.MacroExpansionContext
import org.rust.lang.core.psi.RsProcMacroKind
import org.rust.stdext.readEnum
import org.rust.stdext.writeEnum
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * An error type for [org.rust.lang.core.psi.ext.expansionResult]
 */
sealed class GetMacroExpansionError {
    object MacroExpansionIsDisabled : GetMacroExpansionError()
    object MacroExpansionEngineIsNotReady : GetMacroExpansionError()
    object IncludingFileNotFound : GetMacroExpansionError()
    object OldEngineStd : GetMacroExpansionError()

    object MemExpDuringMacroExpansion : GetMacroExpansionError()
    object MemExpAttrMacro : GetMacroExpansionError()
    data class MemExpParsingError(
        val expansionText: CharSequence,
        val context: MacroExpansionContext
    ) : GetMacroExpansionError()

    object NextStepMacroAccess : GetMacroExpansionError()
    object ExpandedInfoNotFound : GetMacroExpansionError()

    override fun toString(): String = "${GetMacroExpansionError::class.simpleName}.${javaClass.simpleName}"
}

sealed class EMIGetExpansionError : GetMacroExpansionError() {
    object InvalidExpansionFile : EMIGetExpansionError()

    override fun toString(): String = "${EMIGetExpansionError::class.simpleName}.${javaClass.simpleName}"
}

sealed class ExpansionPipelineError : EMIGetExpansionError() {
    object NotYetExpanded : ExpansionPipelineError()
    object CfgDisabled : ExpansionPipelineError()
    object Skipped : ExpansionPipelineError()
    object Unresolved : ExpansionPipelineError()
    object NoProcMacroArtifact : ExpansionPipelineError()
    data class UnmatchedProcMacroKind(
        val callKind: RsProcMacroKind,
        val defKind: RsProcMacroKind,
    ) : ExpansionPipelineError()
    object Macro2IsNotSupported : ExpansionPipelineError()
    object MacroCallSyntax : ExpansionPipelineError()
    object MacroDefSyntax : ExpansionPipelineError()
    data class ExpansionError(val e: MacroExpansionError) : ExpansionPipelineError()

    override fun toString(): String = "${ExpansionPipelineError::class.simpleName}.${javaClass.simpleName}"
}

fun ResolveMacroWithoutPsiError.toExpansionPipelineError(): ExpansionPipelineError = when (this) {
    ResolveMacroWithoutPsiError.Unresolved -> ExpansionPipelineError.Unresolved
    ResolveMacroWithoutPsiError.NoProcMacroArtifact -> ExpansionPipelineError.NoProcMacroArtifact
    is ResolveMacroWithoutPsiError.UnmatchedProcMacroKind ->
        ExpansionPipelineError.UnmatchedProcMacroKind(callKind, defKind)
    ResolveMacroWithoutPsiError.Macro2IsNotSupported -> ExpansionPipelineError.Macro2IsNotSupported
}

@Throws(IOException::class)
fun DataOutput.writeExpansionPipelineError(err: ExpansionPipelineError) {
    val ordinal = when (err) {
        ExpansionPipelineError.NotYetExpanded -> 0
        ExpansionPipelineError.CfgDisabled -> 1
        ExpansionPipelineError.Skipped -> 2
        ExpansionPipelineError.Unresolved -> 3
        ExpansionPipelineError.NoProcMacroArtifact -> 4
        is ExpansionPipelineError.UnmatchedProcMacroKind -> 5
        ExpansionPipelineError.Macro2IsNotSupported -> 6
        ExpansionPipelineError.MacroCallSyntax -> 7
        ExpansionPipelineError.MacroDefSyntax -> 8
        is ExpansionPipelineError.ExpansionError -> 9
    }
    writeByte(ordinal)

    when (err) {
        is ExpansionPipelineError.UnmatchedProcMacroKind -> {
            writeEnum(err.callKind)
            writeEnum(err.defKind)
        }
        is ExpansionPipelineError.ExpansionError -> writeMacroExpansionError(err.e)
        else -> Unit
    }
}

@Throws(IOException::class)
fun DataInput.readExpansionPipelineError(): ExpansionPipelineError = when (val ordinal = readUnsignedByte()) {
    0 -> ExpansionPipelineError.NotYetExpanded
    1 -> ExpansionPipelineError.CfgDisabled
    2 -> ExpansionPipelineError.Skipped
    3 -> ExpansionPipelineError.Unresolved
    4 -> ExpansionPipelineError.NoProcMacroArtifact
    5 -> ExpansionPipelineError.UnmatchedProcMacroKind(readEnum(), readEnum())
    6 -> ExpansionPipelineError.Macro2IsNotSupported
    7 -> ExpansionPipelineError.MacroCallSyntax
    8 -> ExpansionPipelineError.MacroDefSyntax
    9 -> ExpansionPipelineError.ExpansionError(readMacroExpansionError())
    else -> throw IOException("Unknown expansion pipeline error code $ordinal")
}
