/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsDoctestInjectionCompletionTest : RsCompletionTestBase("lib.rs") {

    fun `test simple`() = doSingleCompletion("""
        /// ```
        /// fn foo() {}
        /// fn main() {
        ///     fo/*caret*/
        /// }
        /// ```
        fn func() {}
    """, """
        /// ```
        /// fn foo() {}
        /// fn main() {
        ///     foo()/*caret*/
        /// }
        /// ```
        fn func() {}
    """)

    fun `test out of scope same crate`() = expect<IllegalStateException> {
    doSingleCompletion("""
        /// ```
        /// mod inner {
        ///     pub fn foo() {}
        /// }
        /// fn main() {
        ///     fo/*caret*/
        /// }
        /// ```
        fn func() {}
    """, """
        /// ```
        /// use inner::foo;
        /// mod inner {
        ///     pub fn foo() {}
        /// }
        /// fn main() {
        ///     foo()/*caret*/
        /// }
        /// ```
        fn func() {}
    """)
    }

    // TODO: Fix formatting & insert import at top level
    fun `test out of scope different crate`() = doSingleCompletion("""
        /// ```
        /// fn main() {
        ///     fo/*caret*/
        /// }
        /// ```
        fn func() {}
        pub fn foo() {}
    """, """
        /// ```
        /// fn main() {
        ///     use test_package::foo;
        /// foo()/*caret*/
        /// }
        /// ```
        fn func() {}
        pub fn foo() {}
    """)
}
