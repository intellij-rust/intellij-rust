/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import junit.framework.TestCase


/** Tries to find the specified annotation on the current test method and then on the current class */
inline fun <reified T : Annotation> TestCase.findAnnotationInstance(): T? =
    javaClass.getMethod(name).getAnnotation(T::class.java) ?: javaClass.getAnnotation(T::class.java)

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
