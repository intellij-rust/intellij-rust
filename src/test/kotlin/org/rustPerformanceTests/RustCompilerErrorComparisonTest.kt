/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.rust.MockRustcVersion
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.annotator.AnnotatorBase
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.lang.core.CompilerFeature
import org.rust.lang.core.FeatureState
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.stdext.nextOrNull
import org.rust.stdext.toPath
import org.rust.test.util.RsTestJsonPrettyPrinter
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.writeText

/**
 * This test runs our inspections against the Rust compiler's test suite and reports detected false negatives
 * and false positives. You can find a verbose test report in `build/reports` directory.
 * The test assumes `https://github.com/rust-lang/rust.git` is cloned into `testData/rust` directory.
 *
 * Also, you can use [parseJsonReport] to parse and further analyze the generated JSON report
 */
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
@MockRustcVersion("1.70.0-nightly")
class RustCompilerErrorComparisonTest : RsTestBase() {
    fun test() {
        val path = "testData/rust/tests/ui"
        val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: error("Please run `git clone https://github.com/rust-lang/rust.git testData/rust`")

        AnnotatorBase.enableAnnotator(RsErrorAnnotator::class.java, testRootDisposable)
        setUpInspections()

        val result = analyzeFilesIn(root)

        val overallStats = result.values.fold(Stats()) { a, l -> a + l.stats }

        writeMarkdownAndHtmlReport(overallStats, result)
        writeJsonReport(result)
        writeCsvReport(result)

        if (System.getenv("TEAMCITY_VERSION") != null) {
            println("##teamcity[buildStatisticValue key='org.rust.compiler.test.all' value='${overallStats.all}']")
            println("##teamcity[buildStatisticValue key='org.rust.compiler.test.full' value='${overallStats.full}']")
            println("##teamcity[buildStatisticValue key='org.rust.compiler.test.partial' value='${overallStats.partial}']")
            println("##teamcity[buildStatisticValue key='org.rust.compiler.test.false-negatives' value='${overallStats.falseNegatives}']")
            println("##teamcity[buildStatisticValue key='org.rust.compiler.test.false-positives' value='${overallStats.falsePositives}']")
            println("##teamcity[publishArtifacts '${markdownReportPath}']")
            println("##teamcity[publishArtifacts '${htmlReportPath}']")
            println("##teamcity[publishArtifacts '${jsonReportPath}']")
            println("##teamcity[publishArtifacts '${csvReportPath}']")
        }
    }

    private fun writeMarkdownAndHtmlReport(overallStats: Stats, result: Map<String, ErrorMismatches>) {
        val markdownString = generateMarkdownString(overallStats, result)
        Path.of(markdownReportPath).writeText(markdownString)

        val flavour = GFMFlavourDescriptor()
        val root = MarkdownParser(flavour).buildMarkdownTreeFromString(markdownString)
        val htmlString = HtmlGenerator(markdownString, root, flavour).generateHtml()
        PrintWriter(Path.of(htmlReportPath).bufferedWriter()).use { output ->
            output.println("<html>")
            output.println("""
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            font-family: system-ui, -apple-system, Segoe UI, Roboto, Noto Sans, Ubuntu,
                                         Cantarell, Helvetica Neue, Arial, sans-serif;
                        }
                        h1 {
                            font-size: 1.5em;
                        }
                        h2 {
                            font-size: 1.2em;
                        }
                        @media (prefers-color-scheme: dark) {
                            body {
                                color: rgb(187, 187, 187);
                                background-color: rgb(35, 39, 43);
                            }
                        }
                    </style>
                </head>
            """.trimIndent())
            output.println(htmlString)
            output.println("</html>")
        }
    }

