/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiFile
import org.rust.FileTreeBuilder
import org.rust.fileTree
import org.rust.lang.core.psi.RsLitExpr

class RsIncludeMacroPathResolveTest : RsResolveTestBase() {

    fun `test path`() = checkResolve {
        rust("main.rs", """
            include!("foo.rs");
                      //^ .../foo.rs
        """)
        rust("foo.rs", """
            pub struct Foo;
        """)
    }

    fun `test relative path`() = checkResolve {
        rust("main.rs", """
            mod foo;
        """)
        file("foo.txt", "")
        dir("foo") {
            rust("mod.rs", """
                fn foo() {
                    let s = include_str!("../foo.txt");
                }                          //^ .../foo.txt
            """)
        }
    }

    fun `test path with raw literal`() = checkResolve {
        rust("main.rs", """
            include_bytes!(r#"foo.txt"#);
                            //^ .../foo.txt
        """)
        file("foo.txt", "")
    }

    fun `test path with escape symbols`() = checkResolve {
        rust("main.rs", """
            include_bytes!("\u{0066}oo.txt");
                             //^ .../foo.txt
        """)
        file("foo.txt", "")
    }

    private fun checkResolve(builder: FileTreeBuilder.() -> Unit) {
        val fileTree = fileTree(builder)
        stubOnlyResolve<RsLitExpr>(fileTree) { it is PsiFile }
    }
}
