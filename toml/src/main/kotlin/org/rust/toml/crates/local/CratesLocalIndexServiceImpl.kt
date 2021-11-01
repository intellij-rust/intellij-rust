/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.impl.source.tree.injected.changesHandler.debug
import com.intellij.util.EnvironmentUtil
import com.intellij.util.io.*
import com.intellij.util.ui.UIUtil
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.errors.RevisionSyntaxException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryCache
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.OrTreeFilter
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.filter.TreeFilter
import org.jetbrains.annotations.TestOnly
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.checkIsBackgroundThread
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.toThreadSafeProgressIndicator
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.cleanDirectory
import org.rust.stdext.supplyAsync
import org.rust.toml.crates.local.CratesLocalIndexService.Error
import org.rust.util.RsBackgroundTaskQueue
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors


/**
 * Crates local index, created from user cargo registry index on host machine.
 * Used for dependency code insight in project's `Cargo.toml`
 */
class CratesLocalIndexServiceImpl : CratesLocalIndexService, Disposable {

    private val queue: RsBackgroundTaskQueue = RsBackgroundTaskQueue()

    private val innerStateLock: Any = Any()

    // Writing is guarded by `innerStateLock`
    @Volatile
    private var innerState: InnerState = InnerState.Loading

    /** [isUpdating] is true when index is performing [CratesLocalIndexUpdateTask] */
    private val isUpdating: Boolean
        get() = updateTaskCount.get() != 0

    private val updateTaskCount = AtomicInteger(0)

    private sealed class InnerState {
        object Loading : InnerState() {
            override fun toString(): String = "InnerState.Loading"
        }

        data class Loaded(val inner: CratesLocalIndexServiceImplInner) : InnerState()

        data class Err(val err: Error.InternalError) : InnerState()

        object Disposed : InnerState() {
            override fun toString(): String = "InnerState.Disposed"
        }
    }

    /**
     * Please, avoid heavy operations in [action]
     *
     * @return new state or `null` if the state is not changed
     */
    private fun stateTransition(action: (InnerState) -> InnerState): InnerState? {
        return synchronized(innerStateLock) {
            val oldState = innerState
            val newState = action(oldState)
            innerState = newState
            if (newState != oldState) {
                LOG.debug { "CratesLocalIndexService state transition $oldState -> $newState" }
                newState
            } else {
                null
            }
        }
    }

    init {
        loadAsync()
    }

    private fun loadAsync() {
        LOG.debug("Loading CratesLocalIndexService")
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                resetIndexIfNeeded()
            } catch (e: IOException) {
                LOG.error(e)
            }

            val inner = CratesLocalIndexServiceImplInner.tryCreate(
                cargoRegistryIndexPath,
                baseCratesLocalIndexDir
            )

            stateTransition { oldState ->
                when (oldState) {
                    InnerState.Loading -> when (inner) {
                        is Ok -> {
                            updateIndex(inner.ok)
                            InnerState.Loaded(inner.ok)
                        }
                        is Err -> InnerState.Err(inner.err)
                    }
                    is InnerState.Loaded, is InnerState.Err -> error("unreachable")
                    InnerState.Disposed -> {
                        inner.ok()?.close()
                        InnerState.Disposed
                    }
                }
            }

