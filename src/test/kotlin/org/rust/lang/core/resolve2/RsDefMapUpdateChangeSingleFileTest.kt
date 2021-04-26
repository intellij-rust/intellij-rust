/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.MockAdditionalCfgOptions
import org.rust.UseNewResolve

/** Tests whether or not [CrateDefMap] should be updated after file modification */
@UseNewResolve
@ExpandMacros  // needed to enable precise modification tracker
class RsDefMapUpdateChangeSingleFileTest : RsDefMapUpdateTestBase() {

    fun `test edit function body`() = doTestNotChanged(type(), """
        fn foo() {/*caret*/}
    """)

    fun `test edit function name`() = doTestChanged(type(), """
        fn foo/*caret*/(x: i32) {}
    """)

    fun `test edit function arg name`() = doTestNotChanged(type(), """
        fn foo(x/*caret*/: i32) {}
    """)

    fun `test edit function arg type`() = doTestNotChanged(type(), """
        fn foo(x: /*caret*/i32) {}
    """)

    fun `test add function in empty file`() = doTestChanged("""

    """, """
        fn bar() {}
    """)

    fun `test add function to end of file`() = doTestChanged("""
        fn foo() {}
    """, """
        fn foo() {}
        fn bar() {}
    """)

    fun `test add function to beginning of file`() = doTestChanged("""
        fn foo() {}
    """, """
        fn bar() {}
        fn foo() {}
    """)

    fun `test swap functions`() = doTestNotChanged("""
        fn foo() {}
        fn bar() {}
    """, """
        fn bar() {}
        fn foo() {}
    """)

    fun `test change enum name`() = doTestChanged("""
        enum E1 {}
    """, """
        enum E2 {}
    """)

    fun `test change enum variant name`() = doTestChanged("""
        enum E { V1 }
    """, """
        enum E { V2 }
    """)

    fun `test add enum variant`() = doTestChanged("""
        enum E { V1 }
    """, """
        enum E { V1, V2 }
    """)

    fun `test swap enum variants`() = doTestNotChanged("""
        enum E { V1, V2 }
    """, """
        enum E { V2, V1 }
    """)

    fun `test enum variant add tuple fields`() = doTestNotChanged("""
        enum E { V1 }
    """, """
        enum E { V1(i32) }
    """)

    fun `test enum variant add block fields`() = doTestChanged("""
        enum E { V1 }
    """, """
        enum E { V1 { x: i32 } }
    """)

    fun `test change item visibility`() = doTestChanged("""
        fn foo() {}
    """, """
        pub fn foo() {}
    """)

    fun `test change item visibility 2`() = doTestChanged("""
        pub fn foo() {}
    """, """
        pub(crate) fn foo() {}
    """)

    fun `test change item visibility 3`() = doTestNotChanged("""
        pub(crate) fn foo() {}
    """, """
        pub(in crate) fn foo() {}
    """)

    fun `test change item visibility 4`() = doTestNotChanged("""
        fn foo() {}
    """, """
        pub(self) fn foo() {}
    """)

    fun `test add item with same name in different namespace`() = doTestChanged("""
        fn foo() {}
    """, """
        fn foo() {}
        mod foo {}
    """)

    fun `test change import 1`() = doTestChanged("""
        use aaa::bbb;
    """, """
        use aaa::ccc;
    """)

    fun `test change import 2`() = doTestChanged("""
        use aaa::{bbb, ccc};
    """, """
        use aaa::{bbb, ddd};
    """)

    fun `test swap imports`() = doTestNotChanged("""
        use aaa::bbb;
        use aaa::ccc;
    """, """
        use aaa::ccc;
        use aaa::bbb;
    """)

    fun `test swap paths in use group`() = doTestNotChanged("""
        use aaa::{bbb, ccc};
    """, """
        use aaa::{ccc, bbb};
    """)

    fun `test change import visibility`() = doTestChanged("""
        use aaa::bbb;
    """, """
        pub use aaa::bbb;
    """)

    fun `test change extern crate 1`() = doTestChanged("""
        extern crate foo;
    """, """
        extern crate bar;
    """)

    fun `test change extern crate 2`() = doTestChanged("""
        extern crate foo;
    """, """
        extern crate foo as bar;
    """)

    fun `test add macro_use to extern crate`() = doTestChanged("""
        extern crate foo;
    """, """
        #[macro_use]
        extern crate foo;
    """)

