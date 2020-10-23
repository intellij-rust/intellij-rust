/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText

class RsMoveMultipleFilesTest : RsMoveFileTestBase() {

    fun `test simple`() = doTest(
        arrayOf("mod1/foo1.rs", "mod1/foo2.rs"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo1;
        mod foo2;
    //- mod2.rs
    //- mod1/foo1.rs
        fn func1() {}
    //- mod1/foo2.rs
        fn func2() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        mod foo1;
        mod foo2;
    //- mod2/foo1.rs
        fn func1() {}
    //- mod2/foo2.rs
        fn func2() {}
    """)

    fun `test move directory and file`() = doTest(
        arrayOf("mod1/foo1", "mod1/foo2.rs"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo1;
        mod foo2;
    //- mod2.rs
    //- mod1/foo1/mod.rs
        fn func1() {}
    //- mod1/foo2.rs
        fn func2() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        mod foo1;
        mod foo2;
    //- mod2/foo1/mod.rs
        fn func1() {}
    //- mod2/foo2.rs
        fn func2() {}
    """)

    fun `test move files with cross self references`() = doTest(
        arrayOf("mod1/foo1.rs", "mod1/foo2.rs"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo1;
        mod foo2;
    //- mod2.rs
    //- mod1/foo1.rs
        pub fn func1() {
            crate::mod1::foo2::func2();
        }
    //- mod1/foo2.rs
        pub fn func2() {
            crate::mod1::foo1::func1();
        }
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        mod foo1;
        mod foo2;
    //- mod2/foo1.rs
        pub fn func1() {
            crate::mod2::foo2::func2();
        }
    //- mod2/foo2.rs
        pub fn func2() {
            crate::mod2::foo1::func1();
        }
    """)

    fun `test can't move non-rust file`() = checkCantMove(
        arrayOf("foo.txt"),
        "target",
        """
    //- foo.txt
        // foo content
    """)

    // TODO: Actually would be nice to handle such move somehow
    fun `test can't move rust file along with non-rust file`() = checkCantMove(
        arrayOf("mod1/foo1.rs", "mod1/foo2.txt"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo1;
    //- mod2.rs
    //- mod1/foo1.rs
        fn func1() {}
    //- mod1/foo2.txt
        // foo2 content
    """)

    // default move processor will be used when our processor can't be used
    private fun checkCantMove(
        elementsToMove: Array<String>,
        targetDirectory: String,
        @Language("Rust") before: String
    ) {
        val testProject = fileTreeFromText(before).create()
        val (psiElementsToMove, psiTargetDirectory) =
            getElementsToMoveAndTargetDirectory(elementsToMove, testProject.root, targetDirectory)
        val canMove = RsMoveFilesOrDirectoriesHandler().canMove(psiElementsToMove, psiTargetDirectory, null)
        assertFalse(canMove)
    }
}
