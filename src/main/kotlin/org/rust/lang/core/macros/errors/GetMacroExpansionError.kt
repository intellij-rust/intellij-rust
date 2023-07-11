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
        MacroExpansionIsDisabled -> RsBundle.message("macro.expansion.is.disabled.in.project.settings")
        MacroExpansionEngineIsNotReady -> RsBundle.message("macro.expansion.engine.is.not.ready")
        IncludingFileNotFound -> RsBundle.message("including.file.is.not.found")
        FileIncludedIntoMultiplePlaces -> RsBundle.message("including.file.included.in.multiple.places.intellij.rust.supports.only.inclusion.into.one.place")
        OldEngineStd -> RsBundle.message("the.old.macro.expansion.engine.can.t.expand.macros.in.rust.stdlib")
        MemExpAttrMacro -> RsBundle.message("the.old.macro.expansion.engine.can.t.expand.an.attribute.or.derive.macro")
        is MemExpParsingError -> RsBundle.message("can.t.parse.0.as.1", expansionText, context)
        CfgDisabled -> RsBundle.message("the.macro.call.is.conditionally.disabled.with.a.cfg.attribute")
        MacroCallSyntax -> RsBundle.message("there.is.an.error.in.the.macro.call.syntax")
        MacroDefSyntax -> RsBundle.message("there.is.an.error.in.the.macro.definition.syntax")
        Skipped -> RsBundle.message("expansion.of.this.procedural.macro.is.skipped.by.intellij.rust")
        Unresolved -> RsBundle.message("the.macro.is.not.resolved")
        NoProcMacroArtifact -> RsBundle.message("the.procedural.macro.is.not.compiled.successfully")
        is UnmatchedProcMacroKind -> RsBundle.message("0.proc.macro.can.t.be.called.as.1", defKind, callKind)
        is ExpansionError -> when (e) {
            BuiltinMacroExpansionError -> RsBundle.message("built.in.macro.expansion.is.not.supported")
            DeclMacroExpansionError.DefSyntax -> RsBundle.message("there.is.an.error.in.the.macro.definition.syntax")
            DeclMacroExpansionError.TooLargeExpansion -> RsBundle.message("the.macro.expansion.is.too.large")
            is DeclMacroExpansionError.Matching -> RsBundle.message("can.t.match.the.macro.call.body.against.the.macro.definition.pattern.s")
            is ProcMacroExpansionError.ServerSideError -> RsBundle.message("a.procedural.macro.error.occurred.0", e.message)
            is ProcMacroExpansionError.Timeout -> RsBundle.message("procedural.macro.expansion.timeout.exceeded.0.ms", e.timeout)
            is ProcMacroExpansionError.UnsupportedExpanderVersion -> RsBundle.message("intellij.rust.can.t.expand.procedural.macros.using.your.rust.toolchain.version.it.looks.like.the.version.is.too.recent.consider.downgrading.your.rust.toolchain.to.a.previous.version.or.try.to.update.intellij.rust.plugin.unsupported.macro.expander.version.0", e.version)
            is ProcMacroExpansionError.ProcessAborted -> RsBundle.message("the.procedural.macro.expander.process.unexpectedly.exited.with.code.0", e.exitCode)
            is ProcMacroExpansionError.IOExceptionThrown -> RsBundle.message("an.exception.thrown.during.communicating.with.proc.macro.expansion.server.see.logs.for.more.details")
            ProcMacroExpansionError.CantRunExpander -> RsBundle.message("error.occurred.during.0.process.creation.see.logs.for.more.details", RsPathManager.INTELLIJ_RUST_NATIVE_HELPER)
            ProcMacroExpansionError.ExecutableNotFound -> RsBundle.message("0.executable.is.not.found.maybe.it.s.not.provided.for.your.platform.by.intellij.rust", RsPathManager.INTELLIJ_RUST_NATIVE_HELPER)
            ProcMacroExpansionError.ProcMacroExpansionIsDisabled -> RsBundle.message("procedural.macro.expansion.is.not.enabled")
        }

        ModDataNotFound -> RsBundle.message("internal.error.can.t.find.moddata.for.containing.mod.of.the.macro.call")
        InconsistentExpansionExpandedFrom -> RsBundle.message("internal.error.macro.expansion.expandedfrom.macro.maybe.the.macro.invocation.is.inside.a.module.that.conflicts.with.another.module.name")
        ModDataNotFound -> RsBundle.message("can.t.find.moddata.for.containing.mod.of.the.macro.call")
        TooDeepExpansion -> RsBundle.message("recursion.limit.reached")
        NoMacroIndex -> RsBundle.message("can.t.find.macro.index.of.the.macro.call")
        ExpansionNameNotFound -> RsBundle.message("internal.error.expansion.name.not.found")
        ExpansionFileNotFound -> RsBundle.message("the.macro.is.not.yet.expanded")
        InconsistentExpansionCacheAndVfs -> RsBundle.message("internal.error.expansion.file.not.found.but.cache.has.valid.expansion")
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
