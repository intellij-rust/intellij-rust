/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.expansion
import org.rust.lang.core.psi.ext.isMacroCall

@MinRustcVersion("1.46.0")
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
@WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
class RsProcMacroSyntaxFixupTest : RsTestBase() {
    fun `test add semicolon to let stmt`() = checkSyntaxFixup("""
        fn main() {
            let a = 123
        }
    """, """
        fn main() {
            let a = 123;
        }
    """)

    fun `test add semicolon to expr stmt`() = checkSyntaxFixup("""
        fn main() {
            123
            321;
        }
    """, """
        fn main() {
            123;
            321;
        }
    """)

    fun `test fix dot expr`() = checkSyntaxFixup("""
        fn main() {
            foo.
        }
    """, """
        fn main() {
            foo.__ij__fixup
        }
    """)

    fun `test fix dot expr and add semicolon`() = checkSyntaxFixup("""
        fn main() {
            foo.
            let _ = 1;
        }
    """, """
        fn main() {
            foo.__ij__fixup;
            let _ = 1;
        }
    """)

    fun `test remove expressions with errors 1`() = checkSyntaxFixup("""
        fn main() {
            let a = 1;
            a + ;
            let c = 2;
        }
    """, """
        fn main() {
            let a = 1;
            __ij__fixup;
            let c = 2;
        }
    """)

    fun `test remove expressions with errors 2`() = checkSyntaxFixup("""
        fn main() {
            let a = 1;
            let b = a + ;
            let c = 2;
        }
    """, """
        fn main() {
            let a = 1;
            let b = __ij__fixup;
            let c = 2;
        }
    """)

    fun `test remove expressions with errors 3`() = checkSyntaxFixup("""
        fn main() {
            let a = 1;
            let b = a + (a + );
            let c = 2;
        }
    """, """
        fn main() {
            let a = 1;
            let b = a + (__ij__fixup);
            let c = 2;
        }
    """)

    fun `test no extra semicolon for if statement`() = checkSyntaxFixup("""
        fn main() {
            let a = 1;
            if true { 1; } else { 2; }
            let c = 2;
        }
    """, """
        fn main() {
            let a = 1;
            if true { 1; } else { 2; }
            let c = 2;
        }
    """)

    private fun checkSyntaxFixup(input: String, @Language("Rust") expectedOutput: String) {
        InlineFile("""
            |use test_proc_macros::attr_as_is;
            |
            |#[attr_as_is]
            |${input.trimIndent()}
        """.trimMargin())

        val macro = myFixture.file
            .descendantsOfType<RsPossibleMacroCall>()
            .single { it.isMacroCall }

        val output = macro.expansion?.file?.text

        if (!StringUtil.equalsIgnoreWhitespaces(expectedOutput.trimIndent(), output)) {
            assertEquals(expectedOutput.trimIndent(), output)
        }
    }
}
