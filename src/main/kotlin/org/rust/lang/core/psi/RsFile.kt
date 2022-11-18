/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.LightVirtualFile
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.RsConstants
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.crate.impl.FakeDetachedCrate
import org.rust.lang.core.crate.impl.FakeInvalidCrate
import org.rust.lang.core.macros.MacroExpansionFileSystem
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve2.*
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.index.RsModulesIndex
import org.rust.openapiext.toPsiFile

/**
 * This class was added in order to fix [RsCodeFragment] copying inside
 * [com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate.copyFile]
 *
 * The problem was because old [RsFile.getOriginalFile] casted `super.getOriginalFile()` to [RsFile],
 * but after manual [RsCodeFragment] copying new [RsFile] has [RsCodeFragment] as original file.
 */
abstract class RsFileBase(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, RsLanguage), RsInnerAttributeOwner {

    override fun getReference(): RsReference? = null

    override fun getOriginalFile(): RsFileBase = super.getOriginalFile() as RsFileBase

    override fun getFileType(): FileType = RsFileType

    override fun getStub(): RsFileStub? = super.getStub() as RsFileStub?
}

class RsFile(
    fileViewProvider: FileViewProvider
) : RsFileBase(fileViewProvider), RsMod {

    override val containingMod: RsMod get() = getOriginalOrSelf()

    val cargoProject: CargoProject? get() = cachedData.cargoProject
    val cargoWorkspace: CargoWorkspace? get() = cachedData.cargoWorkspace
    val crate: Crate get() = cachedData.crate
    val crates: List<Crate> get() = cachedData.crates
    override val crateRoot: RsMod? get() = cachedData.crateRoot
    val isDeeplyEnabledByCfg: Boolean get() = cachedData.isDeeplyEnabledByCfg
    val isIncludedByIncludeMacro: Boolean get() = cachedData.isIncludedByIncludeMacro

    /** Used for in-memory macro expansions */
    @Volatile
    private var forcedCachedData: (() -> CachedData)? = null
    private var hasForcedStubTree: Boolean = false

    private val cachedData: CachedData
        get() {
            forcedCachedData?.let { return it() }

            val originalFile = originalFile
            if (originalFile != this) {
                return (originalFile as? RsFile)?.cachedData
                    ?: CachedData(crate = FakeInvalidCrate(project))
            }

            val key = CACHED_DATA_KEY
            return CachedValuesManager.getCachedValue(this, key) {
                val value = doGetCachedData()
                // Note: if the cached result is invalidated, then the cached result from `memExpansionResult`
                // must also be invalidated, so keep them in sync
                val modificationTracker: Any = when {
                    /** See [rustStructureOrAnyPsiModificationTracker] */
                    virtualFile is VirtualFileWindow -> PsiModificationTracker.MODIFICATION_COUNT
                    value.crate.origin == PackageOrigin.WORKSPACE -> project.rustStructureModificationTracker
                    else -> project.rustPsiManager.rustStructureModificationTrackerInDependencies
                }
                CachedValueProvider.Result(value, modificationTracker)
            }
        }

    private fun doGetCachedData(): CachedData {
        check(originalFile == this)

        val virtualFile = virtualFile
            ?: return CachedData(crate = FakeDetachedCrate(this, id = -1, dependencies = emptyList()))

        if (virtualFile.fileSystem is MacroExpansionFileSystem) {
            val crateId = project.macroExpansionManager.getCrateForExpansionFile(virtualFile)
                ?: return CachedData(crate = FakeInvalidCrate(project)) // Possibly an expansion file from another IDEA project
            val crate = project.crateGraph.findCrateById(crateId)
                ?: return CachedData(crate = FakeInvalidCrate(project))
            return CachedData(
                crate.cargoProject,
                crate.cargoWorkspace,
                crate.rootMod,
                crate,
                crates = listOf(crate),
                isDeeplyEnabledByCfg = true, // Macros are ony expanded in cfg-enabled mods
                isIncludedByIncludeMacro = false, // An expansion file obviously can't be included
            )
        }

        // Note: `this` file can be not a module (can be included with `include!()` macro)
        val allInclusionPoints = findFileInclusionPointsFor(this)
        val inclusionPoint = allInclusionPoints.pickSingleInclusionPoint()
        if (inclusionPoint != null) {
            val crateGraph = project.crateGraph
            val crates = allInclusionPoints.mapNotNull { crateGraph.findCrateById(it.modData.crate) }
            val crate = crates.find { it.id == inclusionPoint.modData.crate }
                ?: return CachedData(crate = FakeInvalidCrate(project))
            return CachedData(
                crate.cargoProject,
                crate.cargoWorkspace,
                crate.rootMod,
                crate,
                crates,
                inclusionPoint.modData.isDeeplyEnabledByCfg,
                isIncludedByIncludeMacro = inclusionPoint.includeMacroIndex != null,
            )
        }

        val injectedFromFile = virtualFile.getInjectedFromIfDoctestInjection(project)
        if (injectedFromFile != null) {
            val cached = injectedFromFile.cachedData
            // Doctest injection file should be a crate root to resolve absolute paths inside injection.
            // Doctest contains a single "extern crate $crateName;" declaration at the top level, so
            // we should be able to resolve it by an absolute path
            val doctestCrate = DoctestCrate.inCrate(cached.crate, this)
            return cached.copy(
                crateRoot = this,
                crate = doctestCrate,
                isIncludedByIncludeMacro = false,
            )
        }

        // This occurs if the file is not included to the project's module structure, i.e. it's
        // most parent module is not mentioned in the `Cargo.toml` as a crate root of some target
        // (usually lib.rs or main.rs). It's anyway useful to know cargoProject&workspace in this case
        val stdlibCrates = project.crateGraph.topSortedCrates
            .filter { it.origin == PackageOrigin.STDLIB }
            .map { Crate.Dependency(it.normName, it) }
        val crate = FakeDetachedCrate(this, DefMapService.getNextNonCargoCrateId(), dependencies = stdlibCrates)

        val cargoProject = project.cargoProjects.findProjectForFile(virtualFile) ?: return CachedData(crate = crate)
        val workspace = cargoProject.workspace ?: return CachedData(cargoProject, crate =  crate)
        return CachedData(cargoProject, workspace, crate = crate)
    }

    /** Very internal utility, do not use */
    fun inheritCachedDataFrom(other: RsFile, lazy: Boolean) {
        forcedCachedData = if (lazy) {
            { other.cachedData }
        } else {
            val cachedData = other.cachedData
            { cachedData }
        }
    }

    override fun setName(name: String): PsiElement {
        if (this.name == RsConstants.MOD_RS_FILE) return this
        val nameWithExtension = if ('.' !in name) "$name.rs" else name
        return super.setName(nameWithExtension)
    }

    override val `super`: RsMod?
        get() {
            val modData = findFileInclusionPointsFor(this).pickSingleInclusionPoint()?.modData ?: return null
            val parenModData = modData.parent ?: return null
            return parenModData.toRsMod(project).firstOrNull()
        }

    // We can't just return file name here because
    // if mod declaration has `path` attribute file name differs from mod name
    override val modName: String?
        get() {
            return declaration?.name
                ?: if (name != RsConstants.MOD_RS_FILE) FileUtil.getNameWithoutExtension(name) else parent?.name
        }

    override val pathAttribute: String?
        get() = declaration?.pathAttribute

    override val crateRelativePath: String? get() = RsPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean
        get() = getOwnedDirectory() != null

    override val isCrateRoot: Boolean
        get() {
            val file = originalFile.virtualFile ?: return false
            return file is VirtualFileWithId && project.crateGraph.findCrateByRootMod(file) != null
                || file.isDoctestInjection(project)
        }

    override val visibility: RsVisibility
        get() {
            if (isCrateRoot) return RsVisibility.Public
            return declaration?.visibility ?: RsVisibility.Private
        }

    override val isPublic: Boolean
        get() {
            if (isCrateRoot) return true
            return declaration?.isPublic ?: false
        }

    val stdlibAttributes: Attributes
        get() = getStdlibAttributes(null)

    fun getStdlibAttributes(crate: Crate?): Attributes {
        val stub = greenStub as RsFileStub?
        if (stub?.mayHaveStdlibAttributes == false) return Attributes.NONE
        val attributes = getQueryAttributes(crate, stub)
        if (attributes.hasAtomAttribute("no_core")) return Attributes.NO_CORE
        if (attributes.hasAtomAttribute("no_std")) return Attributes.NO_STD
        return Attributes.NONE
    }

    /** ```#![macro_use]``` inside file (in contrast to ```#[macro_use]``` on mod declaration) */
    fun hasMacroUseInner(crate: Crate?): Boolean {
        val stub = greenStub as RsFileStub?
        if (stub?.mayHaveMacroUse == false) return false
        return getQueryAttributes(crate, stub).hasAtomAttribute("macro_use")
    }

    fun getRecursionLimit(crate: Crate?): Int {
        val stub = greenStub as RsFileStub?
        if (stub?.mayHaveRecursionLimitAttribute == false) return DEFAULT_RECURSION_LIMIT
        val attributes = getQueryAttributes(crate, stub)
        val recursionLimit = attributes.lookupStringValueForKey("recursion_limit")
        return recursionLimit?.toIntOrNull() ?: DEFAULT_RECURSION_LIMIT
    }

    val declaration: RsModDeclItem? get() = declarations.firstOrNull()

    val declarations: List<RsModDeclItem>
        get() {
            // XXX: without this we'll close over `thisFile`, and it's verboten
            // to store references to PSI inside `CachedValueProvider` other than
            // the key PSI element
            val originalFile = originalFile as? RsFile ?: return emptyList()

            return CachedValuesManager.getCachedValue(originalFile, MOD_DECL_KEY) {
                val decl = if (originalFile.isCrateRoot) emptyList() else RsModulesIndex.getDeclarationsFor(originalFile)
                CachedValueProvider.Result.create(decl, originalFile.rustStructureOrAnyPsiModificationTracker)
            }
        }

    /** Very internal utility, do not use it */
    fun forceSetStubTree(stub: PsiFileStub<*>) {
        check(virtualFile is LightVirtualFile)
        hasForcedStubTree = true
        if (!RsPsiFileInternals.setStubTree(this, stub)) {
            hasForcedStubTree = false
        }
    }

    override fun getTreeElement(): FileElement? {
        return if (hasForcedStubTree && !isContentsLoaded) {
            return null
        } else {
            super.getTreeElement()
        }
    }

    enum class Attributes {
        NO_CORE, NO_STD, NONE;

        fun getAutoInjectedCrate(): String? =
            when (this) {
                NONE -> STD
                NO_STD -> CORE
                NO_CORE -> null
            }

        fun canUseStdlibCrate(crateName: String): Boolean =
            when (this) {
                NONE -> true
                NO_STD -> crateName != STD
                NO_CORE -> crateName != STD && crateName != CORE
            }
    }
}

