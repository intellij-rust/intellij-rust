/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.statistics

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.fus.CidrProjectModelTypeProvider
import com.jetbrains.cidr.fus.CidrProjectModelTypeProvider.Type
import org.rust.cargo.runconfig.hasCargoProject

class CargoProjectModelTypeProvider : CidrProjectModelTypeProvider {
    override fun getTypes(project: Project): List<Type> {
        return if (project.hasCargoProject) listOf(Type.Cargo) else emptyList()
    }
}
