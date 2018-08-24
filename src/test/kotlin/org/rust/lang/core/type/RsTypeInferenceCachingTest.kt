/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.types.inference

class RsTypeInferenceCachingTest : RsTestBase() {
    private val TYPE: () -> Unit = { myFixture.type("a") }
    private val COMPLETE: () -> Unit = { myFixture.completeBasic() }

    private fun List<RsFunction>.collectStamps(): Map<String, Long> =
        associate { it.name!! to it.inference.getTimestamp() }


    private fun checkReinferred(action: () -> Unit, @Language("Rust") code: String, vararg names: String) {
        InlineFile(code).withCaret()
        val fns = myFixture.file.childrenOfType<RsFunction>()
        val oldStamps = fns.collectStamps()
        action()
        PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events
        val changed = fns.collectStamps().entries
            .filter { oldStamps[it.key] != it.value }
            .map { it.key }
        check(changed == names.toList()) {
            "Expected to reinfer types in ${names.asList()}, reinferred in $changed instead"
        }
    }

    fun `test reinferred only current function`() = checkReinferred(TYPE, """
        fn foo() { /*caret*/ }
        fn bar() {  }
    """, "foo")

    fun `test nothing reinferred after completion invocation`() = checkReinferred(COMPLETE, """
        fn foo() { /*caret*/ }
        fn bar() {  }
    """)

    fun `test reinferred everything on structure change 1`() = checkReinferred(TYPE, """
        struct S { /*caret*/ }
        fn foo() {  }
        fn bar() {  }
    """, "foo", "bar")

    fun `test reinferred everything on structure change 2`() = checkReinferred(TYPE, """
        fn foo() { struct S { /*caret*/ } }
        fn bar() {  }
    """, "foo", "bar")
}
