/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import java.lang.annotation.Inherited

/**
 * Allows to extend default cfg options ([org.rust.cargo.CfgOptions.DEFAULT]) for a specific test
 *
 * @see RsTestBase.setupMockCfgOptions
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockAdditionalCfgOptions(vararg val options: String)
