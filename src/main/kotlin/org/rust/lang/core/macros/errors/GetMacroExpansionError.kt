/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.lang.core.macros.MacroExpansionContext
import org.rust.lang.core.psi.RsProcMacroKind
import org.rust.openapiext.RsPathManager

/**
 * An error type for [org.rust.lang.core.psi.ext.expansionResult]
 */
sealed class GetMacroExpansionError {
    object MacroExpansionIsDisabled : GetMacroExpansionError()
    object MacroExpansionEngineIsNotReady : GetMacroExpansionError()
    object IncludingFileNotFound : GetMacroExpansionError()
    object FileIncludedIntoMultiplePlaces : GetMacroExpansionError()
    object OldEngineStd : GetMacroExpansionError()

    object MemExpAttrMacro : GetMacroExpansionError()
    data class MemExpParsingError(
        val expansionText: CharSequence,
        val context: MacroExpansionContext
    ) : GetMacroExpansionError()

    object ModDataNotFound : GetMacroExpansionError()
    object InconsistentExpansionExpandedFrom : GetMacroExpansionError()
    object TooDeepExpansion : GetMacroExpansionError()
    object NoMacroIndex : GetMacroExpansionError()
    object ExpansionNameNotFound : GetMacroExpansionError()
    object ExpansionFileNotFound : GetMacroExpansionError()
    object InconsistentExpansionCacheAndVfs : GetMacroExpansionError()
    object VirtualFileFoundButPsiIsNull : GetMacroExpansionError()
    object VirtualFileFoundButPsiIsUnknown : GetMacroExpansionError()
    object TooLargeExpansion : GetMacroExpansionError()

    object CfgDisabled : GetMacroExpansionError()
    object Skipped : GetMacroExpansionError()
    object Unresolved : GetMacroExpansionError()
    object NoProcMacroArtifact : GetMacroExpansionError()
    data class UnmatchedProcMacroKind(
        val callKind: RsProcMacroKind,
        val defKind: RsProcMacroKind,
    ) : GetMacroExpansionError()
    object MacroCallSyntax : GetMacroExpansionError()
    object MacroDefSyntax : GetMacroExpansionError()
    data class ExpansionError(val e: MacroExpansionError) : GetMacroExpansionError()

    // Can't expand the macro because ...
    // Failed to expand the macro because ...
    @Nls
    fun toUserViewableMessage(): String = when (this) {
        MacroExpansionIsDisabled -> RsBundle.message("macro.expansion.error.MacroExpansionIsDisabled.message")
        MacroExpansionEngineIsNotReady -> RsBundle.message("macro.expansion.error.MacroExpansionEngineIsNotReady.message")
        IncludingFileNotFound -> RsBundle.message("macro.expansion.error.IncludingFileNotFound.message")
        FileIncludedIntoMultiplePlaces -> RsBundle.message("macro.expansion.error.FileIncludedIntoMultiplePlaces.message")
        OldEngineStd -> RsBundle.message("macro.expansion.error.OldEngineStd.message")
        MemExpAttrMacro -> RsBundle.message("macro.expansion.error.MemExpAttrMacro.message")
        is MemExpParsingError -> RsBundle.message("macro.expansion.error.MemExpParsingError.message", expansionText, context)
        CfgDisabled -> RsBundle.message("macro.expansion.error.CfgDisabled.message")
        MacroCallSyntax -> RsBundle.message("macro.expansion.error.MacroCallSyntax.message")
        MacroDefSyntax -> RsBundle.message("macro.expansion.error.MacroDefSyntax.message")
        Skipped -> RsBundle.message("macro.expansion.error.Skipped.message")
        Unresolved -> RsBundle.message("macro.expansion.error.Unresolved.message")
        NoProcMacroArtifact -> RsBundle.message("macro.expansion.error.NoProcMacroArtifact.message")
        is UnmatchedProcMacroKind -> RsBundle.message("macro.expansion.error.UnmatchedProcMacroKind.message", defKind, callKind)
        is ExpansionError -> when (e) {
            BuiltinMacroExpansionError -> RsBundle.message("macro.expansion.error.BuiltinMacroExpansionError.message")
            DeclMacroExpansionError.DefSyntax -> RsBundle.message("macro.expansion.error.DeclMacroExpansionError.DefSyntax.message")
            DeclMacroExpansionError.TooLargeExpansion -> RsBundle.message("macro.expansion.error.DeclMacroExpansionError.TooLargeExpansion.message")
            is DeclMacroExpansionError.Matching -> RsBundle.message("macro.expansion.error.DeclMacroExpansionError.Matching.message")
            is ProcMacroExpansionError.ServerSideError -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.ServerSideError.message", e.message)
            is ProcMacroExpansionError.Timeout -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.Timeout.message", e.timeout)
            is ProcMacroExpansionError.UnsupportedExpanderVersion -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.UnsupportedExpanderVersion.message", e.version)
            is ProcMacroExpansionError.ProcessAborted -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.ProcessAborted.message", e.exitCode)
            is ProcMacroExpansionError.IOExceptionThrown -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.IOExceptionThrown.message")
            ProcMacroExpansionError.CantRunExpander -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.CantRunExpander.message", RsPathManager.INTELLIJ_RUST_NATIVE_HELPER)
            ProcMacroExpansionError.ExecutableNotFound -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.ExecutableNotFound.message", RsPathManager.INTELLIJ_RUST_NATIVE_HELPER)
            ProcMacroExpansionError.ProcMacroExpansionIsDisabled -> RsBundle.message("macro.expansion.error.ProcMacroExpansionError.ProcMacroExpansionIsDisabled.message")
        }

        ModDataNotFound -> RsBundle.message("macro.expansion.error.ModDataNotFound.message")
        InconsistentExpansionExpandedFrom -> RsBundle.message("macro.expansion.error.InconsistentExpansionExpandedFrom.message")
        TooDeepExpansion -> RsBundle.message("macro.expansion.error.TooDeepExpansion.message")
        NoMacroIndex -> RsBundle.message("macro.expansion.error.NoMacroIndex.message")
        ExpansionNameNotFound -> RsBundle.message("macro.expansion.error.ExpansionNameNotFound.message")
        ExpansionFileNotFound -> RsBundle.message("macro.expansion.error.ExpansionFileNotFound.message")
        InconsistentExpansionCacheAndVfs -> RsBundle.message("macro.expansion.error.InconsistentExpansionCacheAndVfs.message")
        VirtualFileFoundButPsiIsNull -> RsBundle.message("macro.expansion.error.VirtualFileFoundButPsiIsNull.message")
        VirtualFileFoundButPsiIsUnknown -> RsBundle.message("macro.expansion.error.VirtualFileFoundButPsiIsUnknown.message")
        TooLargeExpansion -> RsBundle.message("macro.expansion.error.TooLargeExpansion.message")
    }

    override fun toString(): String = "${GetMacroExpansionError::class.simpleName}.${javaClass.simpleName}"
}

fun ResolveMacroWithoutPsiError.toExpansionError(): GetMacroExpansionError = when (this) {
    ResolveMacroWithoutPsiError.Unresolved -> GetMacroExpansionError.Unresolved
    ResolveMacroWithoutPsiError.NoProcMacroArtifact -> GetMacroExpansionError.NoProcMacroArtifact
    is ResolveMacroWithoutPsiError.UnmatchedProcMacroKind ->
        GetMacroExpansionError.UnmatchedProcMacroKind(callKind, defKind)
    ResolveMacroWithoutPsiError.HardcodedProcMacroAttribute -> GetMacroExpansionError.Skipped
}
