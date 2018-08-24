/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.testFramework.LightProjectDescriptor
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

/**
 * Allows to set [LightProjectDescriptor] for a specific test.
 * The [descriptor] class must be a kotlin object (`object Foo : LightProjectDescriptor`).
 *
 * Example values:
 * - [WithStdlibRustProjectDescriptor]
 * - [WithStdlibAndDependencyRustProjectDescriptor]
 *
 * @see RsTestBase.getProjectDescriptor
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProjectDescriptor(val descriptor: KClass<out LightProjectDescriptor>)
