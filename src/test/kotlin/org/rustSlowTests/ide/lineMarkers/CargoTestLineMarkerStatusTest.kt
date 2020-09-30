/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.ide.lineMarkers

import org.intellij.lang.annotations.Language
import org.rust.cargo.icons.CargoIcons
import org.rust.ide.lineMarkers.CargoTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rustSlowTests.cargo.runconfig.test.CargoTestRunnerTestBase
import javax.swing.Icon

class CargoTestLineMarkerStatusTest : CargoTestRunnerTestBase() {

    fun `test show a green mark for a passed test`() = checkTestStateIcon<RsFunction>("""
        #[test]
        fn passing_test() {
            assert!(true);
        }
    """, CargoIcons.TEST_GREEN)

    fun `test show a red mark for a failed test`() = checkTestStateIcon<RsFunction>("""
        #[test]
        fn failing_test() {
            assert!(false);
        }
    """, CargoIcons.TEST_RED)

    fun `test show a mark for an ignored test`() = checkTestStateIcon<RsFunction>("""
        #[test]
        #[ignore]
        fn ignored_test() {}
    """, CargoIcons.TEST)

    fun `test show a green mark for a passed mod`() = checkTestStateIcon<RsMod>("""
        mod tests {
            #[test]
            fn passing_test() {
                assert!(true);
            }
        }
    """, CargoIcons.TEST_GREEN)

    fun `test show a red mark for a failed mod`() = checkTestStateIcon<RsMod>("""
        mod tests {
            #[test]
            fn failing_test() {
                assert!(false);
            }
        }
    """, CargoIcons.TEST_RED)

    fun `test show a mark for an ignored mod`() = checkTestStateIcon<RsMod>("""
        mod tests {
            #[test]
            #[ignore]
            fn ignored_test() {}
        }
    """, CargoIcons.TEST)

    private inline fun <reified E : RsElement> checkTestStateIcon(@Language("Rust") code: String, expected: Icon) {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    /*caret*/$code
                """)
            }
        }
        val file = myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createTestRunConfigurationFromContext()
        executeAndGetTestRoot(configuration)
        val element = file.descendantOfTypeStrict<E>() ?: error("No ${E::class.java} in\n$code")
        val actual = getTestStateIcon(element)
        assertEquals(expected, actual)
    }
}
