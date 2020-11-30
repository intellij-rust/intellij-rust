/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiFileSystemItem
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsLitExpr

class RsFilePathResolveTest : RsResolveTestBase() {

    fun `test include macro path`() = checkResolve("""
    //- main.rs
        include!("foo.rs");
                  //^ foo.rs
    //- foo.rs
        pub struct Foo;
    """)

    fun `test relative include macro path`() = checkResolve("""
    //- main.rs
        mod foo;
    //- foo.txt
        // some text
    //- foo/mod.rs
        fn foo() {
            let s = include_str!("../foo.txt");
        }                          //^ foo.txt
    """)

    fun `test include macro path with raw literal`() = checkResolve("""
    //- main.rs
        include_bytes!(r#"foo.txt"#);
                        //^ foo.txt
    //- foo.txt
        // some text
    """)

    fun `test include macro path with escape symbols`() = checkResolve("""
    //- main.rs
        include_bytes!("\u{0066}oo.txt");
                        //^ foo.txt
    //- foo.txt
        // some text
    """)

    fun `test resolve path on mod decl 1`() = checkResolve("""
    //- main.rs
        #[path="bar.rs"]
               //^ bar.rs
        mod foo;

    //- bar.rs
        fn bar() {}
    """)

    fun `test resolve path on mod decl 2`() = checkResolve("""
    //- main.rs
        #[path="baz/bar.rs"]
                   //^ baz/bar.rs
        mod foo;

    //- baz/bar.rs
        fn bar() {}
    """)

    fun `test resolve path on mod`() = checkResolve("""
    //- main.rs
        #[path="baz"]
               //^ baz
        mod foo {
            #[path="qqq.rs"]
            mod bar;
        }

    //- baz/qqq.rs
        fn bar() {}
    """)

    fun `test resolve path in nested mod decl`() = checkResolve("""
    //- main.rs
        #[path="baz"]
        mod foo {
            #[path="qqq.rs"]
                   //^ baz/qqq.rs
            mod bar;
        }

    //- baz/qqq.rs
        fn bar() {}
    """)

    private fun checkResolve(@Language("Rust") code: String) {
        stubOnlyResolve<RsLitExpr>(fileTreeFromText(code)) { it is PsiFileSystemItem }
    }
}
