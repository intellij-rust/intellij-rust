/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import org.rust.cargo.project.settings.RustProjectSettingsService

/**
 * Specify which cargo features to use in the test.
 * Handled by [RsWithToolchainTestBase]
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CargoFeatures(val cargoFeatures: RustProjectSettingsService.FeaturesSetting, val cargoFeaturesAdditional: String)

