/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.toPsiFile
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CratesLocalIndexService
import org.toml.ide.intentions.TomlElementBaseIntentionAction
import org.toml.lang.psi.*

class AddCrateDependencyIntention : TomlElementBaseIntentionAction<Context>() {
    override fun getText(): String = RsBundle.message("intention.name.add.crate.to.dependencies")
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        if (!isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) return null

        val externCrateItem = element.parentOfType<RsExternCrateItem>(true)
        val path = element.parentOfType<RsPath>(true)

        val target: RsElement = when {
            externCrateItem != null -> {
                if (externCrateItem.reference.multiResolve().isNotEmpty()) return null
                externCrateItem
            }
            path != null -> {
                if (path.reference == null) return null
                if (path.parentOfType<RsUseItem>() == null) return null

                val isPathUnresolved = path.resolveStatus != PathResolveStatus.RESOLVED
                if (!isPathUnresolved) return null

                val qualifier = path.qualifier
                if (qualifier != null) return null
                path
            }
            else -> null
        } ?: return null

        if (target.containingCrate.origin != PackageOrigin.WORKSPACE) return null

        val (crateName, crate) = getAvailableCrate(target) ?: return null
        return Context(target, crateName, crate)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val element = ctx.element
        val pkg = element.containingCargoPackage ?: return
        val manifest = pkg.contentRoot?.findChild("Cargo.toml") ?: return
        val tomlManifest = manifest.toPsiFile(project) as? TomlFile ?: return

        val version = ctx.crate.sortedVersions.reversed().firstOrNull { !it.isYanked } ?: return

        val factory = TomlPsiFactory(project)
        val dependencies = findOrCreateDependencies(tomlManifest, factory)

        val dependency = factory.createKeyValue(ctx.crateName, "\"${version.version}\"")

        val lastChild = dependencies.entries.lastOrNull()
        val inserted = if (lastChild != null) {
            dependencies.addAfter(dependency, lastChild)
        } else {
            dependencies.add(dependency)
        }
        dependencies.addBefore(factory.createNewline(), inserted)

        tomlManifest.navigate(true)
    }
}

data class Context(val element: RsElement, val crateName: String, val crate: CargoRegistryCrate)

private fun getAvailableCrate(element: PsiElement): Pair<String, CargoRegistryCrate>? {
    val crateName = when (element) {
        is RsPath -> {
            val path = element.basePath()
            path.referenceName
        }
        is RsExternCrateItem -> element.identifier?.text
        else -> null
    } ?: return null

    val crate = CratesLocalIndexService.getInstance().getCrate(crateName).ok() ?: return null
    return crateName to crate
}

private fun findOrCreateDependencies(manifest: TomlFile, factory: TomlPsiFactory): TomlTable {
    val tables = mutableMapOf<String, MutableList<TomlTable>>()
    val visitor = object : TomlVisitor() {
        override fun visitTable(element: TomlTable) {
            val key = element.header.key?.segments?.getOrNull(0)?.name ?: return
            tables.getOrPut(key) { mutableListOf() }.add(element)
        }
    }
    manifest.acceptChildren(visitor)

    val dependencyTable = tables["dependencies"]?.getOrNull(0)
    if (dependencyTable != null) return dependencyTable

    val anchor = tables["package"]?.getOrNull(0)
    val newTable = factory.createTable("dependencies")

    return if (anchor != null) {
        val table = manifest.addAfter(newTable, anchor)
        val whitespace = factory.createWhitespace("\n\n")
        manifest.addBefore(whitespace, table)
        table
    } else {
        manifest.add(newTable)
    } as TomlTable
}
