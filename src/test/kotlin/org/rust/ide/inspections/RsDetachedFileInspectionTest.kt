/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.fixes.withMockModuleAttachSelector

class RsDetachedFileInspectionTest : RsInspectionsTestBase(RsDetachedFileInspection::class) {
    fun `test attached file`() = checkByFileTree("""
        //- lib.rs
            mod foo;
        //- foo.rs
        /*caret*/
    """)

    fun `test included file`() = checkByFileTree("""
        //- lib.rs
            include!("foo.rs");
        //- foo.rs
        /*caret*/
    """)

    @ExpandMacros
    fun `test included file via macro`() = checkByFileTree("""
        //- lib.rs
            macro_rules! generate_include {
                (${'$'}package: tt) => {
                    include!(${'$'}package);
                };
            }

            generate_include!("foo.rs");
        //- foo.rs
        /*caret*/
    """)

    fun `test not included file`() = checkByFileTree("""
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """)

    fun `test fix not available if no module is found`() = checkFixIsUnavailableByFileTree("Attach file to a module", """
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """)

    fun `test fix not available if module is not attached`() = checkFixIsUnavailableByFileTree("Attach file to mod.rs", """
        //- mod.rs
            fn test() {}
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """)

    fun `test fix not available if module is not in the same directory`() = checkFixIsUnavailableByFileTree("Attach file to lib.rs", """
        //- lib.rs
            fn test() {}
        //- a/foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """)

    fun `test attach file to a local mod file`() = checkFixByFileTree("Attach file to mod.rs", """
        //- lib.rs
            mod a;
        //- a/mod.rs
        //- a/foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- lib.rs
            mod a;
        //- a/mod.rs
            /*caret*/mod foo;
        //- a/foo.rs
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test attach file to a parent mod file`() = checkFixByFileTree("Attach file to a.rs", """
        //- lib.rs
            mod a;
        //- a.rs
        //- a/foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- lib.rs
            mod a;
        //- a.rs
            /*caret*/mod foo;
        //- a/foo.rs
    """)

    fun `test attach file to library root`() = checkFixByFileTree("Attach file to lib.rs", """
        //- lib.rs
            fn test() {}
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- lib.rs
            /*caret*/mod foo;

            fn test() {}
        //- foo.rs
    """)

    fun `test attach file to binary root`() = checkFixByFileTree("Attach file to main.rs", """
        //- main.rs
            fn main() {}
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- main.rs
            /*caret*/mod foo;

            fn main() {}
        //- foo.rs
    """)

    fun `test attach file to selected module 1`() = checkFixWithMultipleModules("""
        //- main.rs
            fn main() {}
        //- lib.rs
            fn test() {}
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- main.rs
            /*caret*/mod foo;

            fn main() {}
        //- lib.rs
            fn test() {}
        //- foo.rs
    """, "main.rs")

    fun `test attach file to selected module 2`() = checkFixWithMultipleModules("""
        //- main.rs
            fn main() {}
        //- lib.rs
            fn test() {}
        //- foo.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- main.rs
            fn main() {}
        //- lib.rs
            /*caret*/mod foo;

            fn test() {}
        //- foo.rs
    """, "lib.rs")

    fun `test attach module file`() = checkFixWithMultipleModules("""
        //- main.rs
            fn main() {}
        //- lib.rs
            fn test() {}
        //- a/mod.rs
        <warning descr="File is not included in module tree, analysis is not available"></warning>/*caret*/
    """, """
        //- main.rs
            fn main() {}
        //- lib.rs
            /*caret*/mod a;

            fn test() {}
        //- a/mod.rs
    """, "lib.rs")

    private fun checkFixWithMultipleModules(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        moduleName: String
    ) {
        withMockModuleAttachSelector({ _, modules ->
            modules.find { it.name == moduleName }
        }) {
            checkFixByFileTree("Attach file to a module", before, after)
        }
    }
}
