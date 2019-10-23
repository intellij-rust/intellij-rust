/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects

val Project.testCargoProjects: TestCargoProjectsServiceImpl get() = cargoProjects as TestCargoProjectsServiceImpl
