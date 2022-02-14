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

/**
 * An error type for [org.rust.lang.core.psi.ext.expansionResult]
 */
sealed class GetMacroExpansionError {
    object MacroExpansionIsDisabled : GetMacroExpansionError()
    object MacroExpansionEngineIsNotReady : GetMacroExpansionError()
    object IncludingFileNotFound : GetMacroExpansionError()
    object OldEngineStd : GetMacroExpansionError()

    object MemExpAttrMacro : GetMacroExpansionError()
    data class MemExpParsingError(
        val expansionText: CharSequence,
        val context: MacroExpansionContext
    ) : GetMacroExpansionError()

    object ModDataNotFound : GetMacroExpansionError()
    object NoMacroIndex : GetMacroExpansionError()
    object ExpansionNameNotFound : GetMacroExpansionError()
    object ExpansionFileNotFound : GetMacroExpansionError()
    object InconsistentExpansionCacheAndVfs : GetMacroExpansionError()

    // Can't expand the macro because ...
    // Failed to expand the macro because ...
    @Nls
    fun toUserViewableMessage(): String = when (this) {
        MacroExpansionIsDisabled -> "macro expansion is disabled in project settings"
        MacroExpansionEngineIsNotReady -> "macro expansion engine is not ready"
        IncludingFileNotFound -> "including file is not found"
        OldEngineStd -> "the old macro expansion engine can't expand macros in Rust stdlib"
        MemExpAttrMacro -> "the old macro expansion engine can't expand an attribute or derive macro"
        is MemExpParsingError -> "can't parse `$expansionText` as `$context`"
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
        ModDataNotFound -> "can't find ModData for containing mod of the macro call"
        NoMacroIndex -> "can't find macro index of the macro call"
        ExpansionNameNotFound -> "internal error: expansion name not found"
        ExpansionFileNotFound -> "the macro is not yet expanded"
        InconsistentExpansionCacheAndVfs -> "internal error: expansion file not found, but cache has valid expansion"
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
