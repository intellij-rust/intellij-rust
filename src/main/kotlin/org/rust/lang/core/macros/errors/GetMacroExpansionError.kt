/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import org.jetbrains.annotations.Nls
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionContext
import org.rust.lang.core.psi.RsProcMacroKind
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.isFeatureEnabled
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

    // Can't expand the macro because ...
    // Failed to expand the macro because ...
    @Nls
    fun toUserViewableMessage(): String = when(this) {
        MacroExpansionIsDisabled -> "macro expansion is disabled in project settings"
        MacroExpansionEngineIsNotReady -> "macro expansion engine is not ready"
        IncludingFileNotFound -> "including file is not found"
        OldEngineStd -> "the old macro expansion engine can't expand macros in Rust stdlib"
        MemExpDuringMacroExpansion -> "internal error: in-memory macro expansion is requested during other " +
            "macro expansion"
        MemExpAttrMacro -> "internal error: can't do in-memory expansion of an attribute or derive macro"
        is MemExpParsingError -> "can't parse `$expansionText` as `$context`"
        NextStepMacroAccess -> "internal error: the expansion from a next expansion step is accessed during " +
            "a previous expansion step"
        ExpandedInfoNotFound -> "the macro is not yet expanded (1)"
        EMIGetExpansionError.InvalidExpansionFile -> "internal error: the expansion file has been invalidated"
        ExpansionPipelineError.CfgDisabled -> "the macro call is conditionally disabled with a `#[cfg()]` attribute"
        ExpansionPipelineError.MacroCallSyntax -> "there is an error in the macro call syntax"
        ExpansionPipelineError.MacroDefSyntax -> "there is an error in the macro definition syntax"
        ExpansionPipelineError.NotYetExpanded -> "the macro is not yet expanded (2)"
        ExpansionPipelineError.Skipped -> "expansion of this procedural macro is skipped by IntelliJ-Rust"
        ExpansionPipelineError.Unresolved -> "the macro is not resolved"
        ExpansionPipelineError.NoProcMacroArtifact -> if (!isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)) {
            "the procedural macro is not compiled because experimental feature " +
                "`${RsExperiments.EVALUATE_BUILD_SCRIPTS}` is not enabled"
        } else {
            "the procedural macro is not compiled successfully"
        }
        is ExpansionPipelineError.UnmatchedProcMacroKind -> "`$defKind` proc macro can't be called as `$callKind`"
        is ExpansionPipelineError.Macro2IsNotSupported -> "macros 2.0 are not supported by IntelliJ-Rust"
        is ExpansionPipelineError.ExpansionError -> when (e) {
            BuiltinMacroExpansionError -> "built-in macro expansion is not supported"
            DeclMacroExpansionError.DefSyntax -> "there is an error in the macro definition syntax"
            is DeclMacroExpansionError.Matching -> "can't match the macro call body against the " +
                "macro definition pattern(s)"
            is ProcMacroExpansionError.ServerSideError -> "a procedural macro error occurred:\n${e.message}"
            is ProcMacroExpansionError.Timeout -> "procedural macro expansion timeout exceeded (${e.timeout} ms)"
            is ProcMacroExpansionError.ProcessAborted -> "the procedural macro expander process unexpectedly exited " +
                "with code ${e.exitCode}"
            is ProcMacroExpansionError.IOExceptionThrown -> "an exception thrown during communicating with proc " +
                "macro expansion server; see logs for more details"
            ProcMacroExpansionError.CantRunExpander -> "error occurred during `${RsPathManager.INTELLIJ_RUST_NATIVE_HELPER}` " +
                "process creation; see logs for more details"
            ProcMacroExpansionError.ExecutableNotFound -> "`${RsPathManager.INTELLIJ_RUST_NATIVE_HELPER}` executable is not found; " +
                "(maybe it's not provided for your platform by IntelliJ-Rust)"
            ProcMacroExpansionError.ProcMacroExpansionIsDisabled -> "procedural macro expansion is not enabled"
        }
    }

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
    ResolveMacroWithoutPsiError.HardcodedProcMacroAttribute -> ExpansionPipelineError.Skipped
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
