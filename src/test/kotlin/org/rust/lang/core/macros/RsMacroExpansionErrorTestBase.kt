/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.expansionResult
import org.rust.lang.core.psi.ext.isMacroCall
import org.rust.stdext.RsResult

abstract class RsMacroExpansionErrorTestBase : RsTestBase() {
    protected inline fun <reified T : GetMacroExpansionError> checkError(
        @Language("Rust") code: String
    ) {
        checkError(code, T::class.java)
    }

    protected fun checkError(code: String, errorClass: Class<*>) {
        InlineFile(code)
        val markers = findElementsWithDataAndOffsetInEditor<RsPossibleMacroCall>()
        val (macro, expectedErrorMessage) = if (markers.isEmpty()) {
            myFixture.file
                .descendantsOfType<RsPossibleMacroCall>()
                .single { it.isMacroCall } to null
        } else {
            val (macro, message, _) = markers.single()
            check(macro.isMacroCall)
            macro to message
        }

        val err = when (val result = macro.expansionResult) {
            is RsResult.Err -> result.err
            is RsResult.Ok -> error("Expected a macro expansion error, got a successfully expanded macro")
        }

        check(errorClass.isInstance(err)) { "Expected error $errorClass, got $err" }

        if (expectedErrorMessage != null) {
            TestCase.assertEquals(expectedErrorMessage, err.toUserViewableMessage())
        }
    }
}
