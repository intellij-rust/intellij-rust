/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests.refactoring

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.tools.CargoCheckArgs
import org.rust.ide.annotator.RsExternalLinterUtils
import org.rust.ide.refactoring.inlineFunction.RsInlineFunctionProcessor
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.document
import org.rustPerformanceTests.RsRealProjectTestBase
import org.rustPerformanceTests.findDescendantRustFiles
import kotlin.random.Random

private const val ONLY_SAME_MOD: Boolean = true
private const val ONLY_FUNCTIONS: Boolean = false

/**
 * For testing [RsInlineFunctionProcessor] on real projects.
 * Workflow:
 * - Open some real-world project
 * - Choose one function among all project files
 * - Invoke refactoring to inline this function
 * - Run `cargo check` to check for possible errors after inlining
 */
class RsInlineFunctionRandomRefactoringTest : RsRealProjectTestBase() {

    // when testing on cargo, remove `#![cfg_attr(test, deny(warnings))]` in `src/cargo/lib.rs`
    fun `test Cargo`() = doTest(CARGO)
    fun `test clap`() = doTest(CLAP)
    fun `test tokio`() = doTest(TOKIO)

    private fun doTest(info: RealProjectInfo) {
        project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        val base = openRealProject(info) ?: error("Can't open project")

        val files = collectFiles(base)
        invokeCargoCheck(files.first().first)

        val seed = Random.nextInt()
        println("seed: $seed")
        val random = Random(seed)
        for (index in 0..Int.MAX_VALUE) {
            val (file, functions) = files.random(random)
            val (function, usage) = chooseRandomFunctionAndUsage(functions, random) ?: continue
            invokeRefactoring(function, usage, index)
            invokeCargoCheck(file)
        }
    }

    private fun invokeRefactoring(function: RsFunction, usage: RsReference, index: Int) {
        val call = usage.element
        if (!call.isMethodOrFunctionCall()) return

        val callText = call.formatMethodOrFunctionCall().substringBefore('\n')
        val line = call.containingFile.document!!.getLineNumber(call.startOffset) + 1
        println("#$index Inlining `$callText` in ${call.containingMod.qualifiedName}  :$line")
        val processor = RsInlineFunctionProcessor(project, function, usage, inlineThisOnly = true, removeDefinition = false)
        processor.run()
    }

    private fun collectFiles(base: VirtualFile): List<Pair<RsFile, List<RsFunction>>> =
        base
            .findDescendantRustFiles(project)
            .mapNotNull { file ->
                val functions = file
                    .descendantsOfType<RsFunction>()
                    .filter { !ONLY_FUNCTIONS || it.parent is RsMod }
                    .filter { name != null || !it.isMethod }
                if (functions.isEmpty()) return@mapNotNull null
                file to functions
            }

    private fun chooseRandomFunctionAndUsage(functions: List<RsFunction>, random: Random): Pair<RsFunction, RsReference>? {
        val function = functions.random(random)
        if (!function.canBeInlined()) return null
        val usage = function
            .searchReferences()
            .filterIsInstance<RsReference>()
            .filter { !ONLY_SAME_MOD || it.element.containingMod == function.containingMod }
            .randomOrNull(random) ?: return null
        return function to usage
    }
}

private fun invokeCargoCheck(file: RsFile) {
    val project = file.project
    val result = RsExternalLinterUtils.checkLazily(
        project.toolchain ?: return,
        project,
        project,
        file.cargoWorkspace!!.contentRoot,
        CargoCheckArgs.forTarget(project, file.containingCargoTarget!!)
    ).value!!
    for (message in result.messages) {
        when (val level = message.message.level) {
            "warning", "failure-note" -> continue
            "error" -> {
                val errorMessage = message.message.message
                if (IGNORED_ERRORS.any { it.containsMatchIn(errorMessage) }) continue
                error(errorMessage)
            }
            else -> error("unknown message type $level: `$message`")
        }
    }
}

private val IGNORED_ERRORS: List<Regex> = listOf(
    // `Ok(...)?` etc
    "type annotations needed".toRegex(),
    "cannot infer type".toRegex(),

    "aborting due to .* previous errors".toRegex(),
    "aborting due to previous error".toRegex(),

    // add needed imports
    "use of undeclared .*".toRegex(),
    "cannot find .* `.*` in this scope".toRegex(),

    "an inner attribute is not permitted in this context".toRegex(),
)

private fun RsFunction.canBeInlined(): Boolean {
    return !RsInlineFunctionProcessor.doesFunctionHaveMultipleReturns(this)
        && !RsInlineFunctionProcessor.isFunctionRecursive(this)
}

private fun PsiElement.isMethodOrFunctionCall(): Boolean =
    this is RsMethodCall
        || this is RsPath && parent is RsPathExpr && parent.parent is RsCallExpr

private fun RsElement.formatMethodOrFunctionCall(): String =
    when (this) {
        is RsMethodCall -> parent.text
        else -> parent.parent.text
    }
