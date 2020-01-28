/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsMacroBracketCompletionTest : RsCompletionTestBase() {

    fun `test default bracket`() = doSingleCompletion("""
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo/*caret*/
        }
    """, """
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo!(/*caret*/)
        }
    """)

    fun `test bracket from documentation`() = doSingleCompletion("""
        /// `foo![]`
        ///
        /// ```
        /// foo![];
        /// bar_foo!();
        /// foo();
        /// ```
        ///
        /// `foo!()`
        ///
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo/*caret*/
        }
    """, """
        /// `foo![]`
        ///
        /// ```
        /// foo![];
        /// bar_foo!();
        /// foo();
        /// ```
        ///
        /// `foo!()`
        ///
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo![/*caret*/]
        }
    """)

    fun `test insert space for before braces`() = doSingleCompletion("""
        /// `foo! {}`
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo/*caret*/
        }
    """, """
        /// `foo! {}`
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo! {/*caret*/}
        }
    """)

    fun `test raw macro calls`() = doSingleCompletion("""
        /// `r#foo![]`
        ///
        /// ```
        /// foo!();
        /// foo![];
        /// ```
        ///
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo/*caret*/
        }
    """, """
        /// `r#foo![]`
        ///
        /// ```
        /// foo!();
        /// foo![];
        /// ```
        ///
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo![/*caret*/]
        }
    """)

    fun `test do not mess with other macro calls`() = doSingleCompletion("""
        /// ```
        /// foo![];
        /// assert!(true);
        /// assert!(true);
        /// ```
        ///
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo/*caret*/
        }
    """, """
        /// ```
        /// foo![];
        /// assert!(true);
        /// assert!(true);
        /// ```
        ///
        macro_rules! foo {
            () => {};
        }
        fn main() {
            foo![/*caret*/]
        }
    """)
}