private data class CachedData(
    val cargoProject: CargoProject? = null,
    val cargoWorkspace: CargoWorkspace? = null,
    val crateRoot: RsFile? = null,
    val crate: Crate,
    val crates: List<Crate> = emptyList(),
    val isDeeplyEnabledByCfg: Boolean = true,
    val isIncludedByIncludeMacro: Boolean = false,
)

// A rust file can be included in multiple places, but currently IntelliJ Rust works properly only with one
// inclusion point, so we have to choose one
private fun List<FileInclusionPoint>.pickSingleInclusionPoint(): FileInclusionPoint? {
    if (isEmpty()) return null
    singleOrNull()?.let { return it }

    // If there are still multiple options, choose one deterministically
    return minByOrNull { it.modData.crate }
}

private fun VirtualFile.getInjectedFromIfDoctestInjection(project: Project): RsFile? {
    if (!isDoctestInjection(project)) return null
    return ((this as? VirtualFileWindow)?.delegate?.toPsiFile(project) as? RsFile)
}

val PsiFile.rustFile: RsFile? get() = this as? RsFile

val VirtualFile.isNotRustFile: Boolean get() = !isRustFile
val VirtualFile.isRustFile: Boolean get() = fileType == RsFileType

private val MOD_DECL_KEY: Key<CachedValue<List<RsModDeclItem>>> = Key.create("MOD_DECL_KEY")

