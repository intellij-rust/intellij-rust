/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import java.lang.annotation.Inherited
import kotlin.reflect.KClass

/**
 * Allows to set [RustProjectDescriptorBase] for a specific test.
 * The [descriptor] class must be a kotlin object (`object Foo : RustProjectDescriptorBase`).
 *
 * Example values:
 * - [WithStdlibRustProjectDescriptor]
 * - [WithDependencyRustProjectDescriptor]
 * - [WithStdlibAndDependencyRustProjectDescriptor]
 * - [WithProcMacroRustProjectDescriptor]
 *
 * @see RsTestBase.getProjectDescriptor
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProjectDescriptor(val descriptor: KClass<out RustProjectDescriptorBase>)
