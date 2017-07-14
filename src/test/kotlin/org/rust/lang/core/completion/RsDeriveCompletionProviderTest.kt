/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.lang.core.resolve.StdDerivableTrait
import org.rust.lang.core.resolve.withDependencies

class RsDeriveCompletionProviderTest : RsCompletionTestBase() {
    fun testCompleteOnStruct() = checkSingleCompletion("Debug", """
        #[derive(Debu/*caret*/)]
        struct Test {
            foo: u8
        }
    """)

    fun testCompleteOnEnum() = checkSingleCompletion("Debug", """
        #[derive(Debu/*caret*/)]
        enum Test {
            Something
        }
    """)

    fun `test complete with dependencies`() {
        StdDerivableTrait.values()
            .filter { it.dependencies.isNotEmpty() }
            .forEach {
                checkContainsCompletion(it.withDependencies.joinToString(", "), """
                    #[derive(${it.name.dropLast(1)}/*caret*/)]
                    struct Foo;
                """)
            }
    }

    fun `test complete with partially implemented dependencies`() = checkContainsCompletion("Ord, Eq, PartialEq", """
        #[derive(PartialOrd, Or/*caret*/)]
        struct Foo;
    """)

    fun testDoesntCompleteOnFn() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        fn foo() { }
    """)

    fun testDoesntCompleteOnMod() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        mod foo { }
    """)

    fun testDoesntCompleteNonDeriveAttr() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        enum Test { Something }
    """)

    fun testDoesntCompleteInnerAttr() = checkNoCompletion("""
        mod bar {
            #![derive(PartialE/*caret*/)]
        }
    """)

    fun testDoesntCompleteAlreadyDerived() = checkNoCompletion("""
        #[derive(Debug, Debu/*caret*/)]
        enum Test { Something }
    """)
}