private val CACHED_DATA_KEY: Key<CachedValue<CachedData>> = Key.create("CACHED_DATA_KEY")

/**
 * @return true if containing crate root is known for this element and this element is not excluded from
 * a project via `#[cfg]` attribute on some level (e.g. its parent module)
 */
@Suppress("KDocUnresolvedReference")
val RsElement.isValidProjectMember: Boolean
    get() = isValidProjectMemberAndContainingCrate.first

val RsElement.isValidProjectMemberAndContainingCrate: Triple<Boolean, Crate?, List<Crate>>
    get() {
        val file = containingRsFileSkippingCodeFragments ?: return Triple(true, null, emptyList())
        if (!file.isDeeplyEnabledByCfg) return Triple(false, null, emptyList())
        val crate = file.crate.asNotFake ?: return Triple(false, null, emptyList())
        if (!existsAfterExpansion(crate)) return Triple(false, null, emptyList())

        return Triple(true, crate, file.crates)
    }

/** Usually used to filter out test/bench non-workspace crates */
fun shouldIndexFile(project: Project, file: VirtualFile): Boolean {
    val index = ProjectFileIndex.getInstance(project)
    return (index.isInContent(file) || index.isInLibrary(file))
        && !FileTypeManager.getInstance().isFileIgnored(file)
}
