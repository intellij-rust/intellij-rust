/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.externalSystem.service.execution.cmd.ParametersListLexer
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.completion.addSuffix

class CargoCommandCompletionProvider(
    private val workspace: CargoWorkspace?
) : TextFieldCompletionProvider() {

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
        if (args.isEmpty()) {
            return COMMON_COMMANDS.map { it.lookupElement }
        }

        val cmd = COMMON_COMMANDS.find { it.name == args.firstOrNull() } ?: return emptyList()
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
        LookupElementBuilder.create(name).withInsertHandler { ctx, _ -> ctx.addSuffix(" ") }
}

private class Opt(
    val name: String
) {
    val long get() = "--$name"

    val lookupElement: LookupElement =
        LookupElementBuilder.create(long)
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

    fun release() = opt("release")
    fun jobs() = opt("jobs")
    fun features() {
        opt("features")
        opt("all-features")
        opt("no-default-features")
    }

    fun triple() = opt("triple")


    fun targetBin() = opt("bin")
    fun targetExample() = opt("example")
    fun targetTest() = opt("test")
    fun targetBench() = opt("bench")

    fun targetAll() {
        opt("lib")

        targetBin()
        opt("bins")

        targetExample()
        opt("examples")

        targetTest()
        opt("tests")

        targetBench()
        opt("bench")
    }

    fun pkg() = opt("package")
    fun pkgAll() {
        pkg()
        opt("all")
        opt("exclude")
    }


    fun verbose() {
        opt("verbose")
        opt("quite")
    }

    fun opt(long: String) {
        result += Opt(long)
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
        opt("doc")
        pkgAll()
        opt("no-run")
        opt("no-fail-fast")
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
        opt("aggressive")
        opt("precise")
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
        opt("host")
        opt("token")
        opt("no-verify")
        opt("allow-dirty")
        opt("jobs")
    },

    Cmd("clean") {
        compileOptions()
        pkg()
    },

    Cmd("search") {
        opt("host")
        opt("limit")
    },

    Cmd("install") {
        compileOptions()
        targetAll()
        opt("root")
        opt("force")
    }
)