    private fun generateMarkdownString(overallStats: Stats, result: Map<String, ErrorMismatches>): String {
        val output = StringBuilder()
        output.appendLine("# Overall stats:")
        output.appendLine("* ✅ full match - ${overallStats.full}/${overallStats.all}")
        output.appendLine("* ⚠️ partial match - ${overallStats.partial}/${overallStats.all}")
        output.appendLine("* ❌ false negative - ${overallStats.falseNegatives}/${overallStats.all}")
        output.appendLine("* ☠️ false positive - ${overallStats.falsePositives}/${overallStats.all}")
        output.appendLine()
        for ((heading, mismatches) in result) {
            val s = mismatches.stats
            output.appendLine(
                "## $heading (✅ - ${s.full}/${s.all}, ⚠️ - ${s.partial}/${s.all}, " +
                    "❌ - ${s.falseNegatives}/${s.all}, ☠️ - ${s.falsePositives}/${s.all})"
            )
            for (e in mismatches.mismatches) {
                output.appendLine("* ${e.status.emoji} ${e.file}:${e.lineNumber} - ${e.message}")
            }
            output.appendLine()
        }
        return output.toString()
    }

    private fun writeJsonReport(result: Map<String, ErrorMismatches>) {
        val jackson = ObjectMapper()
            .registerKotlinModule()
            .writer()
            .with(DefaultPrettyPrinter(RsTestJsonPrettyPrinter()))
        Path.of(jsonReportPath).bufferedWriter().use { output ->
            jackson.writeValue(output, result)
        }
    }

    /** Parses the previously generated report. Can be used for further analysis */
    @Suppress("unused")
    private fun parseJsonReport(): Map<String, ErrorMismatches> {
        return ObjectMapper().registerKotlinModule().readValue(
            jsonReportPath.toPath().toFile(),
            object: TypeReference<HashMap<String, ErrorMismatches>>() {}
        )
    }

