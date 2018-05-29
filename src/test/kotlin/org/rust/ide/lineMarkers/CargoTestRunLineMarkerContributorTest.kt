/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.test.CargoTestLocator
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import java.time.Instant
import java.util.*

/**
 * Tests for Test Function Line Marker
 */
class CargoTestRunLineMarkerContributorTest : RsLineMarkerProviderTestBase() {
    fun `test simple function`() = doTestByText("""
        #[test]
        fn has_icon() {assert(true)} // - Test has_icon

        fn no_icon() {assert(true)}
    """)

    fun `test function in a module`() = doTestByText("""
        mod module { // - Test module
            #[test]
            fn has_icon() {assert(true)} // - Test module::has_icon

            fn no_icon() {assert(true)}
        }
    """)

    fun `test function in a test module`() = doTestByText("""
        #[cfg(test)]
        mod test { // - Test lib::test
            #[test]
            fn has_icon() {assert(true)} // - Test test::has_icon

            fn no_icon() {assert(true)}
        }
    """)

    fun `test function in a tests module`() = doTestByText("""
        #[cfg(test)]
        mod tests { // - Test lib::tests
            #[test]
            fn has_icon() {assert(true)} // - Test tests::has_icon

            fn no_icon() {assert(true)}
        }
    """)

    fun `test show a green mark for a passed test`() = checkElement<RsFunction>("""
        #[test]
        fn passing_test() {}
        """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(CargoTestLocator.getTestFnUrl(it),
            TestStateStorage.Record(TestStateInfo.Magnitude.PASSED_INDEX.value, Date.from(Instant.now()), 1))
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST_GREEN, icon)
    }

    fun `test show a red mark for a failed test`() = checkElement<RsFunction>("""
        #[test]
        fn failing_test() {}
        """) {
        val instance = TestStateStorage.getInstance(project)
        instance.writeState(CargoTestLocator.getTestFnUrl(it),
            TestStateStorage.Record(TestStateInfo.Magnitude.ERROR_INDEX.value, Date.from(Instant.now()), 1))
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST_RED, icon)
    }

    fun `test show a test mark as default for test function`() = checkElement<RsFunction>("""
        #[test]
        fn foo_bar() {}
        """) {
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST, icon)
    }

    fun `test show a test mark as default for mod`() = checkElement<RsMod>("""
        mod tests {
            #[test]
            fn passing_test() {}
        }
        """) {
        val icon = CargoTestRunLineMarkerContributor.getTestStateIcon(it)
        assertEquals(CargoIcons.TEST, icon)
    }

    private inline fun <reified E : RsElement> checkElement(@Language("Rust") code: String, callback: (E) -> Unit) {
        val element = PsiFileFactory.getInstance(project)
            .createFileFromText("main.rs", RsFileType, code)
            .descendantOfTypeStrict<E>() ?: error("No ${E::class.java} in\n$code")

        callback(element)
    }

}
