/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.impl

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.project.settings.rustSettings
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.execute
import org.rust.toml.crates.*
import java.nio.file.Path
import java.nio.file.Paths

class CrateResolverImpl(private val project: Project) : CrateResolverService {
    private val isAvailable = isGitAvailable()

    private val crateNameIndex: Map<String, CrateIndexEntry>? = getLocallyIndexedCrates(project)
    // TODO: LRU?
    private val crateCache: MutableMap<String, Crate?> = mutableMapOf()

    override fun isAvailable(): Boolean = isAvailable && crateNameIndex != null

    override fun searchCrates(name: String): Collection<String> {
        // TODO: optimize
        return crateNameIndex?.keys?.filter { name in it }.orEmpty()
    }

    override fun getCrate(name: String): Crate? {
        val cacheEntry = crateCache[name]
        if (cacheEntry != null) return cacheEntry

        val indexEntry = crateNameIndex?.get(name)
        val resolved = indexEntry?.let { getCrateFromIndex(project, indexEntry) }
        crateCache[name] = resolved
        return resolved
    }
}

private data class CrateIndexEntry(val path: String, val name: String)

private fun getCrateFromIndex(project: Project, entry: CrateIndexEntry): Crate? {
    val result = GeneralCommandLine(getGitExecutable())
        .withParameters("show", "origin/master:${entry.path}")
        .withWorkDirectory(cargoIndex(project))
        .execute(2000) ?: return null

    if (result.exitCode != 0) return null

    val versions = result.stdout.lineSequence()
        .filter { it.isNotBlank() }
        .map {
            val crateVersion = Gson().fromJson(it, GitCrateVersion::class.java)
            CrateVersion(parseSemver(crateVersion.version), crateVersion.yanked)
        }.toList()

    return Crate(entry.name, versions.findLast { !it.yanked && it.version != null }?.version, versions)
}

private data class GitCrateVersion(
    @SerializedName("vers")
    val version: String,
    val yanked: Boolean
)

private fun getLocallyIndexedCrates(project: Project): Map<String, CrateIndexEntry>? {
    val result = GeneralCommandLine(getGitExecutable())
        .withParameters("ls-tree", "--name-only", "-r", "origin/master")
        .withWorkDirectory(cargoIndex(project))
        .execute(5000) ?: return null

    if (result.exitCode != 0) return null

    return result.stdout.lineSequence().mapNotNull {
        val path = it
        val name = path.substringAfterLast("/")
        Pair(name, CrateIndexEntry(path, name))
    }.toMap()
}

private fun isGitAvailable(): Boolean {
    val result = GeneralCommandLine(getGitExecutable())
        .withParameters("--version")
        .execute(2000) ?: return false
    return result.exitCode == 0
}

private fun getGitExecutable(): Path = Paths.get("git")

private fun cargoHome(project: Project): String {
    return project.rustSettings.toolchain?.location?.parent?.toString()
        ?: System.getenv("CARGO_HOME")
        ?: "~/.cargo"
}

private fun cargoIndex(project: Project): String {
    return FileUtil.expandUserHome("${cargoHome(project)}/registry/index/github.com-1ecc6299db9ec823")
}