    private fun writeCsvReport(result: Map<String, ErrorMismatches>) {
        PrintWriter(Path.of(csvReportPath).bufferedWriter()).use { output ->
            output.println("error,full,partial,false_negative,false_positive")
            for ((heading, mismatches) in result) {
                val s = mismatches.stats
                output.println(
                    "${heading.replace(',', '-')},${s.full},${s.partial},${s.falseNegatives},${s.falsePositives}"
                )
            }
        }

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

    private fun analyzeFilesIn(root: VirtualFile): Map<String, ErrorMismatches> {
        val files = VfsUtil.collectChildrenRecursively(root)
            .asSequence()
            .filter { it.name.endsWith(".rs") }
            .filter { file -> !excludedFiles.any { file.path.endsWith(it) } }
            .filter { file -> !excludedPaths.any { file.path.contains(it) } }
            .toList()

        val results = hashMapOf<ErrorHeading, MutableList<ErrorMismatch>>()

        for ((i, file) in files.withIndex()) {
            println("Analyzing ($i/${files.size}) ${file.path}")
            analyzeFile(file, results, root)
        }
        for ((_, list) in results) {
            list.sortWith(Comparator.comparing(ErrorMismatch::file).thenComparing(ErrorMismatch::lineNumber))
        }
        return results.entries
            .sortedBy { it.key }
            .mapNotNull {
                val stats = Stats.of(it.value)
                // Filter out false positives from our own errors that does not correspond to any Rustc error
                val shouldIgnore = it.key is ErrorHeading.WithoutErrorCode
                    && stats.full == 0
                    && stats.partial == 0
                    && stats.falseNegatives == 0
                if (shouldIgnore) return@mapNotNull null
                it.key.toString() to ErrorMismatches(stats, it.value)
            }
            .toMap()
    }

    private fun analyzeFile(
        file: VirtualFile,
        results: MutableMap<ErrorHeading, MutableList<ErrorMismatch>>,
        root: VirtualFile
    ) {
        val stderrFile = file.parent.findChild(file.name.replace(".rs", ".stderr")) ?: return
        val expectedErrors = parseStderrFile(VfsUtil.loadText(stderrFile))
        if (expectedErrors.isEmpty()) return

        val code = VfsUtil.loadText(file)
        var edition = Edition.EDITION_2015
        for ((cfg, line) in iterHeader(code)) {
            if (cfg != null) return
            if (line.startsWith("revision:") || line.startsWith("aux-build:") || line.startsWith("aux-crate:")) {
                return
            }
            if (line.startsWith("edition:")) {
                edition = when (line.removePrefix("edition:").trimStart()) {
                    "2015" -> Edition.EDITION_2015
                    "2018" -> Edition.EDITION_2018
                    "2021" -> Edition.EDITION_2021
                    else -> return
                }
            }
        }
        val singleFileDisposable = Disposer.newDisposable()
        project.testCargoProjects.setEdition(edition, singleFileDisposable)
        try {
            InlineFile(code)
            val psiFile = myFixture.file as RsFile
            if (psiFile.hasUnstableFeatures()) return
            if (psiFile.queryAttributes.hasAttribute("deny")) return

            val highlightInfos = try {
                myFixture.doHighlighting(HighlightSeverity.ERROR)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error analyzing ${file.path}")
                return
            }
            val actualErrors = mutableListOf<PluginErrorSpecification>()
            for (highlightInfo in highlightInfos) {
                val position = myFixture.editor.offsetToLogicalPosition(highlightInfo.startOffset)
                val message = highlightInfo.description ?: ""
                val errorCode = errorCodeRegex.find(message)?.groupValues?.getOrNull(0)
                actualErrors += PluginErrorSpecification(
                    errorCode,
                    message,
                    position.line + 1,
                    position.column + 1,
                )
            }
            FileEditorManager.getInstance(project).closeFile(file)
            runWriteAction { myFixture.getDocument(myFixture.file).setText("") }
            val filePath = file.path.removePrefix(root.path)
            collectErrorMismatches(expectedErrors, actualErrors, results, filePath)
        } finally {
            Disposer.dispose(singleFileDisposable)
        }
    }

    private fun collectErrorMismatches(
        expectedErrors: List<RustcErrorSpecification>,
        actualErrors: MutableList<PluginErrorSpecification>,
        results: MutableMap<ErrorHeading, MutableList<ErrorMismatch>>,
        filePath: String,
    ) {
        for (expectedError in expectedErrors) {
            if (!collectErrorMismatch(matchExactly = true, expectedError, actualErrors, results, filePath)) {
                collectErrorMismatch(matchExactly = false, expectedError, actualErrors, results, filePath)
            }
        }
        // All matched error has been removed from `actualErrors`, the rest errors are false positive
        for (falsePositiveError in actualErrors) {
            results.getOrPut(falsePositiveError.errorHeading()) { mutableListOf() } += ErrorMismatch(
                ErrorStatus.FalsePositive,
                filePath,
                falsePositiveError.lineNumber,
                falsePositiveError.message
            )
        }
    }

    private fun collectErrorMismatch(
        matchExactly: Boolean,
        expectedError: RustcErrorSpecification,
        actualErrors: MutableList<PluginErrorSpecification>,
        results: MutableMap<ErrorHeading, MutableList<ErrorMismatch>>,
        filePath: String
    ): Boolean {
        val lineNumber = expectedError.lineNumber
        val correspondingActualError = if (matchExactly) {
            actualErrors.find {
                it.lineNumber == lineNumber && it.errorCode == expectedError.errorCode
            } ?: return false
        } else {
            actualErrors.find {
                it.lineNumber == lineNumber
            }
        }
        actualErrors.remove(correspondingActualError)
        val status = when {
            correspondingActualError == null -> ErrorStatus.FalseNegative
            correspondingActualError.errorCode == expectedError.errorCode -> ErrorStatus.Full
            else -> ErrorStatus.Partial
        }
        results.getOrPut(expectedError.errorHeading()) { mutableListOf() } += ErrorMismatch(
            status,
            filePath,
            lineNumber,
            expectedError.message
        )
        return true
    }

    private fun RsFile.hasUnstableFeatures() = queryAttributes
        .attrsByName("feature")
        .any {
            val name = it.metaItemArgs?.metaItemList?.singleOrNull()?.name ?: return@any false
            val feature = CompilerFeature.find(name) ?: return@any false
            feature.state != FeatureState.ACCEPTED
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
            if (lineNumber != null) {
                errors += RustcErrorSpecification(errorCode, message, lineNumber, columnNumber)
            }
        }
        return errors
    }

    private data class RustcErrorSpecification(
        /** E0000 (with `E` letter) */
        val errorCode: String?,
        val message: String,
        /** 1-based */
        val lineNumber: Int,
        val columnNumber: Int?,
    ) {
        fun errorHeading(): ErrorHeading {
            return if (errorCode != null) {
                ErrorHeading.WithErrorCode(errorCode)
            } else {
                ErrorHeading.WithoutErrorCode(extractMessageHeading(message))
            }
        }
    }

