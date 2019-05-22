/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import org.rust.cargo.project.model.CargoProjectsService
import java.lang.annotation.Inherited

/**
 * Like [org.rust.MockEdition], but makes the test run 2 times with both edition
 *
 * @see CargoProjectsService.setEdition
 */
@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class BothEditions
