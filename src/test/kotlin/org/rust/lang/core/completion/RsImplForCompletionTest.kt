/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.openapi.project.DumbServiceImpl
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.rust.RsJUnit4ParameterizedTestRunner
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RsJUnit4ParameterizedTestRunner::class)
@Parameterized.UseParametersRunnerFactory(RsJUnit4ParameterizedTestRunner.RsRunnerForParameters.Factory::class)
class RsImplForCompletionTest : RsCompletionTestBase() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Iterable<Any> {
            return listOf(AtomicBoolean(false), AtomicBoolean(true))
        }
    }

    // @Parameter doesn't work properly for primitive Kotlin types,
    // throws `can't assign to a private member` exception even when the field is public
    @Parameter(0)
    lateinit var isDumbMode: AtomicBoolean

    override fun setUp() {
        super.setUp()
        DumbServiceImpl.getInstance(project).isDumb = isDumbMode.get()
    }

    override fun tearDown() {
        DumbServiceImpl.getInstance(project).isDumb = false
        super.tearDown()
    }

    fun `test impl trait for completion`() = checkCompletion("for", """
        trait A {}
        struct S {}

        impl A f/*caret*/
    """, """
        trait A {}
        struct S {}

        impl A for /*caret*/
    """)

    fun `test impl trait for bracket completion`() = checkCompletion("for", """
        trait A {}
        struct S {}

        impl A f/*caret*/ {
    """, """
        trait A {}
        struct S {}

        impl A for /*caret*/ {
    """)

    fun `test impl generic trait for completion`() = checkCompletion("for", """
        trait A<T> {}
        struct S<T> {}

        impl<T> A<T> f/*caret*/
    """, """
        trait A<T> {}
        struct S<T> {}

        impl<T> A<T> for /*caret*/
    """)

    fun `test impl generic trait for bracket completion`() = checkCompletion("for", """
        trait A<T> {}
        struct S<T> {}

        impl<T> A<T> f/*caret*/ {
    """, """
        trait A<T> {}
        struct S<T> {}

        impl<T> A<T> for /*caret*/ {
    """)

    fun `test impl trait for where completion`() = checkCompletion("for", """
        trait A {}
        trait B {}
        struct S {}

        impl<T> A f/*caret*/ where T: B
    """, """
        trait A {}
        trait B {}
        struct S {}

        impl<T> A for /*caret*/ where T: B
    """)


    fun `test impl trait for where bracket completion`() = checkCompletion("for", """
        trait A {}
        trait B {}
        struct S {}

        impl<T> A f/*caret*/ where T: B {
    """, """
        trait A {}
        trait B {}
        struct S {}

        impl<T> A for /*caret*/ where T: B {
    """)

    fun `test impl trait alias for no completion`() = checkCompletionInDumbModeOnly("for", """
        trait A {}
        type B = A;
        struct S {}

        // type aliases cannot be used as traits
        impl B f/*caret*/
    """, """
        trait A {}
        type B = A;
        struct S {}

        // type aliases cannot be used as traits
        impl B for /*caret*/
    """)

    fun `test impl struct for no completion`() = checkCompletionInDumbModeOnly("for", """
        struct S {}

        impl S f/*caret*/
    """, """
        struct S {}

        impl S for /*caret*/
    """)

    fun `test impl struct where for no completion`() = checkNotContainsCompletion("for", """
        struct S<T> {}
        trait A {}

        impl<T> S where T: A f/*caret*/
    """)

    fun `test impl struct for where no completion`() = checkCompletionInDumbModeOnly("for", """
        struct S<T> {}
        trait A {}

        impl<T> S f/*caret*/ where T: A
    """, """
        struct S<T> {}
        trait A {}

        impl<T> S for /*caret*/ where T: A
    """)

    fun `test impl for no completion`() = checkNotContainsCompletion("for", """
        struct S<T> {}
        trait A {}

        impl<T> f/*caret*/
    """)

    fun `test impl (trait) for no completion`() = checkNotContainsCompletion("for", """
        struct S {}
        trait A {}

        impl (A) f/*caret*/
    """)

    fun `test impl dyn trait for no completion`() = checkNotContainsCompletion("for", """
        struct S {}
        trait A {}

        impl dyn A f/*caret*/
    """)

    fun `test impl trait+trait for no completion`() = checkNotContainsCompletion("for", """
        struct S {}
        trait A {}
        trait B {}

        impl A+B f/*caret*/
    """)

    /**
     * In dumb mode, completion can be less strict and show more variants
     */
    private fun checkCompletionInDumbModeOnly(
        lookupString: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        completionChar: Char = '\n'
    )  {
        if (isDumbMode.get()) {
            checkCompletion(lookupString, before, after, completionChar)
        } else {
            checkNotContainsCompletion(lookupString, before)
        }
    }

}