    fun `test change extern crate visibility`() = doTestChanged("""
        extern crate foo;
    """, """
        pub extern crate foo;
    """)

    fun `test change mod item to mod decl`() = doTestChanged("""
        mod foo {}
    """, """
        mod foo;
    """)

    fun `test add macro_use to mod item`() = doTestChanged("""
        mod foo {}
    """, """
        #[macro_use]
        mod foo {}
    """)

    fun `test add path attribute to mod item`() = doTestChanged("""
        mod foo {}
    """, """
        #[path = "bar.rs"]
        mod foo {}
    """)

    fun `test change path attribute of mod item`() = doTestChanged("""
        #[path = "bar1.rs"]
        mod foo {}
    """, """
        #[path = "bar2.rs"]
        mod foo {}
    """)

    fun `test add macro_use to mod decl`() = doTestChanged("""
        mod foo;
    """, """
        #[macro_use]
        mod foo;
    """)

    fun `test add path attribute to mod decl`() = doTestChanged("""
        mod foo;
    """, """
        #[path = "bar.rs"]
        mod foo;
    """)

    fun `test change path attribute of mod decl`() = doTestChanged("""
        #[path = "bar1.rs"]
        mod foo;
    """, """
        #[path = "bar2.rs"]
        mod foo;
    """)

    fun `test change macro call path`() = doTestChanged("""
        foo!();
    """, """
        bar!();
    """)

    fun `test change macro call content`() = doTestChanged("""
        foo!();
    """, """
        foo!(bar);
    """)

    fun `test change macro call content spaces`() = doTestChanged("""
        foo!();
    """, """
        foo!( );
    """)

    fun `test swap macro calls`() = doTestChanged("""
        foo1!();
        foo2!();
    """, """
        foo2!();
        foo1!();
    """)

    fun `test change macro call path inside item`() = doTestNotChanged("""
        fn func() {
            foo!();
        }
    """, """
        fn func() {
            bar!();
        }
    """)

    fun `test change macro call content inside item`() = doTestNotChanged("""
        fn func() {
            foo!();
        }
    """, """
        fn func() {
            foo!(bar);
        }
    """)

    fun `test change macro def name`() = doTestChanged("""
        macro_rules! foo {
            () => {};
        }
    """, """
        macro_rules! bar {
            () => {};
        }
    """)

    fun `test change macro def content 1`() = doTestChanged("""
        macro_rules! foo {
            () => {};
        }
    """, """
        macro_rules! foo {
            () => { fn func() {} };
        }
    """)

    fun `test add macro_export to macro def`() = doTestChanged("""
        macro_rules! foo {
            () => {};
        }
    """, """
        #[macro_export]
        macro_rules! foo {
            () => {};
        }
    """)

    fun `test add local_inner_macros to macro def`() = doTestChanged("""
        #[macro_export]
        macro_rules! foo {
            () => {};
        }
    """, """
        #[macro_export(local_inner_macros)]
        macro_rules! foo {
            () => {};
        }
    """)

    fun `test change macro 2 def name`() = doTestChanged("""
        macro foo() {}
    """, """
        macro bar() {}
    """)

    fun `test change macro 2 def visibility`() = doTestChanged("""
        macro foo() {}
    """, """
        pub macro foo() {}
    """)

    fun `test swap macro 2 defs`() = doTestNotChanged("""
        macro foo() {}
        macro bar() {}
    """, """
        macro bar() {}
        macro foo() {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test add file level cfg attribute`() = doTestChanged("""
    """, """
        #![cfg(not(intellij_rust))]
    """)

    private fun type(text: String = "a"): () -> Unit = {
        myFixture.type(text)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun replaceFileContent(after: String): () -> Unit = {
        val virtualFile = myFixture.file.virtualFile
        runWriteAction {
            VfsUtil.saveText(virtualFile, after)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun doTestChanged(action: () -> Unit, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        doTest(action, shouldChange = true)
    }

    private fun doTestNotChanged(action: () -> Unit, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        doTest(action, shouldChange = false)
    }

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        shouldChange: Boolean
    ) {
        InlineFile(before)
        doTest(replaceFileContent(after), shouldChange)
        doTest(replaceFileContent(before), shouldChange)
    }

    private fun doTestChanged(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = doTest(before, after, shouldChange = true)

    private fun doTestNotChanged(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = doTest(before, after, shouldChange = false)
}
