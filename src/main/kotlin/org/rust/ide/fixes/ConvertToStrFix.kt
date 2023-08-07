/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory

/**
 * For the given `expr` adds `as_str()`/`as_mut_str()` method call. Note the fix doesn't attempt to verify that the type
 * of `expr` is `String` and so doesn't check if adding the function call will produce a valid expression.
 */
abstract class ConvertToStrFix(expr: RsExpr, strTypeName: String, private val strMethodName: String) :
    ConvertToTyFix(expr, strTypeName, "`$strMethodName` method") {

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        element.replace(RsPsiFactory(project).createNoArgsMethodCall(element, strMethodName))
    }
}

class ConvertToImmutableStrFix(expr: RsExpr) : ConvertToStrFix(expr, "&str", "as_str")
class ConvertToMutStrFix(expr: RsExpr) : ConvertToStrFix(expr, "&mut str", "as_mut_str")
