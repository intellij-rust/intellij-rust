/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

/**
 * IDEA side of Cargo package from crates.io
 */
class CargoLibrary(
    val root: VirtualFile,
    private val name: String,
    private val isStd: Boolean
) : SyntheticLibrary(), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)
    override fun equals(other: Any?): Boolean = other is CargoLibrary && other.root == root
    override fun hashCode(): Int = root.hashCode()

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon? = if (isStd) RsIcons.RUST else CargoIcons.ICON

    override fun getPresentableText(): String? = name
}


class RsAdditionalLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<CargoLibrary> =
        // BACKCOMPAT 2017.3: add `checkReadAccessAllowed()` here.
        // This *should* use read action, but it does not do it always.
        // We don't launch read action ourselves though, because we use
        // only safe operations. Fingers crossed.
        project.cargoProjects.allProjects
            .mapNotNull { it.workspace }
            .smartFlatMap { it.ideaLibraries }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        getAdditionalProjectLibraries(project).map { it.root }
}

private fun <U, V> Collection<U>.smartFlatMap(transform: (U) -> Collection<V>): Collection<V> =
    when (size) {
        0 -> emptyList()
        1 -> transform(first())
        else -> this.flatMap(transform)
    }

private val CargoWorkspace.ideaLibraries: Collection<CargoLibrary>
    get() = packages.filter { it.origin != PackageOrigin.WORKSPACE }
        .mapNotNull { pkg ->
            val root = pkg.contentRoot ?: return@mapNotNull null
            CargoLibrary(root, pkg.name, pkg.origin == PackageOrigin.STDLIB)
        }

