/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import junit.framework.Test
import org.junit.internal.MethodSorter
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.TestClass
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**
 * Almost standard JUnit 4 test runner (see [BlockJUnit4ClassRunner]), but unlike the standard one
 * it adds JUnit3-style test methods to the list of tests.
 * In essence, you don't have to add `@Test` annotation to each test method when using this runner.
 */
class RsJUnit4TestRunner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
    override fun computeTestMethods(): List<FrameworkMethod> =
        addJUnit3Methods(super.computeTestMethods(), testClass)

    companion object {
        fun addJUnit3Methods(junit4Methods: List<FrameworkMethod>, testClass: TestClass): List<FrameworkMethod> {
            val junit3Methods = computeJUnit3TestMethods(testClass)

            return if (junit3Methods.isEmpty()) {
                junit4Methods
            } else {
                val all = junit4Methods.toMutableList()
                junit3Methods.mapTo(all) { FrameworkMethod(it) }
                Collections.unmodifiableList(all)
            }.sortTests()
        }

        private fun computeJUnit3TestMethods(testClass: TestClass): List<Method> {
            val theClass = testClass.javaClass
            var superClass = theClass
            val names = mutableSetOf<String>()
            val testMethods = mutableListOf<Method>()
            while (Test::class.java.isAssignableFrom(superClass)) {
                for (method in MethodSorter.getDeclaredMethods(superClass)) {
                    val name = method.name
                    if (!names.contains(name) && isJUnit3TestMethod(method)) {
                        names.add(name)
                        testMethods += method
                    }
                }
                superClass = superClass.superclass
            }

            return testMethods
        }

        private fun isJUnit3TestMethod(m: Method): Boolean {
            return m.parameterTypes.isEmpty()
                && m.name.startsWith("test")
                && m.returnType == Void.TYPE
                && Modifier.isPublic(m.modifiers)
                && m.getAnnotation(org.junit.Test::class.java) == null
        }

        private fun List<FrameworkMethod>.sortTests(): List<FrameworkMethod> {
            // Switching between project descriptors is expensive, so let's group tests by project descriptor
            return map { it to it.projectDescriptorName }
                .sortedBy { it.second }
                .map { it.first }
        }

        private val FrameworkMethod.projectDescriptorName: String
            get() = method.getAnnotation(ProjectDescriptor::class.java)?.descriptor?.simpleName ?: ""
    }
}
