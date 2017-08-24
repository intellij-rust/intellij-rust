/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.externalSystem.service.execution.cmd.ParametersListLexer
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.completion.addSuffix
import org.rust.lang.core.completion.withPriority

class CargoCommandCompletionProvider(
    private val workspaceGetter: () -> CargoWorkspace?
) : TextFieldCompletionProvider() {

    constructor(workspace: CargoWorkspace?) : this({ workspace })

    override fun getPrefix(currentTextPrefix: String): String = splitContextPrefix(currentTextPrefix).second

    override fun acceptChar(c: Char): CharFilter.Result? =
        if (c == '-') CharFilter.Result.ADD_TO_PREFIX else null

    override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
        val (ctx, _) = splitContextPrefix(text)
        result.addAllElements(complete(ctx))
    }

    // public for testing
    fun splitContextPrefix(text: String): Pair<String, String> {
        val lexer = ParametersListLexer(text)
        var contextEnd = 0
        while (lexer.nextToken()) {
            if (lexer.tokenEnd == text.length) {
                return text.substring(0, contextEnd) to lexer.currentToken
            }
            contextEnd = lexer.tokenEnd
        }

        return text.substring(0, contextEnd) to ""
    }

    // public for testing
    fun complete(context: String): List<LookupElement> {
        val args = ParametersListUtil.parse(context)
        if ("--" in args) return emptyList()
        if (args.isEmpty()) {
            return COMMON_COMMANDS.map { it.lookupElement }
        }

        val cmd = COMMON_COMMANDS.find { it.name == args.firstOrNull() } ?: return emptyList()

        val argCompleter = cmd.options.find { it.long == args.lastOrNull() }?.argCompleter
        if (argCompleter != null) {
            return workspaceGetter()?.let { argCompleter(it, args) }.orEmpty()
        }

        return cmd.options
            .filter { it.long !in args }
            .map { it.lookupElement }
    }
}

private class Cmd(
    val name: String,
    initOptions: OptBuilder.() -> Unit = {}
) {
    val options = OptBuilder().apply(initOptions).result
    val lookupElement: LookupElement =
        LookupElementBuilder.create(name).withInsertHandler { ctx, _ ->
            ctx.addSuffix(" ")
        }
}

private typealias ArgCompleter = (CargoWorkspace, List<String>) -> List<LookupElement>

private class Opt(
    val name: String,
    val argCompleter: ArgCompleter? = null

) {
    val long get() = "--$name"

    val lookupElement: LookupElement =
        LookupElementBuilder.create(long)
            .withInsertHandler { ctx, _ ->
                if (argCompleter != null) {
                    ctx.addSuffix(" ")
                    ctx.setLaterRunnable {
                        CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(ctx.project, ctx.editor)
                    }
                }
            }
}

private val CargoWorkspace.Target.lookupElement: LookupElement get() = LookupElementBuilder.create(name)
private val CargoWorkspace.Package.lookupElement: LookupElement
    get() {
        val priority = if (origin == PackageOrigin.WORKSPACE) 1.0 else 0.0
        return LookupElementBuilder.create(name).withPriority(priority)
    }

private class OptBuilder(
    val result: MutableList<Opt> = mutableListOf()
) {
    fun compileOptions() {
        release()
        jobs()
        features()
        triple()
        verbose()
    }

    fun release() = flag("release")
    fun jobs() = flag("jobs")
    fun features() {
        flag("features")
        flag("all-features")
        flag("no-default-features")
    }

    fun triple() = flag("triple")

    private fun targetCompleter(kind: CargoWorkspace.TargetKind): ArgCompleter = { ws, _ ->
        ws.packages.filter { it.origin == PackageOrigin.WORKSPACE }
            .flatMap { it.targets.filter { it.kind == kind } }
            .map { it.lookupElement }
    }

    fun targetBin() = opt("bin", targetCompleter(CargoWorkspace.TargetKind.BIN))

    fun targetExample() = opt("example", targetCompleter(CargoWorkspace.TargetKind.EXAMPLE))
    fun targetTest() = opt("test", targetCompleter(CargoWorkspace.TargetKind.TEST))
    fun targetBench() = opt("bench", targetCompleter(CargoWorkspace.TargetKind.BENCH))

    fun targetAll() {
        flag("lib")

        targetBin()
        flag("bins")

        targetExample()
        flag("examples")

        targetTest()
        flag("tests")

        targetBench()
        flag("bench")
    }

    fun pkg() = opt("package") { ws, _ ->
        ws.packages
            .map { it.lookupElement }
    }

    fun pkgAll() {
        pkg()
        flag("all")
        flag("exclude")
    }


    fun verbose() {
        flag("verbose")
        flag("quite")
    }

    fun flag(long: String) {
        result += Opt(long)
    }

    fun opt(long: String, argCompleter: ArgCompleter) {
        result += Opt(long, argCompleter)
    }
}

private fun options(f: OptBuilder.() -> Unit): List<Opt> = OptBuilder().apply(f).result

private val COMMON_COMMANDS = listOf(
    Cmd("run") {
        compileOptions()
        targetBin()
        targetExample()
        pkg()
    },

    Cmd("test") {
        compileOptions()
        targetAll()
        flag("doc")
        pkgAll()
        flag("no-run")
        flag("no-fail-fast")
    },

    Cmd("check") {
        compileOptions() // yeah, you can check with `--release`
        targetAll()
        pkgAll()
    },

    Cmd("build") {
        compileOptions()
        targetAll()
        pkgAll()
    },

    Cmd("update") {
        pkg()
        flag("aggressive")
        flag("precise")
    },

    Cmd("bench") {
        compileOptions()
        targetAll()
        pkgAll()
    },

    Cmd("doc") {
        compileOptions()
        pkgAll()
        targetAll()
    },

    Cmd("publish") {
        flag("host")
        flag("token")
        flag("no-verify")
        flag("allow-dirty")
        flag("jobs")
    },

    Cmd("clean") {
        compileOptions()
        pkg()
    },

    Cmd("search") {
        flag("host")
        flag("limit")
    },

    Cmd("install") {
        compileOptions()
        targetAll()
        flag("root")
        flag("force")
    }
)
