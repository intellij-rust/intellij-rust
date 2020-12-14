/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.util.io.*
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.rust.openapiext.pluginDirInSystem
import org.rust.stdext.cleanDirectory
import org.rust.toml.crates.local.CratesLocalIndexService.Companion.CratesLocalIndexState
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Crates local index, created from user cargo registry index on host machine.
 * Used for dependency code insight in project's `Cargo.toml`.
 * Stores crates info in [crates] persistent hash map and hash for commit which has been used for index load in
 * persistent state [CratesLocalIndexState].
 */
@State(name = "CratesLocalIndexState", storages = [Storage("rust.crateslocalindex.xml")])
class CratesLocalIndexService : PersistentStateComponent<CratesLocalIndexState>, Disposable {
    private val userCargoIndexDir: Path
        get() = Paths.get(System.getProperty("user.home"), CARGO_REGISTRY_INDEX_LOCATION)

    // TODO: handle RepositoryNotFoundException
    private val repository: Repository = FileRepositoryBuilder()
        .setGitDir(userCargoIndexDir.toFile())
        .build()

    private val registryHeadCommitHash: String = repository.resolve("origin/master")?.name ?: run {
        LOG.error("Git revision string cannot be resolved to any object id")
        INVALID_COMMIT_HASH
    }

    private val crates: PersistentHashMap<String, CargoRegistryCrate>? = run {
        resetIndexIfNeeded()

        val file = baseCratesLocalRegistryDir.resolve("crates-local-index")
        try {
            IOUtil.openCleanOrResetBroken({
                PersistentHashMap(
                    file,
                    EnumeratorStringDescriptor.INSTANCE,
                    CrateExternalizer,
                    4 * 1024,
                    CRATES_INDEX_VERSION
                )
            }, file)
        } catch (e: IOException) {
            LOG.error("Cannot open or create PersistentHashMap in $file", e)
            null
        }
    }

    @Volatile
    private var state: CratesLocalIndexState = CratesLocalIndexState()

    private val isReady: AtomicBoolean = AtomicBoolean(true)

    override fun getState(): CratesLocalIndexState = state
    override fun loadState(state: CratesLocalIndexState) {
        this.state = state
    }

    fun isReady(): Boolean = isReady.get()

    fun getCrate(crateName: String): CargoRegistryCrate? {
        updateIfNeeded()
        if (crates == null) return null

        return try {
            crates.get(crateName)
        } catch (e: IOException) {
            LOG.error("Failed to get crate $crateName", e)
            null
        }
    }

    fun getAllCrateNames(): List<String> {
        updateIfNeeded()
        if (crates == null) return emptyList()

        val crateNames = mutableListOf<String>()

        try {
            crates.processKeys { name ->
                crateNames.add(name)
            }
        } catch (e: IOException) {
            LOG.error("Failed to get crate names", e)
        }

        return crateNames
    }

    private fun updateIfNeeded() {
        if (state.indexedCommitHash != registryHeadCommitHash && isReady.compareAndSet(true, false)) {
            CratesLocalIndexUpdateTask().queue()
        }
    }

    private fun resetIndexIfNeeded() {
        if (corruptionMarkerFile.exists()) {
            baseCratesLocalRegistryDir.cleanDirectory()
        }
    }

    fun invalidateCaches() {
        corruptionMarkerFile.apply {
            parent?.createDirectories()
            Files.createFile(this)
            state = CratesLocalIndexState(INVALID_COMMIT_HASH)
        }
    }

    private fun reloadCrates(revTree: RevTree) {
        TreeWalk(repository).use { treeWalk ->
            treeWalk.addTree(revTree)
            treeWalk.isRecursive = true
            treeWalk.isPostOrderTraversal = false

            while (treeWalk.next()) {
                if (treeWalk.pathString == "config.json") continue

                val objectId = treeWalk.getObjectId(0)
                val loader = repository.open(objectId)
                val versions = mutableListOf<CargoRegistryCrateVersion>()
                val reader = BufferedReader(InputStreamReader(loader.openStream()))

                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine

                    try {
                        versions.add(CargoRegistryCrateVersion.fromJson(line))
                    } catch (e: Exception) {
                        LOG.warn("Failed to parse JSON from ${treeWalk.pathString}, line ${line}: ${e.message}")
                    }
                }

                try {
                    crates?.put(treeWalk.nameString, CargoRegistryCrate(versions))
                } catch (e: IOException) {
                    LOG.error("Failed to put crate `${treeWalk.nameString}` into local index", e)
                }
            }
        }

