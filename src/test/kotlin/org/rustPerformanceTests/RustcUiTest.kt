/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.rust.*
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.annotator.AnnotatorBase
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.lang.core.CompilerFeature
import org.rust.lang.core.FeatureState
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.stdext.nextOrNull
import java.nio.file.Path
import kotlin.io.path.writeText

@ExpandMacros(MacroExpansionScope.ALL, "std")
@WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RustcUiTest : RsTestBase() {
    fun test() {
        val path = "testData/rust/tests/ui"
        val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)!!

        AnnotatorBase.enableAnnotator(RsErrorAnnotator::class.java, testRootDisposable)
        setUpInspections()

        val files = VfsUtil.collectChildrenRecursively(root)
            .filter { it.name.endsWith(".rs") }
            .filter { !it.path.endsWith("/nll/user-annotations/normalization-infer.rs") }
            .filter { !it.path.endsWith("/consts/issue-103790.rs") }
            .filter { !it.path.endsWith("/macros/issue-16098.rs") }
            .filter { !it.path.endsWith("/macros/trace_faulty_macros.rs") }
            .filter { !it.path.endsWith("/traits/issue-33140.rs") }
            .filter { !it.path.endsWith("/traits/mutual-recursion-issue-75860.rs") }
            .filter { !it.path.endsWith("/imports/issue-30560.rs") }
            .filter { !it.path.endsWith("/const-generics/occurs-check/unify-n-nplusone.rs") }
            .filter { !it.path.endsWith("/associated-types/impl-wf-cycle-1.rs") }
            .filter { !it.path.endsWith("/associated-types/impl-wf-cycle-2.rs") }
            .filter { !it.path.contains("/dep-graph/") }
            .filter { !it.path.contains("/asm/") }
//            .filter { it.path.contains("/error-codes/") }

        val resultsWithErrorCode = hashMapOf<String, MutableList<ComparedError>>()
        val resultsWithoutErrorCode = linkedMapOf<String, MutableList<ComparedError>>()

        testLoop@for (file in files) {
            println(file.path)
            val stderrFile = file.parent.findChild(file.name.replace(".rs", ".stderr")) ?: continue
            val expectedErrors = parseStderrFile(VfsUtil.loadText(stderrFile))
            if (expectedErrors.isEmpty()) continue
            val actualErrors = mutableListOf<PluginErrorSpecification>()

            val code = VfsUtil.loadText(file)
            var edition = Edition.EDITION_2015
            for ((cfg, line) in iterHeader(code)) {
                if (cfg != null) continue@testLoop
                if (line.startsWith("revision:") || line.startsWith("aux-build:") || line.startsWith("aux-crate:")) {
                    continue@testLoop
                }
                if (line.startsWith("edition:")) {
                    edition = when (line.removePrefix("edition:").trimStart()) {
                        "2015" -> Edition.EDITION_2015
                        "2018" -> Edition.EDITION_2018
                        "2021" -> Edition.EDITION_2021
                        else -> continue@testLoop
                    }
                }
            }
            val singleFileDisposable = Disposer.newDisposable()
            project.testCargoProjects.setEdition(edition, singleFileDisposable)
            try {
                InlineFile(code)
                val psiFile = myFixture.file as RsFile
                val hasUnstableFeatures = psiFile.queryAttributes
                    .attrsByName("feature")
                    .any {
                        val name = it.metaItemArgs?.metaItemList?.singleOrNull()?.name ?: return@any false
                        val feature = CompilerFeature.find(name) ?: return@any false
                        feature.state != FeatureState.ACCEPTED
                    }
                if (hasUnstableFeatures) continue@testLoop
                if (psiFile.queryAttributes.hasAttribute("deny")) continue@testLoop

                for (highlightInfo in myFixture.doHighlighting(HighlightSeverity.ERROR)) {
                    val position = myFixture.editor.offsetToLogicalPosition(highlightInfo.startOffset)
                    actualErrors += PluginErrorSpecification(
                        highlightInfo.description ?: "",
                        position.line + 1,
                        position.column + 1,
                    )
                }
                FileEditorManager.getInstance(project).closeFile(file)
                runWriteAction { myFixture.getDocument(myFixture.file).setText("") }
                for (e in expectedErrors) {
                    val lineNumber = e.lineNumber ?: continue
                    val correspondingActualError = actualErrors.find {
                        it.lineNumber == lineNumber
                    }
                    val status = when {
                        correspondingActualError == null -> ErrorStatus.MISSED
                        e.errorCode == null -> ErrorStatus.FULL
                        correspondingActualError.message.contains(e.errorCode) -> ErrorStatus.FULL
                        else -> ErrorStatus.PARTIAL
                    }
                    val result = if (e.errorCode != null) {
                        resultsWithErrorCode.getOrPut(e.errorCode) { mutableListOf() }
                    } else {
                        resultsWithoutErrorCode.getOrPut(extractMessageHeading(e.message)) { mutableListOf() }
                    }
                    result += ComparedError(
                        file.path.removePrefix(root.path),
                        e.errorCode,
                        e.message,
                        lineNumber,
                        status
                    )
                }
            } finally {
                Disposer.dispose(singleFileDisposable)
            }
        }

        val output = StringBuilder()
        val result = resultsWithErrorCode.entries.sortedBy { it.key } +
            resultsWithoutErrorCode.entries
        for ((heading, list) in result) {
            val count = list.size
            val countFull = list.count { it.status == ErrorStatus.FULL }
            val countPartial = list.count { it.status == ErrorStatus.PARTIAL }
            val countMissed = list.count { it.status == ErrorStatus.MISSED }
            output.appendLine("## $heading (✅ - $countFull/$count, ⚠️ - $countPartial/$count, ❌ - $countMissed/$count)")
            list.sortWith(Comparator.comparing(ComparedError::file).thenComparing(ComparedError::lineNumber))
            for (e in list) {
                output.appendLine("* ${e.status.emoji} ${e.file}:${e.lineNumber} - ${e.message}")
            }
            output.appendLine()
        }
        Path.of("build/reports/rustc-ui-tests.md").writeText(output)
    }

    private fun iterHeader(text: String): Sequence<Pair<String?, String>> {
        return text.lineSequence()
            .map { it.trim() }
            .takeWhile { !it.startsWith("fn") && !it.startsWith("mod") }
            .mapNotNull { line ->
                if (line.startsWith("//")) {
                    val content = line.substring(2).trimStart()
                    val closeBracket = content.indexOf("]")

                    if (closeBracket == -1) {
                        null to content
                    } else {
                        content.substring(1, closeBracket) to content.substring(closeBracket + 1).trimStart()
                    }
                } else {
                    null
                }
            }
    }

    private fun parseStderrFile(text: String): List<RustcErrorSpecification> {
        val errors = mutableListOf<RustcErrorSpecification>()
        val iter = text.lineSequence().iterator()
        while (iter.hasNext()) {
            val line = iter.next()
            var errorCode: String? = null
            val message = (if (line.startsWith("error[")) {
                errorCode = line.substring("error[".length, line.indexOf("]"))
                line.substring(line.indexOf(":") + 1)
            } else if (line.startsWith("error:")) {
                if (line.endsWith("(error E0445)")) {
                    errorCode = "E0445"
                }
                if (line.endsWith("(error E0446)")) {
                    errorCode = "E0446"
                }
                line.removePrefix("error:")
            } else {
                continue
            }).trim()
            val locLine = iter.nextOrNull()?.trim() ?: continue
            if (!locLine.startsWith("-->")) continue
            val (lineNumber, columnNumber) = locLine.split(":").let {
                if (it.size < 2) return@let null
                it[it.lastIndex - 1].toIntOrNull() to it.last().toIntOrNull()
            } ?: continue
            errors += RustcErrorSpecification(errorCode, message, lineNumber, columnNumber)
        }
        return errors
    }

    data class RustcErrorSpecification(
        val errorCode: String?,
        val message: String,
        // 1-based
        val lineNumber: Int?,
        val columnNumber: Int?,
    )

    data class PluginErrorSpecification(
        val message: String,
        // 1-based
        val lineNumber: Int,
        val columnNumber: Int,
    )

    data class ComparedError(
        val file: String,
        val errorCode: String?,
        val message: String,
        // 1-based
        val lineNumber: Int,
        val status: ErrorStatus
    )

    enum class ErrorStatus(val emoji: String) {
        FULL("✅"), PARTIAL("⚠️"), MISSED("❌")
    }

    private fun setUpInspections() {
        val inspections = InspectionToolRegistrar.getInstance().createTools()
            .map { it.tool }
            .filterIsInstance<RsLocalInspectionTool>()

        for (inspection in inspections) {
            setUpInspection(inspection)
        }

        myFixture.enableInspections(*inspections.toTypedArray())
    }

    private fun setUpInspection(inspection: RsLocalInspectionTool) {
        when (inspection) {
            is RsUnresolvedReferenceInspection -> inspection.ignoreWithoutQuickFix = false
        }
    }

    private val regex = Regex("([0-9]+)|`([^`]*)`(, `([^`]*)`)*")
    private val parserMessages = setOf(
        "identifier contains uncommon Unicode codepoints",
        "this file contains an unclosed delimiter",
        "return types are denoted using `->`",
        "comparison operators cannot be chained",
        "denote infinite loops with `loop { ... }`",
        "struct fields are separated by `,`",
        "Ferris cannot be used as an identifier",
        "macro names aren't followed by a `!`",
        "structs are not allowed in struct definitions",
        "parenthesized lifetime bounds are not supported",
        "`mut` must be followed by a named binding",
        "unmatched angle bracket",
        "generic parameters without surrounding angle brackets",
        "suffixes on byte string literals are invalid",
        "overlong unicode escape",
        "at least one trait must be specified",
        "missing type to the right of `=`",
        "missing angle brackets in associated item path",
        "missing `fn` for method definition",
        "`~` cannot be used as a unary operator",
        "this is a block expression, not an array",
        "closure bodies that contain statements must be surrounded by braces",
        "expected string literal",
        "free function without a body",
        "missing `in` in `for` loop",
        "unexpected keyword `Self` in generic parameters",
        "block label not supported here",
        "malformed loop label",
        "unexpected parentheses surrounding `for` loop head",
        "missing fragment specifier",
        "unexpected token `||` in pattern",
        "unexpected `||` before function parameter",
        "expected item after attributes",
        "expected statement after outer attribute",
        "an inner attribute is not permitted in this context",
        "an inner attribute is not permitted following an outer attribute",
        "attribute without generic parameters",
        "character constant must be escaped",
        "missing condition for `if` expression",
        "struct literal body without path",
        "non-item in item list",
        "encountered diff marker",
        "expected item after doc comment",
        "suffixes on a tuple index are invalid",
        "Rust has no postfix increment operator",
        "Rust has no prefix increment operator",
        "an inner attribute is not permitted following an outer doc comment",
        "pattern on wrong side of `@`",
        "left-hand side of `@` must be a binding",
        "unexpected `if` in the condition expression",
        "you might have meant to write `impl` instead of `fn`",
        "invalid `struct` delimiters or `fn` call arguments",
        "unmatched angle brackets",
        "missing trait in a trait impl",
        "missing `fn` or `struct` for function or struct definition",
        "missing `fn` for function definition",
        "lifetime must precede `mut`",
        "invalid `?` in type",
        "expected pattern, found keyword `in`",
        "labeled expression must be followed by `:`",
        "parentheses are required around this expression to avoid confusion with a labeled break expression",
        "`match` arm body without braces",
        "invalid struct literal",
        "missing expression to iterate on in `for` loop",
        "`mut` must be attached to each individual binding",
        "`mut` on a binding may not be repeated",
        "lifetimes cannot start with a number",
        "found single colon before projection in qualified path",
        "too many `#` when terminating raw string",
        "union fields are separated by `,`",
        "`mut` must precede `dyn`",
        "default values on `struct` fields aren't supported",
        "invalid `dyn` keyword",
        "field expressions cannot have generic arguments",
        "function body cannot be `= expression;`",
        "cannot define duplicate `where` clauses on an item",
    )
    private val literalMessages = setOf(
        "non-ASCII character in byte string literal",
        "expected at least one digit in exponent",
        "suffixes on string literals are invalid",
        "incorrect unicode escape sequence",
        "character literal may only contain one codepoint",
        "binary float literal is not supported",
        "integer literal is too large",
        "numeric character escape is too short",
        "empty character literal",
        "empty unicode escape",
        "octal float literal is not supported",
        "hexadecimal float literal is not supported",
        "invalid base prefix for number literal",
        "out of range hex escape",
        "suffixes on char literals are invalid",
        "suffixes on byte literals are invalid",
        "non-ASCII character in byte literal",
        "non-ASCII character in raw byte string literal",
        "unicode escape in byte string",
        "invalid start of unicode escape: `_`",
        "float literals must have an integer part",
        "invalid trailing slash in literal",
        "invalid unicode character escape",
        "unterminated unicode escape",
        "underscore literal suffix is not allowed",
    )

    private fun extractMessageHeading(m: String): String {
        return when {
            m.startsWith("[") && m.endsWith("]") -> "no message"
            m in parserMessages -> "Parser"

            m == "allocators must be statics" -> "Allocator"
            m == "cannot define multiple global allocators" -> "Allocator"

            m in literalMessages -> "Literal"

            m == "repetition matches empty token tree" -> "Declarative macro"
            m == "expected a statement" -> "Declarative macro"
            m == "this must repeat at least once" -> "Declarative macro"
            m == "meta-variable repeats with different Kleene operator" -> "Declarative macro"

            m.startsWith("unexpected token:") -> "Parser"
            m.startsWith("unknown start of token:") -> "Parser"
            m.startsWith("expected identifier, found ") -> "Parser"
            m.startsWith("expected expression, found ") -> "Parser"
            m.startsWith("expected one of") -> "Parser"
            m.startsWith("found invalid character;") -> "Parser"
            m.startsWith("expected type, found ") -> "Parser"
            m.startsWith("identifiers cannot contain emoji:") -> "Parser"
            m.startsWith("expected associated constant bound, found ") -> "Parser"
            m.startsWith("expected item, found ") -> "Parser"
            m.startsWith("bare CR not allowed in ") -> "Parser"
            m.startsWith("expected pattern, found ") -> "Parser"

            m.startsWith("invalid format string:") -> "format string"
            m.startsWith("duplicate argument named ") -> "format string"
            m.startsWith("invalid reference to positional arguments ") -> "format string"
            m.startsWith("unknown format trait ") -> "format string"
            m.contains("positional arguments in format string") -> "format string"
            m.contains("positional argument in format string") -> "format string"
            m.contains("invalid reference to positional argument") -> "format string"
            m == "multiple unused formatting arguments" -> "format string"
            m == "positional arguments cannot follow named arguments" -> "format string"
            m == "format argument must be a string literal" -> "format string"

            m.startsWith("unreachable ") -> "unreachable"
            m.startsWith("use of deprecated ") -> "use of deprecated"
            m.contains("that will be deprecated in") -> "use of deprecated"
            m.startsWith("evaluate(") -> "evaluate()"
            m.startsWith("unnecessary ") -> "unnecessary"
            m.startsWith("braces around ") && m.endsWith("is unnecessary") -> "unnecessary"
            m.startsWith("missing documentation for ") -> "missing documentation"
            m.startsWith("no rules expected the token") -> "Declarative macro"
            m.startsWith("unexpected end of macro invocation") -> "Declarative macro"
            m.startsWith("macro expansion ignores token") -> "Declarative macro"
            m.startsWith("invalid macro matcher;") -> "Declarative macro"
            m.startsWith("invalid reference to positional argument ") -> "invalid reference to positional argument"
            m.startsWith("function pointer calls are not allowed in ") -> "function pointer calls are not allowed in"
            m.startsWith("`..` can only be used once per") -> "`..` can only be used once"
            m.endsWith(" is not followed by an item") -> "Parser"
            m.endsWith(" is not a logical operator") -> "Parser"
            m.endsWith(" is private") -> "%X is private"
            m.endsWith(" without body") -> "%X without body"
            m.endsWith(" cannot be `default`") -> "`default`"
            m == "a static item cannot be `default`" -> "`default`"
            m.endsWith(" never used") -> "never used"
            m.endsWith(" is not supported in `extern` blocks") -> "is not supported in `extern` blocks"
            m.endsWith(" that must be used") -> "must use"
            m.endsWith(" never read") -> "never read"
            m.endsWith(" is still repeating at this depth") -> "Declarative macro"
            m.endsWith(" never constructed") -> "%X is never constructed"
            m.endsWith(" is not supported in `trait`s or `impl`s") -> "%X is not supported in `trait`s or `impl`s"
            m.endsWith(" cannot be declared unsafe") -> "cannot be declared unsafe"
            m.contains("should have") && m.endsWith(" name") -> "Naming"
            m.startsWith("cannot find ") && m.endsWith(" in this scope") -> "Cannot find %X in this scope"
            m.contains(" is not allowed in ") -> "%X is not allowed in %Y"
            else -> {
                val replaced = m.replace(regex, "%X")
                when {
                    replaced == "leading %X is not supported"
                        || replaced == "unexpected %X"
                        || replaced == "expected parameter name, found %X"
                        || replaced == "unexpected %X in pattern"
                        || replaced == "found associated const %X when type was expected"
                        || replaced == "missing type for %X item"
                        || replaced == "unexpected %X after identifier"
                        || replaced == "unexpected keyword %X after identifier"
                        || replaced == "%X is interpreted as a start of generic arguments for %X, not a comparison"
                        || replaced == "%X is interpreted as a start of generic arguments for %X, not a shift"
                        || replaced == "mismatched closing delimiter: %X"
                        || replaced == "invalid comparison operator %X"
                        || replaced == "unexpected closing delimiter: %X"
                        || replaced == "keyword %X is written in a wrong case"
                        || replaced.startsWith("expected %X")
                    -> "Parser"
                    replaced == "expected unsuffixed literal or identifier, found %X"
                        || replaced == "invalid width %X for float literal"
                        || replaced == "invalid digit for a base %X literal"
                        || replaced == "unknown character escape: %X"
                        || replaced == "values of the type %X are too big for the current architecture"
                        || replaced == "invalid suffix %X for number literal"
                        || replaced == "invalid suffix %X for float literal"
                        || replaced == "unknown byte escape: %X"
                        || replaced == "invalid character in numeric character escape: %X"
                        || replaced == "byte constant must be escaped: %X"
                    -> "Literal"
                    replaced.endsWith("character isn't allowed in %X") -> "character isn't allowed in %X"
                    else -> replaced
                }
            }
        }
    }
}

