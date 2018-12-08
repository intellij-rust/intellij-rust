/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.todo

import com.intellij.editor.TodoItemsTestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsFileType

// Note, `TodoItemsTestCase` contains some general tests for C-style comments
class RsTodoTest : TodoItemsTestCase() {

    override fun supportsCStyleMultiLineComments(): Boolean = true
    override fun supportsCStyleSingleLineComments(): Boolean = true
    override fun getFileExtension(): String = RsFileType.defaultExtension

    fun `test single todo`() = doTest("""
        // [TODO first line]
        // second line
        fn main() {}
    """)

    fun `test multiline todo in block comment`() = doTest("""
        /* [TODO first line]
            [second line]*/
        fn main() {}
    """)

    fun `test multiline todo in outer eol doc comment`() = doTest("""
        /// [TODO first line]
        ///  [second line]
        fn main() {}
    """)

    fun `test multiline todo in outer block doc comment`() = doTest("""
        /** [TODO first line]
             [second line]*/
        fn main() {}
    """)

    fun `test multiline todo in inner eol doc comment`() = doTest("""
        mod foo {
            //! [TODO first line]
            //!  [second line]
        }
    """)

    fun `test multiline todo in inner block doc comment`() = doTest("""
        mod foo {
            /*! [TODO first line]
                 [second line]*/
        }
    """)

    private fun doTest(@Language("Rust") text: String) = testTodos(text)
}