        // Force to save everything on the disk
        try {
            crates?.force()
        } catch (e: IOException) {
            LOG.warn(e)
        }
    }

    override fun dispose() {
        try {
            crates?.close()
        } catch (e: IOException) {
            LOG.warn(e)
        }
    }

    private inner class CratesLocalIndexUpdateTask : Task.Backgroundable(null, "Loading cargo registry index", false) {
        override fun run(indicator: ProgressIndicator) {
            val branch = repository.resolve("origin/master") ?: return
            val revCommit = RevWalk(repository).parseCommit(ObjectId.fromString(branch.name))
            reloadCrates(revCommit.tree)
        }

        override fun onSuccess() {
            state = CratesLocalIndexState(registryHeadCommitHash)
        }

        override fun onFinished() {
            isReady.set(true)
        }
    }

    companion object {
        data class CratesLocalIndexState(var indexedCommitHash: String = "")

        private val corruptionMarkerFile: Path
            get() = baseCratesLocalRegistryDir.resolve(CORRUPTION_MARKER_NAME)

        private val baseCratesLocalRegistryDir: Path
            get() = pluginDirInSystem().resolve("crates-local-index")

        // TODO: Determine how path to index is created
        private const val CARGO_REGISTRY_INDEX_LOCATION: String = ".cargo/registry/index/github.com-1ecc6299db9ec823/.git/"
        private const val CORRUPTION_MARKER_NAME: String = "corruption.marker"
        private const val INVALID_COMMIT_HASH: String = "<invalid>"
        private const val CRATES_INDEX_VERSION: Int = 0
        private val LOG: Logger = logger<CratesLocalIndexService>()
    }
}

data class CargoRegistryCrate(val versions: List<CargoRegistryCrateVersion>)
data class CargoRegistryCrateVersion(val version: String, val isYanked: Boolean, val features: List<String>) {
    companion object {
        private data class ParsedVersion(
            val name: String,
            val vers: String,
            val yanked: Boolean,
            val features: HashMap<String, List<String>>
        )

        fun fromJson(json: String): CargoRegistryCrateVersion {
            val parsedVersion = Gson().fromJson(json, ParsedVersion::class.java)

            return CargoRegistryCrateVersion(
                parsedVersion.vers,
                parsedVersion.yanked,
                parsedVersion.features.map { it.key }
            )
        }
    }
}

val CargoRegistryCrate.lastVersion: String?
    // TODO: Last version sometimes can differ from latest major
    //  (e.g. if developer uploaded a patch to previous major)
    get() = versions.lastOrNull()?.version

private object CrateExternalizer : DataExternalizer<CargoRegistryCrate> {
    override fun save(out: DataOutput, value: CargoRegistryCrate) {
        out.writeInt(value.versions.size)
        value.versions.forEach { version ->
            out.writeUTF(version.version)
            out.writeBoolean(version.isYanked)

            out.writeInt(version.features.size)
            version.features.forEach { feature ->
                out.writeUTF(feature)
            }
        }
    }

    override fun read(inp: DataInput): CargoRegistryCrate {
        val versions = mutableListOf<CargoRegistryCrateVersion>()
        val versionsSize = inp.readInt()
        repeat(versionsSize) {
            val version = inp.readUTF()
            val yanked = inp.readBoolean()

            val features = mutableListOf<String>()
            val featuresSize = inp.readInt()
            repeat(featuresSize) {
                features.add(inp.readUTF())
            }
            versions.add(CargoRegistryCrateVersion(version, yanked, features))
        }
        return CargoRegistryCrate(versions)
    }
}
