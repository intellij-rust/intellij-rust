/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import junit.framework.TestCase
import org.rust.openapiext.EmptyTestmark
import org.rust.openapiext.TestmarkPred
import org.rust.openapiext.not


/** Tries to find the specified annotation on the current test method and then on the current class */
inline fun <reified T : Annotation> TestCase.findAnnotationInstance(): T? =
    javaClass.getMethod(name).getAnnotation(T::class.java) ?: javaClass.getAnnotation(T::class.java)

fun TestCase.collectTestmarksFromAnnotations(): TestmarkPred {
    val testmarks = mutableListOf<TestmarkPred>()
    findAnnotationInstance<CheckTestmarkHit>()?.testmarkClasses?.mapNotNullTo(testmarks) { it.objectInstance }
    findAnnotationInstance<CheckTestmarkNotHit>()?.testmarkClasses?.mapNotNullTo(testmarks) { it.objectInstance?.not() }
    return when (testmarks.size) {
        0 -> EmptyTestmark
        1 -> testmarks.single()
        else -> error("Using multiple testmarks is not supported, but it should be easy! " +
            "just make an implementation of `TestmarkPred` for a list of `TestmarkPred`s")
    }
}

inline fun <reified X : Throwable> expect(f: () -> Unit) {
    try {
        f()
    } catch (e: Throwable) {
        if (e is X)
            return
        throw e
    }
    RsTestBase.fail("No ${X::class.java} was thrown during the test")
}
