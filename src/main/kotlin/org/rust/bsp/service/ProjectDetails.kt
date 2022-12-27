/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.BuildTarget
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.DependencySourcesItem
import ch.epfl.scala.bsp4j.JavacOptionsItem
import ch.epfl.scala.bsp4j.ResourcesItem
import ch.epfl.scala.bsp4j.SourcesItem

public data class ProjectDetails(
    val targetsId: List<BuildTargetIdentifier>,
    val targets: Set<BuildTarget>,
    val sources: List<SourcesItem>,
    val resources: List<ResourcesItem>,
    val dependenciesSources: List<DependencySourcesItem>,
    val javacOptions: List<JavacOptionsItem>,
)