            LOG.debug("Loading CratesLocalIndexService finished")
        }
    }

    override fun getCrate(crateName: String): RsResult<CargoRegistryCrate?, Error> =
        handleErrors { it.getCrate(crateName) }

    override fun getAllCrateNames(): RsResult<List<String>, Error> =
        handleErrors(CratesLocalIndexServiceImplInner::getAllCrateNames)

    private fun <T> handleErrors(action: (CratesLocalIndexServiceImplInner) -> T): RsResult<T, Error> {
        if (isUpdating) {
            return Err(Error.Updating)
        }
        return when (val innerState = innerState) {
            InnerState.Loading -> Err(Error.NotYetLoaded)
            is InnerState.Loaded -> try {
                Ok(action(innerState.inner))
            } catch (e: IOException) {
                Err(onPersistentHashMapError(innerState, e))
            }
            is InnerState.Err -> Err(innerState.err)
            is InnerState.Disposed -> Err(Error.Disposed)
        }
    }

    private fun onPersistentHashMapError(lastInnerState: InnerState.Loaded, e: IOException): Error.InternalError {
        val err = Error.InternalError.PersistentHashMapReadError(e.toString())
        stateTransition { oldState ->
            if (oldState == lastInnerState) {
                lastInnerState.inner.close()
                InnerState.Err(err)
            } else {
                oldState
            }
        }
        LOG.warn(e)
        return err
    }

    fun recoverIfNeeded() {
        val newState = stateTransition { oldState ->
            when (oldState) {
                is InnerState.Err -> InnerState.Loading
                else -> oldState
            }
        }

        if (newState != null) {
            loadAsync()
        }
    }

    fun hasInterestingEvent(events: List<VFileEvent>): Boolean {
        val innerState = innerState
        if (innerState !is InnerState.Loaded) return false

        val cargoRegistryIndexRefsLocation = innerState.inner.cargoRegistryIndexRefsLocation
        return events.any { it.path.startsWith(cargoRegistryIndexRefsLocation) }
    }

    fun updateIndex() {
        val innerState = innerState
        if (innerState !is InnerState.Loaded) return

        updateIndex(innerState.inner)
    }

    private fun updateIndex(inner: CratesLocalIndexServiceImplInner) {
        updateTaskCount.incrementAndGet()
        queue.run(inner.createUpdateTask(this::updateTaskFailed, this::updateTaskFinished))
    }

    override fun dispose() {
        queue.dispose()
        stateTransition { oldState ->
            if (oldState is InnerState.Loaded) {
                oldState.inner.close()
            }
            InnerState.Disposed
        }
    }

    // Called from a background thread
    private fun updateTaskFailed(inner: CratesLocalIndexServiceImplInner, err: Error.InternalError) {
        stateTransition { oldState ->
            if (oldState is InnerState.Loaded && oldState.inner == inner) {
                InnerState.Err(err)
            } else {
                oldState
            }
        }
    }

    // Called from EDT
    private fun updateTaskFinished() {
        // `runWriteAction` is needed to restart inspections that depends on this index
        runWriteAction {
            updateTaskCount.decrementAndGet()
        }
    }

    @TestOnly
    fun awaitLoadedAndUpdated() {
        while (innerState is InnerState.Loading || isUpdating) {
            Thread.sleep(10)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    companion object {
        private val baseCratesLocalIndexDir: Path
            get() = RsPathManager.pluginDirInSystem().resolve("crates-local-index")

        private val corruptionMarkerFile: Path
            get() = baseCratesLocalIndexDir.resolve(CORRUPTION_MARKER_NAME)

        private val cargoHome: String
            get() = EnvironmentUtil.getValue("CARGO_HOME")
                ?: Paths.get(System.getProperty("user.home"), ".cargo/").toString()

        // Currently, for crates.io only
        private val cargoRegistryIndexPath: Path
            get() = Paths.get(cargoHome, "registry/index/", CRATES_IO_HASH, ".git/")

        // Crates.io index hash is permanent.
        // See https://github.com/rust-lang/cargo/issues/8572
        private const val CRATES_IO_HASH = "github.com-1ecc6299db9ec823"


        private const val CORRUPTION_MARKER_NAME: String = "corruption.marker"

        // Must not load the service!
        @JvmStatic
        @Throws(IOException::class)
        fun invalidateCaches() {
            corruptionMarkerFile.apply {
                parent?.createDirectories()
                Files.createFile(this)
            }
        }

        @Throws(IOException::class)
        fun resetIndexIfNeeded() {
            if (corruptionMarkerFile.exists()) {
                baseCratesLocalIndexDir.cleanDirectory()
            }
        }
    }
}

private class CratesLocalIndexServiceImplInner(
    private val cargoRegistryIndexPath: Path,
    private val crates: PersistentHashMap<String, CargoRegistryCrate>,
    val cargoRegistryIndexRefsLocation: String,
    private val cargoRegistryIndexRefsWatchRequest: LocalFileSystem.WatchRequest?,
    private val indexedCommitHashFile: Path,
    // Read/mutated only within `CratesLocalIndexUpdateTask`, there isn't concurrent access
    private var indexedCommitHash: String,
) {
    private val isClosedLock: Any = Any()
    // Guarded by `isClosedLock`
    private var isClosed: Boolean = false

    @Throws(IOException::class)
    fun getCrate(crateName: String): CargoRegistryCrate? {
        return crates.get(crateName) // throws IOException
    }

    @Throws(IOException::class)
    fun getAllCrateNames(): List<String> {
        val crateNames = mutableListOf<String>()

        // throws IOException
        crates.processKeys { name ->
            crateNames.add(name)
        }

        return crateNames
    }

    @Throws(IOException::class)
    private fun writeCratesUpdate(update: CratesUpdate) {
        synchronized(isClosedLock) {
            if (isClosed) return
            // An extra protection from a crash
            writeCommitHash(indexedCommitHashFile, INVALID_COMMIT_HASH)
        }

        for ((name, crate) in update.updatedCrates) {
            crates.put(name, crate)
        }

        crates.force() // Force to save everything on the disk

        synchronized(isClosedLock) {
            if (isClosed) return
            // Write the new hash only if the writing to PersistentHashMap succeed
            writeCommitHash(indexedCommitHashFile, update.newHeadHash)
            indexedCommitHash = update.newHeadHash
        }
    }

    fun createUpdateTask(onError: (CratesLocalIndexServiceImplInner, Error.InternalError) -> Unit, onFinish: () -> Unit) =
        CratesLocalIndexUpdateTask(
            cargoRegistryIndexPath,
            { indexedCommitHash },
            this::writeCratesUpdate,
            { onError(this, it) },
            onFinish
        )

    fun close() {
        synchronized(isClosedLock) {
            isClosed = true
        }
        cargoRegistryIndexRefsWatchRequest?.let { LocalFileSystem.getInstance().removeWatchedRoot(it) }
        catchAndWarn(crates::close)
    }

    companion object {
        fun tryCreate(
            cargoRegistryIndexPath: Path,
            baseCratesLocalRegistryDir: Path,
        ): RsResult<CratesLocalIndexServiceImplInner, Error.InternalError> {
            if (!isUnitTestMode) {
                checkIsBackgroundThread()
            }

            val cargoRegistryIndexRefsPath = cargoRegistryIndexPath.resolve("refs")

            if (!cargoRegistryIndexPath.exists() || !cargoRegistryIndexRefsPath.exists()) {
                return Err(Error.InternalError.NoCargoIndex(cargoRegistryIndexPath))
            }

            val cargoRegistryIndexRefsLocation = cargoRegistryIndexRefsPath.toString()
            val cargoRegistryIndexRefsVFile =
                LocalFileSystem.getInstance().refreshAndFindFileByPath(cargoRegistryIndexRefsLocation)

            if (cargoRegistryIndexRefsVFile == null) {
                LOG.error("Failed to subscribe to cargo registry changes in $cargoRegistryIndexRefsLocation")
                return Err(Error.InternalError.NoCargoIndex(cargoRegistryIndexRefsPath))
            }

            val indexedCommitHashFile = baseCratesLocalRegistryDir.resolve("indexed-commit-hash")
            var indexedCommitHash = readCommitHash(indexedCommitHashFile)

            if (indexedCommitHash == INVALID_COMMIT_HASH && baseCratesLocalRegistryDir.exists()) {
                try {
                    baseCratesLocalRegistryDir.cleanDirectory() // throws IOException
                } catch (e: IOException) {
                    LOG.error("Cannot clean directory $baseCratesLocalRegistryDir", e)
                }
            }

            val cratesFilePath = baseCratesLocalRegistryDir.resolve("crates-local-index")
            val crates: PersistentHashMap<String, CargoRegistryCrate> = try {
                IOUtil.openCleanOrResetBroken({
                    PersistentHashMap(
                        cratesFilePath,
                        EnumeratorStringDescriptor.INSTANCE,
                        CrateExternalizer,
                        4 * 1024,
                        CRATES_INDEX_VERSION
                    )
                }, {
                    baseCratesLocalRegistryDir.cleanDirectory() // Also deletes `indexedCommitHashFile`
                    indexedCommitHash = INVALID_COMMIT_HASH
                })
            } catch (e: IOException) {
                LOG.error("Cannot open or create PersistentHashMap in $cratesFilePath", e)
                return Err(Error.InternalError.PersistentHashMapInitError(cratesFilePath, e.toString()))
            }

            val watchRequest = LocalFileSystem.getInstance().addRootToWatch(cargoRegistryIndexRefsLocation, true)

            // VFS fills up lazily, therefore we need to explicitly add root directory and go through children
            VfsUtilCore.processFilesRecursively(cargoRegistryIndexRefsVFile) { true }
            RefreshQueue.getInstance().refresh(true, true, null, cargoRegistryIndexRefsVFile)

            val inner = CratesLocalIndexServiceImplInner(
                cargoRegistryIndexPath,
                crates,
                cargoRegistryIndexRefsLocation,
                watchRequest,
                indexedCommitHashFile,
                indexedCommitHash
            )
            return Ok(inner)
        }

        private fun readCommitHash(indexedCommitHashFile: Path): String {
            return if (indexedCommitHashFile.exists()) {
                try {
                    Files.readString(indexedCommitHashFile)
                } catch (e: IOException) {
                    LOG.warn("Cannot read file $indexedCommitHashFile", e)
                    INVALID_COMMIT_HASH
                }
            } else {
                INVALID_COMMIT_HASH
            }
        }

        @Throws(IOException::class)
        private fun writeCommitHash(indexedCommitHashFile: Path, hash: String) {
            Files.writeString(indexedCommitHashFile, hash)
        }

        @JvmStatic
        private fun catchAndWarn(runnable: () -> Unit) {
            try {
                runnable()
            } catch (e: IOException) {
                LOG.warn(e)
            } catch (t: Throwable) {
                LOG.error(t)
            }
        }
    }
}

private data class CratesUpdate(
    val updatedCrates: List<Pair<String, CargoRegistryCrate>>,
    val newHeadHash: String
)

private class CratesLocalIndexUpdateTask(
    private val cargoRegistryIndexPath: Path,
    private val indexedCommitHashGetter: () -> String,
    private val writeCratesUpdate: (CratesUpdate) -> Unit,
    private val onError: (Error.InternalError) -> Unit,
    private val onFinish: () -> Unit
) : Task.Backgroundable(null, "Loading cargo registry index", false) {
    override fun run(indicator: ProgressIndicator) {
        val builder = FileRepositoryBuilder()
            .setGitDir(cargoRegistryIndexPath.toFile())

        val update = try {
            val repository = builder.build() // throws IOException

            try {
                val indexedCommitHash = indexedCommitHashGetter()
                val newHead = readRegistryHeadCommitHash(repository) // throws IOException
                if (newHead == indexedCommitHash) return

                indicator.checkCanceled()

                val crateList = readNewCrates(indicator, repository, newHead, indexedCommitHash) // throws IOException
                CratesUpdate(crateList, newHead)
            } finally {
                // Ensure `repository` is really closed and all file descriptors are closed
                RepositoryCache.unregister(repository)
                repository.close()
            }
        } catch (e: RepositoryNotFoundException) {
            LOG.warn(e)
            onError(Error.InternalError.NoCargoIndex(cargoRegistryIndexPath))
            return
        } catch (e: IOException) {
            onError(Error.InternalError.RepoReadError(cargoRegistryIndexPath, e.toString()))
            LOG.warn(e)
            return
        }

        if (update.updatedCrates.isNotEmpty()) {
            ProgressManager.getInstance().executeNonCancelableSection {
                try {
                    writeCratesUpdate(update)
                } catch (e: IOException) {
                    LOG.warn(e)
                    onError(Error.InternalError.PersistentHashMapWriteError(e.toString()))
                }
            }
        }
    }

    // Always called on EDT
    override fun onFinished() {
        onFinish()
    }

    @Throws(IOException::class, RevisionSyntaxException::class)
    private fun readRegistryHeadCommitHash(repository: Repository): String {
        // BACKCOMPAT: Rust 1.49
        // Since 1.50 there should always be CARGO_REGISTRY_INDEX_TAG
        val objectId = repository.resolve(CARGO_REGISTRY_INDEX_TAG)
            ?: repository.resolve(CARGO_REGISTRY_INDEX_TAG_PRE_1_50) // throws IOException

        return objectId?.name ?: run {
            LOG.error("Failed to resolve remote branch in the cargo registry index repository")
            INVALID_COMMIT_HASH
        }
    }

    @Throws(IOException::class)
    private fun readNewCrates(
        indicator: ProgressIndicator,
        repository: Repository,
        newHeadHash: String,
        prevHeadHash: String
    ): List<Pair<String, CargoRegistryCrate>> {
        val reader = repository.newObjectReader()
        val currentTreeIter = CanonicalTreeParser().apply {
            val currentHeadTree = repository.resolve("$newHeadHash^{tree}") ?: run {
                LOG.error("Git revision `$newHeadHash^{tree}` cannot be resolved to any object id")
                return emptyList()
            }
            reset(reader, currentHeadTree)
        }

        val filter = run {
            val prevHeadTree = repository.resolve("$prevHeadHash^{tree}") ?: return@run TreeFilter.ALL

            val prevTreeIter = CanonicalTreeParser().apply {
                reset(reader, prevHeadTree)
            }

            val git = Git(repository)

            val changes = try {
                git.diff()
                    .setNewTree(currentTreeIter)
                    .setOldTree(prevTreeIter)
                    .call()
            } catch (e: GitAPIException) {
                LOG.error("Failed to calculate diff due to Git API error: ${e.message}")
                return@run TreeFilter.ALL
            }

            when (changes.size) {
                0 -> TreeFilter.ALL
                1 -> PathFilter.create(changes.single().newPath)
                else -> OrTreeFilter.create(changes.map { PathFilter.create(it.newPath) })
            }
        }

        val revTree = RevWalk(repository).parseCommit(ObjectId.fromString(newHeadHash)).tree
        val mapper = JsonMapper()
            .registerKotlinModule()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        val objectIds = TreeWalk(repository).use { treeWalk ->
            treeWalk.addTree(revTree)
            treeWalk.filter = filter
            treeWalk.isRecursive = true
            treeWalk.isPostOrderTraversal = false

            val objectIds = mutableListOf<Pair<ObjectId, String>>()
            while (treeWalk.next()) {
                if (treeWalk.isSubtree) continue
                val nameString = treeWalk.nameString
                if (nameString == "config.json") continue
                indicator.checkCanceled()

                val objectId = treeWalk.getObjectId(0)
                objectIds.add(objectId to nameString)
            }
            objectIds
        }

        val pool = Executors.newWorkStealingPool(2)
        val threadSafeIndicator = indicator.toThreadSafeProgressIndicator()
        val future = supplyAsync(pool) {
            objectIds
                .parallelStream()
                .map { (objectId, name) ->
                    threadSafeIndicator.checkCanceled()
                    val loader = repository.open(objectId)
                    val versions = mutableListOf<CargoRegistryCrateVersion>()
                    val fileReader = loader.openStream().bufferedReader(Charsets.UTF_8)

                    fileReader.forEachLine { line ->
                        if (line.isBlank()) return@forEachLine

                        try {
                            versions.add(crateFromJson(line, mapper))
                        } catch (e: Exception) {
                            LOG.warn("Failed to parse JSON for crate $name, line $line", e)
                        }
                    }
                    name to CargoRegistryCrate(versions)
                }
                .collect(Collectors.toList())
        }

        return try {
            future.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        } finally {
            pool.shutdownNow()
        }
    }
}


private val LOG: Logger = logger<CratesLocalIndexServiceImpl>()
private const val CARGO_REGISTRY_INDEX_TAG_PRE_1_50: String = "origin/master"
private const val CARGO_REGISTRY_INDEX_TAG: String = "origin/HEAD"
private const val INVALID_COMMIT_HASH: String = "<invalid>"
private const val CRATES_INDEX_VERSION: Int = 1

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

data class ParsedVersion(
    val name: String,
    val vers: String,
    val yanked: Boolean,
    val features: HashMap<String, List<String>>
)

private fun crateFromJson(json: String, mapper: ObjectMapper): CargoRegistryCrateVersion {
    val parsedVersion = mapper.readValue<ParsedVersion>(json)

    return CargoRegistryCrateVersion(
        parsedVersion.vers,
        parsedVersion.yanked,
        parsedVersion.features.map { it.key }
    )
}
