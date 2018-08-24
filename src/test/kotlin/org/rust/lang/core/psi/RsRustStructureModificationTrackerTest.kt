/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsRustStructureModificationTrackerTest.TestAction.INC
import org.rust.lang.core.psi.RsRustStructureModificationTrackerTest.TestAction.NOT_INC

class RsRustStructureModificationTrackerTest : RsTestBase() {
    private enum class TestAction(val function: (Long, Long) -> Boolean, val comment: String) {
        INC({ a, b -> a > b }, "Modification counter expected to be incremented, but it remained the same"),
        NOT_INC({ a, b -> a == b }, "Modification counter expected to remain the same, but it was incremented")
    }

    private fun checkModCount(op: TestAction, action: () -> Unit) {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val modTracker = project.rustStructureModificationTracker
        val oldCount = modTracker.modificationCount
        action()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        check(op.function(modTracker.modificationCount, oldCount)) { op.comment }
    }

    private fun checkModCount(op: TestAction, @Language("Rust") code: String, text: String) {
        InlineFile(code).withCaret()
        checkModCount(op) { myFixture.type(text) }
    }

    private fun doTest(op: TestAction, @Language("Rust") code: String, text: String = "a") {
        checkModCount(op, code, text)
        checkModCount(op, """
            fn wrapped() {
                $code
            }
        """, text)
    }

    fun `test comment`() = doTest(NOT_INC, """
        // /*caret*/
    """)

    //

    fun `test fn`() = doTest(INC, """
        /*caret*/
    """, "fn foo() {}")

    fun `test fn vis`() = doTest(INC, """
        /*caret*/fn foo() {}
    """, "pub ")

    fun `test fn name`() = doTest(INC, """
        fn foo/*caret*/() {}
    """)

    fun `test fn params`() = doTest(INC, """
        fn foo(/*caret*/) {}
    """)

    fun `test fn return type 1`() = doTest(INC, """
        fn foo()/*caret*/ {}
    """, "-> u8")

    fun `test fn return type 2`() = doTest(INC, """
        fn foo() -> u/*caret*/ {}
    """, "-> 8")

    fun `test fn body`() = doTest(NOT_INC, """
        fn foo() { /*caret*/ }
    """)

    //

    fun `test struct vis`() = doTest(INC, """
        /*caret*/struct Foo;
    """, "pub ")

    fun `test struct name`() = doTest(INC, """
        struct Foo/*caret*/;
    """)

    fun `test struct body`() = doTest(INC, """
        struct Foo { /*caret*/ }
    """)

    //

    fun `test const vis`() = doTest(INC, """
        /*caret*/const FOO: u8 = 0;
    """, "pub ")

    fun `test const name`() = doTest(INC, """
        const FOO/*caret*/: u8 = 0;
    """)

    fun `test const body`() = doTest(INC, """
        const FOO: u8 = 0/*caret*/;
    """, "1")

    //

    fun `test impl type`() = doTest(INC, """
        impl Foo/*caret*/ {}
    """)

    fun `test impl for`() = doTest(INC, """
        impl Foo/*caret*/ {}
    """, " for")

    fun `test impl for trait`() = doTest(INC, """
        impl Foo for/*caret*/ {}
    """, " Bar")

    fun `test impl body`() = doTest(INC, """
        impl Foo { /*caret*/ }
    """, "fn foo() {}")

    fun `test impl fn`() = doTest(INC, """
        impl Foo { fn foo(/*caret*/) {} }
    """, "&self")

    fun `test impl fn body`() = doTest(NOT_INC, """
        impl Foo { fn foo() { /*caret*/ } }
    """)

    //

    fun `test macro`() = doTest(INC, """
        macro_rules! foo { () => { /*caret*/ } }
    """)

    fun `test macro call`() = doTest(INC, """
        foo! { /*caret*/ }
    """)

    //

    fun `test external file change`() {
        val p = fileTreeFromText("""
        //- main.rs
            mod foo;
              //^
        //- foo.rs
            // fn bar() {}
        """).createAndOpenFileWithCaretMarker()
        val file = p.psiFile("foo.rs").virtualFile!!
        checkModCount(INC) {
            runWriteAction {
                VfsUtil.saveText(file, VfsUtil.loadText(file).replace("//", ""))
            }
        }
    }

    fun `test external file removal`() {
        val p = fileTreeFromText("""
        //- main.rs
            mod foo;
              //^
        //- foo.rs
            fn bar() {}
        """).createAndOpenFileWithCaretMarker()
        val file = p.psiFile("foo.rs").virtualFile!!
        checkModCount(INC) {
            runWriteAction {
                file.delete(null)
            }
        }
    }
}
