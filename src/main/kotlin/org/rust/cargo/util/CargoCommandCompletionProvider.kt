/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.utils.buildList

class CargoCommandCompletionProvider(
    private val workspace: CargoWorkspace?
) : TextFieldCompletionProvider() {
    override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
        result.addAllElements(complete(text))
    }


    // public for testing
    fun complete(text: String): List<LookupElement> {
        val args = ParametersListUtil.parse(text)
        return buildList {
            if (args.size <= 1) {
                addAll(COMMON_COMMANDS.map { it.lookupElement })
            }
        }
    }
}

private val String.lookupElement: LookupElement get() = LookupElementBuilder.create(this)


private val COMMON_COMMANDS = listOf(
    "build",
    "check",
    "clean",
    "doc",
//    "new",
//    "init",
    "run",
    "test",
    "bench",
    "update",
    "search",
    "publish",
    "install"
)
