/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import org.rust.openapiext.Testmark
import kotlin.reflect.KClass

/**
 * The [testmarkClasses] must be a kotlin object (`object Foo : Testmark`).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckTestmarkHit(vararg val testmarkClasses: KClass<out Testmark>)

/**
 * The [testmarkClasses] must be a kotlin object (`object Foo : Testmark`).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckTestmarkNotHit(vararg val testmarkClasses: KClass<out Testmark>)
