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
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.completion.addSuffix

abstract class RsCommandCompletionProvider(
    val projects: CargoProjectsService,
    val implicitTextPrefix: String,
    val workspaceGetter: () -> CargoWorkspace?
) : TextFieldCompletionProvider() {

    protected abstract val commonCommands: List<CmdBase>

    constructor(projects: CargoProjectsService, workspaceGetter: () -> CargoWorkspace?)
        : this(projects, "", workspaceGetter)

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
        val args = ParametersListUtil.parse(implicitTextPrefix + context)
        if ("--" in args) return emptyList()
        if (args.isEmpty()) {
            return commonCommands.map { it.lookupElement }
        }

        val cmd = commonCommands.find { it.name == args.firstOrNull() } ?: return emptyList()

        val argCompleter = cmd.options.find { it.long == args.lastOrNull() }?.argCompleter
        if (argCompleter != null) {
            return argCompleter(Context(projects.allProjects, workspaceGetter(), args))
        }

        return cmd.options
            .filter { it.long !in args }
            .map { it.lookupElement }
    }
}

abstract class CmdBase(
    val name: String
) {
    abstract val options: List<Opt>
    val lookupElement: LookupElement =
        LookupElementBuilder.create(name).withInsertHandler { ctx, _ ->
            ctx.addSuffix(" ")
        }
}

data class Context(
    val projects: Collection<CargoProject>,
    val currentWorkspace: CargoWorkspace?,
    val commandLinePrefix: List<String>
)

typealias ArgCompleter = (Context) -> List<LookupElement>

class Opt(
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

interface OptBuilder {
    val result: MutableList<Opt>

    fun flag(long: String) {
        result += Opt(long)
    }

    fun opt(long: String, argCompleter: ArgCompleter) {
        result += Opt(long, argCompleter)
    }
}
