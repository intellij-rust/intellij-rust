/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.FileTree
import org.rust.fileTree
import org.rust.lang.RsTestBase

class ExtractInlineModuleIntentionTest : RsTestBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun `test valid extract inline module`() = doTest(
        fileTree {
            rust("main.rs", """
                mod /*caret*/foo {
                    // function
                    fn a() {}
                }

                fn main() {}
            """)
        },
        fileTree {
            rust("main.rs", """
                mod foo;

                fn main() {}
            """)
            rust("foo.rs", """
                // function
                fn a() {}
            """)
        }
    )

    fun `test extracting module preserves attributes and visibility`() = ExtractInlineModuleIntention.Testmarks.copyAttrs.checkHit {
        doTest(
            fileTree {
                rust("main.rs", """
                #[cfg(test)]
                pub(in super) mod /*caret*/tests {
                    #[test]
                    fn foo() {}
                }
            """)
            },
            fileTree {
                rust("main.rs", """
                #[cfg(test)]
                pub(in super) mod tests;
            """)
                rust("tests.rs", """
                #[test]
                fn foo() {}
            """)
            }
        )
    }

    fun `test invalid extract inline module`() {
        doTest(fileTree {
            rust("main.rs", """
                mod foo {
                    // function
                    fn a() {}
                }

                fn /*caret*/main() {}
            """)
        }, fileTree {
            rust("main.rs", """
                mod foo {
                    // function
                    fn a() {}
                }

                fn main() {}
            """)
        })
    }

    private fun doTest(before: FileTree, after: FileTree) {
        val testProject = before.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        myFixture.launchAction(ExtractInlineModuleIntention())
        after.assertEquals(myFixture.findFileInTempDir("."))
    }
}
