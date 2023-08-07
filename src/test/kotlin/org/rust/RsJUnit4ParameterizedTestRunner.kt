/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runners.Parameterized
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * Almost standard JUnit 4 parametrized test runner (see [Parameterized]), but unlike the standard one
 * it adds JUnit3-style test methods to the list of tests.
 * In essence, you don't have to add `@Test` annotation to each test method when using this runner.
 *
 * Use it like this:
 * ```
 * @RunWith(RsJUnit4ParameterizedTestRunner::class)
 * @UseParametersRunnerFactory(RsJUnit4ParameterizedTestRunner.RsRunnerForParameters.Factory::class)
 * ```
 */
class RsJUnit4ParameterizedTestRunner(testClass: Class<*>): Parameterized(testClass) {
    override fun filter(filter: Filter) {
        val wrappedFilter = object : Filter() {
            override fun shouldRun(description: Description): Boolean {
                if (filter.shouldRun(description)) return true

                val descriptionCopy = Description.createSuiteDescription(
                    description.displayName,
                    *description.annotations.toTypedArray()
                )
                for (child in description.children) {
                    descriptionCopy.addChild(removeParamsFromDescription(child))
                }
                return filter.shouldRun(descriptionCopy)
            }

            override fun describe(): String = filter.describe()
            override fun apply(child: Any) = filter.apply(child)
        }
        super.filter(wrappedFilter)
    }

    class RsRunnerForParameters(test: TestWithParameters) : BlockJUnit4ClassRunnerWithParameters(test) {
        override fun filter(filter: Filter) {
            val wrappedFilter = object : Filter() {
                override fun shouldRun(description: Description): Boolean {
                    if (filter.shouldRun(description)) return true
                    return filter.shouldRun(removeParamsFromDescription(description))
                }

                override fun describe(): String = filter.describe()
                override fun apply(child: Any) = filter.apply(child)
            }
            super.filter(wrappedFilter)
        }

        override fun computeTestMethods(): List<FrameworkMethod> =
            RsJUnit4TestRunner.addJUnit3Methods(super.computeTestMethods(), testClass)

        class Factory : ParametersRunnerFactory {
            override fun createRunnerForTestWithParameters(test: TestWithParameters): Runner =
                RsRunnerForParameters(test)
        }
    }
}

private fun removeParamsFromDescription(description: Description): Description? {
    val name = description.displayName
    val i = name.indexOf("[")
    if (i == -1) return description

    return Description.createTestDescription(
        description.testClass,
        name.substring(0, i),
        *description.annotations.toTypedArray()
    )
}
