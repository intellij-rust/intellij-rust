/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import com.intellij.util.io.IOUtil
import org.rust.lang.core.macros.MACRO_LOG
import org.rust.lang.core.macros.decl.FragmentKind
import org.rust.stdext.*
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * An error type for [org.rust.lang.core.macros.MacroExpander]
 */
sealed class MacroExpansionError

sealed class DeclMacroExpansionError : MacroExpansionError() {
    data class Matching(val errors: List<MacroMatchingError>) : DeclMacroExpansionError()
    object DefSyntax : DeclMacroExpansionError()
    object TooLargeExpansion : DeclMacroExpansionError()
}

sealed class MacroMatchingError {
    abstract val offsetInCallBody: Int

    data class PatternSyntax(override val offsetInCallBody: Int) : MacroMatchingError()

    data class ExtraInput(override val offsetInCallBody: Int) : MacroMatchingError()

    data class EndOfInput(override val offsetInCallBody: Int) : MacroMatchingError()

    data class UnmatchedToken(
        override val offsetInCallBody: Int,
        val expectedTokenType: String,
        val expectedTokenText: String,
        val actualTokenType: String,
        val actualTokenText: String
    ) : MacroMatchingError() {
        override fun toString(): String = "${super.toString()}(" +
            "$expectedTokenType(`$expectedTokenText`) != $actualTokenType(`$actualTokenText`)" +
            ")"
    }

    data class FragmentIsNotParsed(
        override val offsetInCallBody: Int,
        val variableName: String,
        val kind: FragmentKind
    ) : MacroMatchingError() {
        override fun toString(): String = "${super.toString()}($variableName, $kind)"
    }

    data class EmptyGroup(override val offsetInCallBody: Int) : MacroMatchingError()

    data class TooFewGroupElements(override val offsetInCallBody: Int) : MacroMatchingError()

    data class Nesting(override val offsetInCallBody: Int, val variableName: String) : MacroMatchingError() {
        override fun toString(): String = "${super.toString()}($variableName)"
    }

    override fun toString(): String = "${MacroMatchingError::class.simpleName}.${javaClass.simpleName}"
}

sealed class ProcMacroExpansionError : MacroExpansionError() {

    /** An error occurred on the proc macro expander side. This usually means a panic from a proc-macro */
    data class ServerSideError(val message: String) : ProcMacroExpansionError() {
        override fun toString(): String = "${super.toString()}(message = \"$message\")"
    }

    /**
     * The proc macro expander process exited before answering the request.
     * This can indicate an issue with the procedural macro (a segfault or `std::process::exit()` call)
     * or an issue with the proc macro expander process, for example it was killed by a user or OOM-killed.
     */
    data class ProcessAborted(val exitCode: Int) : ProcMacroExpansionError()

    /**
     * An [IOException] thrown during communicating with the proc macro expander
     * (this includes possible OS errors and JSON serialization/deserialization errors).
     * The stacktrace is logged to [MACRO_LOG] logger.
     */
    object IOExceptionThrown : ProcMacroExpansionError()

    data class Timeout(val timeout: Long) : ProcMacroExpansionError()
    data class UnsupportedExpanderVersion(val version: Int) : ProcMacroExpansionError()
    object CantRunExpander : ProcMacroExpansionError()
    object ExecutableNotFound : ProcMacroExpansionError()
    object ProcMacroExpansionIsDisabled : ProcMacroExpansionError()

    override fun toString(): String = "${ProcMacroExpansionError::class.simpleName}.${javaClass.simpleName}"
}

object BuiltinMacroExpansionError : MacroExpansionError()

@Throws(IOException::class)
fun DataOutput.writeMacroExpansionError(err: MacroExpansionError) {
    val ordinal = when (err) {
        is DeclMacroExpansionError.Matching -> 0
        DeclMacroExpansionError.DefSyntax -> 1
        is ProcMacroExpansionError.ServerSideError -> 2
        is ProcMacroExpansionError.ProcessAborted -> 3
        is ProcMacroExpansionError.IOExceptionThrown -> 4
        is ProcMacroExpansionError.Timeout -> 5
        is ProcMacroExpansionError.UnsupportedExpanderVersion -> 6
        ProcMacroExpansionError.CantRunExpander -> 7
        ProcMacroExpansionError.ExecutableNotFound -> 8
        ProcMacroExpansionError.ProcMacroExpansionIsDisabled -> 9
        BuiltinMacroExpansionError -> 10
        DeclMacroExpansionError.TooLargeExpansion -> 11
    }
    writeByte(ordinal)

    when (err) {
        is DeclMacroExpansionError.Matching -> {
            writeVarInt(err.errors.size)
            for (error in err.errors) {
                saveMatchingError(error)
            }
        }
        is ProcMacroExpansionError.ServerSideError -> IOUtil.writeUTF(this, err.message)
        is ProcMacroExpansionError.ProcessAborted -> writeInt(err.exitCode)
        is ProcMacroExpansionError.Timeout -> writeLong(err.timeout)
        is ProcMacroExpansionError.UnsupportedExpanderVersion -> writeInt(err.version)
        else -> Unit
    }
}

