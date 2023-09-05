/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.codeInspection.InspectionProfileEntry
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

/**
 * Enables the specified inspections for the given test.
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithEnabledInspections(vararg val inspections: KClass<out InspectionProfileEntry>)
