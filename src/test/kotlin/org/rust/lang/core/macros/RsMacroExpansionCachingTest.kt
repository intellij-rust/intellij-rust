/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.resolve.RsResolveTestBase

class RsMacroExpansionCachingTest : RsResolveTestBase() {
    fun `test macro re-expanded after external file change`() {
        val p = fileTreeFromText("""
        //- main.rs
            #[macro_use]
            mod macros;
            foo!();
            fn main() {
                foo();
                bar();
            }  //^
        //- macros.rs
            macro_rules! foo {
                () => {
                    fn foo() {}
//                            fn bar() {}
                }
            }
        """).createAndOpenFileWithCaretMarker()

        val ref = p.findElementInFile<RsPath>("main.rs").reference
        check(ref.resolve() == null)

        check((p.psiFile("macros.rs") as RsFile).stub != null)

        val macros = p.root.findFileByRelativePath("macros.rs")!!
        runWriteAction {
            VfsUtil.saveText(macros, VfsUtil.loadText(macros).replace("//", ""))
        }

        check((p.psiFile("macros.rs") as RsFile).stub != null)

        check(ref.resolve() != null)
    }
}