@Throws(IOException::class)
fun DataInput.readMacroExpansionError(): MacroExpansionError = when (val ordinal = readUnsignedByte()) {
    0 -> {
        val size = readVarInt()
        DeclMacroExpansionError.Matching((0 until size).map { readMatchingError() })
    }
    1 -> DeclMacroExpansionError.DefSyntax
    2 -> ProcMacroExpansionError.ServerSideError(IOUtil.readUTF(this))
    3 -> ProcMacroExpansionError.ProcessAborted(readInt())
    4 -> ProcMacroExpansionError.IOExceptionThrown
    5 -> ProcMacroExpansionError.Timeout(readLong())
    6 -> ProcMacroExpansionError.UnsupportedExpanderVersion(readInt())
    7 -> ProcMacroExpansionError.CantRunExpander
    8 -> ProcMacroExpansionError.ExecutableNotFound
    9 -> ProcMacroExpansionError.ProcMacroExpansionIsDisabled
    10 -> BuiltinMacroExpansionError
    11 -> DeclMacroExpansionError.TooLargeExpansion
    else -> throw IOException("Unknown expansion error code $ordinal")
}

@Throws(IOException::class)
private fun DataOutput.saveMatchingError(value: MacroMatchingError) {
    val ordinal = when (value) {
        is MacroMatchingError.PatternSyntax -> 0
        is MacroMatchingError.ExtraInput -> 1
        is MacroMatchingError.EndOfInput -> 2
        is MacroMatchingError.UnmatchedToken -> 3
        is MacroMatchingError.FragmentIsNotParsed -> 4
        is MacroMatchingError.EmptyGroup -> 5
        is MacroMatchingError.TooFewGroupElements -> 6
        is MacroMatchingError.Nesting -> 7
    }
    writeByte(ordinal)
    writeVarInt(value.offsetInCallBody)

    when (value) {
        is MacroMatchingError.UnmatchedToken -> {
            IOUtil.writeUTF(this, value.expectedTokenType)
            IOUtil.writeUTF(this, value.expectedTokenText)
            IOUtil.writeUTF(this, value.actualTokenType)
            IOUtil.writeUTF(this, value.actualTokenText)
        }
        is MacroMatchingError.FragmentIsNotParsed -> {
            IOUtil.writeUTF(this, value.variableName)
            writeEnum(value.kind)
        }
        is MacroMatchingError.Nesting -> {
            IOUtil.writeUTF(this, value.variableName)
        }
        else -> Unit
    }
}

@Throws(IOException::class)
private fun DataInput.readMatchingError(): MacroMatchingError {
    val ordinal = readUnsignedByte()
    val offsetInCallBody = readVarInt()

    return when (ordinal) {
        0 -> MacroMatchingError.PatternSyntax(offsetInCallBody)
        1 -> MacroMatchingError.ExtraInput(offsetInCallBody)
        2 -> MacroMatchingError.EndOfInput(offsetInCallBody)
        3 -> MacroMatchingError.UnmatchedToken(
            offsetInCallBody,
            expectedTokenType = IOUtil.readUTF(this),
            expectedTokenText = IOUtil.readUTF(this),
            actualTokenType = IOUtil.readUTF(this),
            actualTokenText = IOUtil.readUTF(this)
        )
        4 -> MacroMatchingError.FragmentIsNotParsed(offsetInCallBody, IOUtil.readUTF(this), readEnum())
        5 -> MacroMatchingError.EmptyGroup(offsetInCallBody)
        6 -> MacroMatchingError.TooFewGroupElements(offsetInCallBody)
        7 -> MacroMatchingError.Nesting(offsetInCallBody, IOUtil.readUTF(this))
        else -> throw IOException("Unknown matching error code $ordinal")
    }
}

/**
 * Some proc macro errors are not cached because they are pretty unstable
 * (subsequent macro invocations may succeed)
 */
fun MacroExpansionError.canCacheError() = when (this) {
    is DeclMacroExpansionError -> true
    BuiltinMacroExpansionError -> true
    is ProcMacroExpansionError -> false
}
