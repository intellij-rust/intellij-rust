/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY_REFERENCE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_PASTE
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.fileTree

class RsQualifiedNameProviderTest : RsTestBase() {
    fun `test struct`() = doCopyPasteTest("""
        struct Foo/*copy*/;

        fn main() {
            /*paste*/
        }
    """, "Foo")

    fun `test struct field`() = doCopyPasteTest("""
        struct Foo {
            x/*copy*/: u32
        }

        fn main() {
            /*paste*/
        }
    """, "Foo::x")

    fun `test struct method`() = doCopyPasteTest("""
        struct Foo;
        impl Foo {
            fn foo/*copy*/(&self) {}
        }

        fn main() {
            /*paste*/
        }
    """, "Foo::foo")

    fun `test trait constant`() = doCopyPasteTest("""
        trait Foo {
            const C/*copy*/: u32 = 0;
        }

        fn main() {
            /*paste*/
        }
    """, "Foo::C")

    fun `test trait type alias`() = doCopyPasteTest("""
        trait Foo {
            type T/*copy*/;
        }

        fn main() {
            /*paste*/
        }
    """, "Foo::T")

    fun `test function`() = doCopyPasteTest("""
        fn foo/*copy*/() {}

        fn main() {
            /*paste*/
        }
    """, "foo")

    fun `test struct constant`() = doCopyPasteTest("""
        struct S;

        impl S {
            const C/*copy*/: u32 = 0;
        }

        fn main() {
            /*paste*/
        }
    """, "S::C")

    fun `test impl function`() = doCopyPasteTest("""
        trait Foo {
            fn foo(&self);
        }

        struct S;

        impl Foo for S {
            fn foo/*copy*/(&self) {}
        }

        fn main() {
            /*paste*/
        }
    """, "S::foo")

    fun `test enum variant`() = doCopyPasteTest("""
        enum Enum {
            V1/*copy*/
        }

        fn main() {
            /*paste*/
        }
    """, "Enum::V1")

    fun `test enum variant field`() = doCopyPasteTest("""
        enum Enum {
            V1 {
                a/*copy*/: u32
            }
        }

        fn main() {
            /*paste*/
        }
    """, "Enum::V1::a")

    fun `test struct inside a function`() = doCopyPasteTest("""
        fn foo() {
            struct Foo/*copy*/;
        }

        fn main() {
            /*paste*/
        }
    """, "Foo")

    fun `test paste in parent module`() = doCopyPasteTest("""
        mod foo {
            pub struct Foo/*copy*/;
        }

        fn main() {
            /*paste*/
        }
    """, "foo::Foo")

    fun `test paste in same module`() = doCopyPasteTest("""
        mod foo {
            pub struct Foo/*copy*/;

            fn foo() {
                /*paste*/
            }
        }
    """, "Foo")

    fun `test paste in child module`() = doCopyPasteTest("""
        pub struct Foo/*copy*/;

        mod foo {
            fn foo() {
                /*paste*/
            }
        }
    """, "crate::Foo")

    fun `test paste in parallel module`() = doCopyPasteTest("""
        mod bar {
            pub struct Foo/*copy*/;
        }

        mod foo {
            fn foo() {
                /*paste*/
            }
        }
    """, "crate::bar::Foo")

    fun `test copy directory`() = doDirectoryCopyPasteTest(fileTree {
        dir("a") {
            rust("mod.rs", """
                pub mod b;
            """)
            dir("b") {
                rust("mod.rs", "")
            }
        }
        rust("main.rs", """
            mod a;
            fn main() {
                /*caret*/
            }
        """)
    }, "a/b", "a::b")

    private fun doCopyPasteTest(@Language("Rust") code: String, expected: String) {
        InlineFile(code.replace("/*copy*/", "/*caret*/"))

        myFixture.performEditorAction(ACTION_COPY_REFERENCE)
        InlineFile(code.replace("/*paste*/", "/*caret*/"))
        myFixture.performEditorAction(ACTION_PASTE)
        myFixture.checkResult(code.replace("/*paste*/", expected))
    }

    private fun doDirectoryCopyPasteTest(tree: FileTree, directory: String, expected: String) {
        val project = tree.create()
        val psiDirectory = project.psiFile(directory)
        val file = project.psiFile(project.fileWithCaret)
        val text = file.text
        assert(CopyReferenceAction.doCopy(psiDirectory, myFixture.project))

        myFixture.configureFromTempProjectFile(project.fileWithCaret)
        myFixture.performEditorAction(ACTION_PASTE)
        myFixture.checkResult(text.replace("<caret>", expected))
    }
}
