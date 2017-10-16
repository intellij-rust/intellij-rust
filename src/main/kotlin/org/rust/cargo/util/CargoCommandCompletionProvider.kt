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
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.completion.addSuffix
import org.rust.lang.core.completion.withPriority

class CargoCommandCompletionProvider(
    private val projects: CargoProjectsService,
    private val workspaceGetter: () -> CargoWorkspace?
) : TextFieldCompletionProvider() {

    constructor(projects: CargoProjectsService, workspace: CargoWorkspace?) : this(projects, { workspace })

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
            return argCompleter(Context(projects.allProjects, workspaceGetter(), args))
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

private data class Context(
    val projects: Collection<CargoProject>,
    val currentWorkspace: CargoWorkspace?,
    val commandLinePrefix: List<String>
)

private typealias ArgCompleter = (Context) -> List<LookupElement>

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

private val CargoProject.lookupElement: LookupElement
    get() =
        LookupElementBuilder
            .create(manifest.toString())
            .withIcon(CargoIcons.ICON)

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

    private fun targetCompleter(kind: CargoWorkspace.TargetKind): ArgCompleter = { ctx ->
        ctx.currentWorkspace?.packages.orEmpty()
            .filter { it.origin == PackageOrigin.WORKSPACE }
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

    fun pkg() = opt("package") { ctx ->
        ctx.currentWorkspace?.packages.orEmpty().map { it.lookupElement }
    }

    fun pkgAll() {
        pkg()
        flag("all")
        flag("exclude")
    }

    fun manifestPath() {
        opt("manifest-path") { ctx ->
            ctx.projects.map { it.lookupElement }
        }
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
        manifestPath()
    },

    Cmd("test") {
        compileOptions()
        targetAll()
        flag("doc")
        pkgAll()
        manifestPath()
        flag("no-run")
        flag("no-fail-fast")
    },

    Cmd("check") {
        compileOptions() // yeah, you can check with `--release`
        targetAll()
        pkgAll()
        manifestPath()
    },

    Cmd("build") {
        compileOptions()
        targetAll()
        pkgAll()
        manifestPath()
    },

    Cmd("update") {
        pkg()
        manifestPath()
        flag("aggressive")
        flag("precise")
    },

    Cmd("bench") {
        compileOptions()
        targetAll()
        pkgAll()
        manifestPath()
    },

    Cmd("doc") {
        compileOptions()
        pkgAll()
        manifestPath()
        targetAll()
    },

    Cmd("publish") {
        flag("index")
        flag("token")
        flag("no-verify")
        flag("allow-dirty")
        flag("jobs")
        flag("dry-run")
        manifestPath()
    },

    Cmd("clean") {
        compileOptions()
        pkg()
        manifestPath()
    },

    Cmd("search") {
        flag("index")
        flag("limit")
    },

    Cmd("install") {
        compileOptions()
        targetAll()
        flag("root")
        flag("force")
    }
)

// Copy of com.intellij.openapi.externalSystem.service.execution.cmd.ParametersListLexer,
// which is not present in all IDEs.
class ParametersListLexer(private val myText: String) {
    private var myTokenStart = -1
    private var index = 0

    val tokenEnd: Int
        get() {
            assert(myTokenStart >= 0)
            return index
        }

    val currentToken: String
        get() = myText.substring(myTokenStart, index)

    fun nextToken(): Boolean {
        var i = index

        while (i < myText.length && Character.isWhitespace(myText[i])) {
            i++
        }

        if (i == myText.length) return false

        myTokenStart = i
        var isInQuote = false

        do {
            val a = myText[i]
            if (!isInQuote && Character.isWhitespace(a)) break
            when {
                a == '\\' && i + 1 < myText.length && myText[i + 1] == '"' -> i += 2
                a == '"' -> {
                    i++
                    isInQuote = !isInQuote
                }
                else -> i++
            }
        } while (i < myText.length)

        index = i
        return true
    }
}
