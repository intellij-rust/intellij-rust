/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.RsJUnit4ParameterizedTestRunner
import org.rust.SkipTestWrapping
import org.rust.TestWrapping
import kotlin.reflect.KClass

/**
 * A base test class for [annotator][AnnotatorBase] tests.
 *
 * By default, each test declared in a subclass of this class will run several times - one per each
 * [TestWrapping] value returned from [RsAnnotatorTestBase.data] method. This allows us to test annotators
 * under different circumstances, e.g. inside a procedural macro call. Use [SkipTestWrapping]
 * annotation to skip test run with a specific (or all) [TestWrapping] (s).
 */
@RunWith(RsJUnit4ParameterizedTestRunner::class)
@Parameterized.UseParametersRunnerFactory(RsJUnit4ParameterizedTestRunner.RsRunnerForParameters.Factory::class)
abstract class RsAnnotatorTestBase(private vararg val annotatorClasses: KClass<out AnnotatorBase>) : RsAnnotationTestBase() {

    @field:Parameterized.Parameter(0)
    override lateinit var testWrapping: TestWrapping

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Iterable<TestWrapping> = listOf(
            TestWrapping.NONE,
            TestWrapping.ATTR_MACRO_AS_IS_ALL_ITEMS,
        )
    }

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> =
        RsAnnotationTestFixture(this, myFixture, annotatorClasses = annotatorClasses.toList())
}
