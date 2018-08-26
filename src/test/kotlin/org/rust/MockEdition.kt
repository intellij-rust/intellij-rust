/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.model.CargoProjectsService

/**
 * Allows to set certain edition for all [CargoProject]s in test case.
 *
 * @see CargoProjectsService.setEdition
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockEdition(val edition: CargoWorkspace.Edition)
