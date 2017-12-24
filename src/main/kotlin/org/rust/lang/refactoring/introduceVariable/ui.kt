/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pass
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.psi.RsExpr
import org.rust.openapiext.isUnitTestMode


fun showIntroduceVariableTargetExpressionChooser(
    editor: Editor,
    exprs: List<RsExpr>,
    callback: (RsExpr) -> Unit
) {
    if (isUnitTestMode) {
        callback(MOCK!!.chooseTarget(exprs))
    } else {
        IntroduceTargetChooser.showChooser(editor, exprs, callback.asPass, { it.text })
    }
}

fun showOccurrencesChooser(
    editor: Editor,
    expr: RsExpr,
    occurrences: List<RsExpr>,
    callback: (List<RsExpr>) -> Unit
) {
    if (isUnitTestMode && occurrences.size > 1) {
        callback(MOCK!!.chooseOccurrences(expr, occurrences))
    } else {
        OccurrencesChooser.simpleChooser<RsExpr>(editor)
            .showChooser(
                expr,
                occurrences,
                { choice: OccurrencesChooser.ReplaceChoice ->
                    val toReplace = if (choice == OccurrencesChooser.ReplaceChoice.ALL) occurrences else listOf(expr)
                    callback(toReplace)
                }.asPass
            )
    }
}

private val <T> ((T) -> Unit).asPass: Pass<T>
    get() = object : Pass<T>() {
        override fun pass(t: T) = this@asPass(t)
    }

interface IntroduceVariableUi {
    fun chooseTarget(exprs: List<RsExpr>): RsExpr
    fun chooseOccurrences(expr: RsExpr, occurrences: List<RsExpr>): List<RsExpr>
}

private var MOCK: IntroduceVariableUi? = null
@TestOnly
fun withMockIntroduceVariableTargetExpressionChooser(mock: IntroduceVariableUi, f: () -> Unit) {
    MOCK = mock
    try {
        f()
    } finally {
        MOCK = null
    }
}

