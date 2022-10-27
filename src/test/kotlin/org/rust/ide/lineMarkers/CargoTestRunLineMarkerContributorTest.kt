/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.cargo.icons.CargoIcons
import org.rust.fileTree
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.descendantOfTypeStrict

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

    fun `test mod decl`() = doTestByFileTree("lib.rs") {
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

    fun `test show a test mark as default for test function`() = checkElement<RsFunction>("""
        #[test]
        fn test() {}
    """) {
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

    fun `test simple custom test attribute`() = doTestByText("""
        #[custom_test]
        fn has_icon() { assert(true) } // - Test has_icon
    """)

    fun `test custom test attribute with path`() = doTestByText("""
        #[tokio::test]
        fn has_icon() { assert(true) } // - Test has_icon
    """)

    fun `test custom test attribute with parameters`() = doTestByText("""
        #[tokio::test(threaded_scheduler)]
        fn has_icon() { assert(true) } // - Test has_icon
    """)

    fun `test custom test attribute with underscore`() = doTestByText("""
        #[my_tokio::test(threaded_scheduler)]
        fn has_icon() { assert(true) } // - Test has_icon
    """)

    fun `test ignore attributes with irrelevant test 1`() = doTestByText("""
        #[cfg(test)]
        fn has_icon() { assert(true) }
    """)

    fun `test ignore attributes with irrelevant test 2`() = doTestByText("""
        #[cfg(not(test))]
        fn has_icon() { assert(true) }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test attribute under cfg_attr`() = doTestByText("""
        #[cfg_attr(intellij_rust, test)]
        fn has_icon() { assert(true) } // - Test has_icon
        #[cfg_attr(not(intellij_rust), test)]
        fn no_icon() { assert(true) }
    """)

    fun `test function doctest`() = doTestByText("""
        /// Some documentation.
        /// ```                 // - Doctest of foo (line 2)
        /// let a = 5;
        /// ```
        fn foo() {}
    """.trimIndent())

    fun `test module doctest`() = doTestByText("""
        /// Some documentation.
        /// ```                 // - Doctest of foo (line 2)
        /// let a = 5;
        /// ```
        mod foo {}
    """.trimIndent())

    fun `test multiple doctests`() = doTestByText("""
        /// Some documentation.
        /// ```                 // - Doctest of foo (line 2)
        /// let a = 5;
        /// ```
        ///
        /// ```                 // - Doctest of foo (line 6)
        /// let a = 5;
        /// ```
        fn foo() {}
    """.trimIndent())

    fun `test function in module doctest`() = doTestByText("""
        mod foo {
            /// Some documentation.
            /// ```                 // - Doctest of foo::bar (line 3)
            /// let a = 5;
            /// ```
            fn bar() {}
        }
    """.trimIndent())

    fun `test top-level doctest`() = doTestByText("""
        //! Some documentation.
        //! ```                 // - Doctest of test-package (line 2)
        //! let a = 5;
        //! ```
    """.trimIndent())

    fun `test code block with other language`() = doTestByText("""
        /// Some documentation.
        /// ```python
        /// a = 5
        /// ```
        fn foo() {}
    """.trimIndent())

    fun `test code block with rust`() = doTestByText("""
        /// Some documentation.
        /// ```rust             // - Doctest of foo (line 2)
        /// let a = 5;
        /// ```
        fn foo() {}
    """.trimIndent())

    fun `test unterminated doctest`() = doTestByText("""
        /// Some documentation.
        /// ```                 // - Doctest of foo (line 2)
        /// let a = 5;
        fn foo() {}
    """.trimIndent())

    fun `test doctest with unterminated predecessor`() = doTestByText("""
        /// Some documentation.
        /// ```                 // - Doctest of foo (line 2)
        /// let a = 5;
        //
        /// ```
        /// let b = 5;
        /// ```
        fn foo() {}
    """.trimIndent())

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test tests in dependency`() = doTestByFileTree("dep-lib/lib.rs") {
        dir("dep-lib") {
            rust("lib.rs", """
                /// ```
                /// let a = 5;
                /// ```
                fn foo() {}
                #[test]
                fn test() {}
            """)
        }
    }

    private inline fun <reified E : RsElement> checkElement(@Language("Rust") code: String, callback: (E) -> Unit) {
        val element = PsiFileFactory.getInstance(project)
            .createFileFromText("main.rs", RsFileType, code)
            .descendantOfTypeStrict<E>() ?: error("No ${E::class.java} in\n$code")
        callback(element)
    }
}
