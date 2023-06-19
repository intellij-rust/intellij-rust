/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import org.jetbrains.annotations.Nls
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
        MacroExpansionIsDisabled -> "macro expansion is disabled in project settings"
        MacroExpansionEngineIsNotReady -> "macro expansion engine is not ready"
        IncludingFileNotFound -> "including file is not found"
        FileIncludedIntoMultiplePlaces -> "including file included in multiple places; " +
            "IntelliJ-Rust supports only inclusion into one place"
        OldEngineStd -> "the old macro expansion engine can't expand macros in Rust stdlib"
        MemExpAttrMacro -> "the old macro expansion engine can't expand an attribute or derive macro"
        is MemExpParsingError -> "can't parse `$expansionText` as `$context`"
        CfgDisabled -> "the macro call is conditionally disabled with a `#[cfg()]` attribute"
        MacroCallSyntax -> "there is an error in the macro call syntax"
        MacroDefSyntax -> "there is an error in the macro definition syntax"
        Skipped -> "expansion of this procedural macro is skipped by IntelliJ-Rust"
        Unresolved -> "the macro is not resolved"
        NoProcMacroArtifact -> "the procedural macro is not compiled successfully"
        is UnmatchedProcMacroKind -> "`$defKind` proc macro can't be called as `$callKind`"
        is ExpansionError -> when (e) {
            BuiltinMacroExpansionError -> "built-in macro expansion is not supported"
            DeclMacroExpansionError.DefSyntax -> "there is an error in the macro definition syntax"
            DeclMacroExpansionError.TooLargeExpansion -> "the macro expansion is too large"
            is DeclMacroExpansionError.Matching -> "can't match the macro call body against the " +
                "macro definition pattern(s)"
            is ProcMacroExpansionError.ServerSideError -> "a procedural macro error occurred:\n${e.message}"
            is ProcMacroExpansionError.Timeout -> "procedural macro expansion timeout exceeded (${e.timeout} ms)"
            is ProcMacroExpansionError.UnsupportedExpanderVersion -> "IntelliJ Rust can't expand procedural macros using " +
                "your Rust toolchain version. It looks like the version is too recent. " +
                "Consider downgrading your Rust toolchain to a previous version or try to update IntelliJ Rust plugin. " +
                "(unsupported macro expander version ${e.version})"
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
        ModDataNotFound -> "internal error: can't find ModData for containing mod of the macro call"
        InconsistentExpansionExpandedFrom -> "internal error: `macro.expansion.expandedFrom != macro`; " +
            "maybe the macro invocation is inside a module that conflicts with another module name?"
        ModDataNotFound -> "can't find ModData for containing mod of the macro call"
        TooDeepExpansion -> "recursion limit reached"
        NoMacroIndex -> "can't find macro index of the macro call"
        ExpansionNameNotFound -> "internal error: expansion name not found"
        ExpansionFileNotFound -> "the macro is not yet expanded"
        InconsistentExpansionCacheAndVfs -> "internal error: expansion file not found, but cache has valid expansion"
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
