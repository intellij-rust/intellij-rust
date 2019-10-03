/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import java.lang.annotation.Inherited

/**
 * Allows extending default cfg options ([org.rust.cargo.CfgOptions.DEFAULT]) for a specific test.
 *
 * NB: Only a few cfg options are allowed to be evaluated.
 * You can either use the special test-only [org.rust.cargo.CfgOptions.TEST] option here
 * or one of the supported options from [org.rust.lang.utils.evaluation.CfgEvaluator.SUPPORTED_NAME_OPTIONS].
 *
 * @see RsTestBase.setupMockCfgOptions
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockAdditionalCfgOptions(vararg val options: String)