    private data class PluginErrorSpecification(
        /** E0000 (with `E` letter) */
        val errorCode: String?,
        val message: String,
        /** 1-based */
        val lineNumber: Int,
        val columnNumber: Int,
    ) {
        fun errorHeading(): ErrorHeading {
            return if (errorCode != null) {
                ErrorHeading.WithErrorCode(errorCode)
            } else {
                ErrorHeading.WithoutErrorCode(message)
            }
        }
    }

    private data class ErrorMismatch(
        val status: ErrorStatus,
        val file: String,
        /** 1-based */
        val lineNumber: Int,
        val message: String
    )

    private data class ErrorMismatches(
        val stats: Stats,
        val mismatches: List<ErrorMismatch>,
    )

    private sealed interface ErrorHeading : Comparable<ErrorHeading> {
        data class WithErrorCode(val errorCode: String): ErrorHeading {
            override fun compareTo(other: ErrorHeading): Int = when (other) {
                is WithErrorCode -> errorCode.compareTo(other.errorCode)
                is WithoutErrorCode -> -1
            }

            override fun toString(): String = errorCode
        }

        data class WithoutErrorCode(val heading: String): ErrorHeading {
            override fun compareTo(other: ErrorHeading): Int = when (other) {
                is WithErrorCode -> 1
                is WithoutErrorCode -> heading.compareTo(other.heading)
            }

            override fun toString(): String = heading
        }
    }

    private enum class ErrorStatus(val emoji: String) {
        Full("✅"), Partial("⚠️"), FalseNegative("❌"), FalsePositive("☠️")
    }

    private data class Stats(
        val all: Int = 0,
        val full: Int = 0,
        val partial: Int = 0,
        val falseNegatives: Int = 0,
        val falsePositives: Int = 0,
    ) {
        operator fun plus(other: Stats): Stats = Stats(
            all + other.all,
            full + other.full,
            partial + other.partial,
            falseNegatives + other.falseNegatives,
            falsePositives + other.falsePositives,
        )

        companion object {
            fun of(errors: List<ErrorMismatch>): Stats {
                var countFull = 0
                var countPartial = 0
                var countFalseNegatives = 0
                var countFalsePositives = 0
                for (error in errors) {
                    when (error.status) {
                        ErrorStatus.Full -> countFull++
                        ErrorStatus.Partial -> countPartial++
                        ErrorStatus.FalseNegative -> countFalseNegatives++
                        ErrorStatus.FalsePositive -> countFalsePositives++
                    }
                }
                return Stats(errors.size, countFull, countPartial, countFalseNegatives, countFalsePositives)
            }
        }
    }

    companion object {
        private const val baseReportPath = "build/reports/rust-compiler-error-comparison-test-report"
        private const val markdownReportPath = "$baseReportPath.md"
        private const val htmlReportPath = "$baseReportPath.html"
        private const val jsonReportPath = "$baseReportPath.json"
        private const val csvReportPath = "$baseReportPath.csv"
        private val excludedFiles = listOf(
            "/diagnostic-flags/terminal_urls.rs",
            "/nll/user-annotations/normalization-infer.rs",
            "/consts/issue-103790.rs",
            "/macros/issue-16098.rs",
            "/macros/trace_faulty_macros.rs",
            "/traits/issue-33140.rs",
            "/traits/mutual-recursion-issue-75860.rs",
            "/imports/issue-30560.rs",
            "/const-generics/occurs-check/unify-n-nplusone.rs",
            "/associated-types/impl-wf-cycle-1.rs",
            "/associated-types/impl-wf-cycle-2.rs",
            "/traits/new-solver/exponential-trait-goals.rs",
        )
        private val excludedPaths = listOf(
            "/dep-graph/",
            "/asm/",
        )
        private val errorCodeRegex = Regex("E([0-9]{4})")
        private val variableRegex = Regex("([0-9]+)|`([^`]*)`(, `([^`]*)`)*")
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
                m.endsWith(" is not followed by an item") -> "Parser"
                m.endsWith(" is not a logical operator") -> "Parser"

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
                    val replaced = m.replace(variableRegex, "%X")
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
}

