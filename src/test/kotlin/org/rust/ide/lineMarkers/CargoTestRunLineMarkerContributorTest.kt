/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.test.CargoTestLocator
import org.rust.fileTree
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import java.time.Instant
import java.util.*

/**
 * Tests for Test Function Line Marker.
 */
class CargoTestRunLineMarkerContributorTest : RsLineMarkerProviderTestBase() {
    fun `test simple function`() = doTestByText("""
        #[test]
        fn has_icon() { assert(true) } // - Test has_icon
        fn no_icon() { assert(true) }
    """)

    fun `test function in a module`() = doTestByText("""
        mod module { // - Test module
            #[test]
            fn has_icon() { assert(true) } // - Test module::has_icon
            fn no_icon() { assert(true) }
        }
    """)

    fun `test function in a test module`() = doTestByText("""
        #[cfg(test)]
        mod test { // - Test lib::test
            #[test]
            fn has_icon() { assert(true) } // - Test test::has_icon
            fn no_icon() { assert(true) }
        }
    """)

    fun `test function in a tests module`() = doTestByText("""
        #[cfg(test)]
        mod tests { // - Test lib::tests
            #[test]
            fn has_icon() { assert(true) } // - Test tests::has_icon
            fn no_icon() { assert(true) }
        }
    """)

    @MockAdditionalCfgOptions("test")
    fun `test function in a nested tests module`() = doTestByText("""
        #[cfg(test)]
        mod tests { // - Test lib::tests
            #[cfg(test)]
            mod nested_tests { // - Test nested_tests
                #[test]
                fn has_icon() { assert(true) } // - Test tests::nested_tests::has_icon
                fn no_icon() { assert(true) }
            }
        }
    """)

    fun `test mod decl`() = doTestFromFile(
        "lib.rs",
        fileTree {
            rust("tests.rs", """
                #[test]
                fn test() {}
            """)

            rust("no_tests.rs", "")

            rust("lib.rs", """
                mod tests; // - Test lib::tests
                mod no_tests;
            """)
        }
    )

    fun `test show a test mark as default for test function`() = checkElement<RsFunction>("""
        #[test]
        fn test() {}
    """) {
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST, icon)
    }

    fun `test show a green mark for a passed test`() = checkElement<RsFunction>("""
        #[test]
        fn passing_test() {}
    """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(
            CargoTestLocator.getTestUrl(it),
            createRecord(TestStateInfo.Magnitude.PASSED_INDEX.value, Date.from(Instant.now()), 1)
        )
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST_GREEN, icon)
    }

    fun `test show a red mark for a failed test`() = checkElement<RsFunction>("""
        #[test]
        fn failing_test() {}
    """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(
            CargoTestLocator.getTestUrl(it),
            createRecord(TestStateInfo.Magnitude.ERROR_INDEX.value, Date.from(Instant.now()), 1)
        )
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST_RED, icon)
    }

    fun `test show a mark for an ignored test`() = checkElement<RsFunction>("""
        #[test]
        #[ignore]
        fn ignored_test() {}
    """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(
            CargoTestLocator.getTestUrl(it),
            createRecord(TestStateInfo.Magnitude.IGNORED_INDEX.value, Date.from(Instant.now()), 1)
        )
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST, icon)
    }

    fun `test show a test mark as default for mod`() = checkElement<RsMod>("""
        mod tests {
            #[test]
            fn test() {}
        }
    """) {
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST, icon)
    }

    fun `test show a green mark for a passed mod`() = checkElement<RsMod>("""
        mod tests {
            #[test]
            fn passing_test() {}
        }
    """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(
            CargoTestLocator.getTestUrl(it),
            createRecord(TestStateInfo.Magnitude.PASSED_INDEX.value, Date.from(Instant.now()), 1)
        )
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST_GREEN, icon)
    }

    fun `test show a red mark for a failed mod`() = checkElement<RsMod>("""
        mod tests {
            #[test]
            fn failing_test() {}
        }
    """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(
            CargoTestLocator.getTestUrl(it),
            createRecord(TestStateInfo.Magnitude.ERROR_INDEX.value, Date.from(Instant.now()), 1)
        )
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST_RED, icon)
    }

    fun `test show a mark for an ignored mod`() = checkElement<RsMod>("""
        mod tests {
            #[test]
            #[ignore]
            fn ignored_test() {}
        }
    """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(
            CargoTestLocator.getTestUrl(it),
            createRecord(TestStateInfo.Magnitude.IGNORED_INDEX.value, Date.from(Instant.now()), 1)
        )
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST, icon)
    }

    /** Issue [3386](https://github.com/intellij-rust/intellij-rust/issues/3386) */
    fun `test no extra markers next to syntax error elements`() = doTestByText("""
        fn foo bar<T>(t: T) {}
        #[test]
        fn has_icon() { assert(true) } // - Test has_icon
    """)

    fun `test quickcheck`() = doTestByText("""
        #[quickcheck]
        fn has_icon() { assert(true) } // - Test has_icon
    """)

    private inline fun <reified E : RsElement> checkElement(@Language("Rust") code: String, callback: (E) -> Unit) {
        val element = PsiFileFactory.getInstance(project)
            .createFileFromText("main.rs", RsFileType, code)
            .descendantOfTypeStrict<E>() ?: error("No ${E::class.java} in\n$code")
        callback(element)
    }

    companion object {
        private fun createRecord(magnitude: Int, date: Date, configurationHash: Long): TestStateStorage.Record =
            TestStateStorage.Record(magnitude, date, configurationHash, 0, "", "", "")
    }
}
