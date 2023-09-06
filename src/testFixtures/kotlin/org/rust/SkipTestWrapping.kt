/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import java.lang.annotation.Inherited

/**
 * You can mark a test with this annotation to skip the test when it is run with [TestWrapping].
 * Leave the [wrapping] list empty to skip all wrappings (except [TestWrapping.NONE]) or
 * enumerate [TestWrapping]s you want the test to skip.
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipTestWrapping(vararg val wrapping: TestWrapping)
