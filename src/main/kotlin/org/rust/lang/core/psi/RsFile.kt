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
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.RsConstants
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.macros.MacroExpansionFileSystem
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve2.findModDataFor
import org.rust.lang.core.resolve2.toRsMod
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.index.RsIncludeMacroIndex
import org.rust.lang.core.stubs.index.RsModulesIndex
import org.rust.openapiext.recursionGuard
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
    val crate: Crate? get() = cachedData.crate
    override val crateRoot: RsMod? get() = cachedData.crateRoot
    val isDeeplyEnabledByCfg: Boolean get() = cachedData.isDeeplyEnabledByCfg
    // TODO a hotfix for https://github.com/intellij-rust/intellij-rust/issues/9110
    val isIncludedByIncludeMacro: Boolean get() = true
//    val isIncludedByIncludeMacro: Boolean get() = cachedData.isIncludedByIncludeMacro

    /** Used for in-memory macro expansions */
    @Volatile
    private var forcedCachedData: (() -> CachedData)? = null

    private val cachedData: CachedData
        get() {
            forcedCachedData?.let { return it() }

            val originalFile = originalFile
            if (originalFile != this) return (originalFile as? RsFile)?.cachedData ?: EMPTY_CACHED_DATA

            val key = CACHED_DATA_KEY
            return CachedValuesManager.getCachedValue(this, key) {
                val value = recursionGuard(Pair(key, this), { doGetCachedData() }) ?: EMPTY_CACHED_DATA
                // Note: if the cached result is invalidated, then the cached result from `memExpansionResult`
                // must also be invalidated, so keep them in sync
                CachedValueProvider.Result(value, rustStructureOrAnyPsiModificationTracker)
            }
        }

    private fun doGetCachedData(): CachedData {
        check(originalFile == this)

        val virtualFile: VirtualFile? = virtualFile

        if (virtualFile?.fileSystem is MacroExpansionFileSystem) {
            val crateId = project.macroExpansionManager.getCrateForExpansionFile(virtualFile)
                ?: return EMPTY_CACHED_DATA // Possibly an expansion file from another IDEA project
            val crate = project.crateGraph.findCrateById(crateId) ?: return EMPTY_CACHED_DATA
            return CachedData(
                crate.cargoProject,
                crate.cargoWorkspace,
                crate.rootMod,
                crate,
                isDeeplyEnabledByCfg = true, // Macros are ony expanded in cfg-enabled mods
                isIncludedByIncludeMacro = false // An expansion file obviously can't be included
            )
        }

        // Note: `this` file can be not a module (can be included with `include!()` macro)
        val modData = findModDataFor(this)
        if (modData != null) {
            val crate = project.crateGraph.findCrateById(modData.crate) ?: return EMPTY_CACHED_DATA
            return CachedData(
                crate.cargoProject,
                crate.cargoWorkspace,
                crate.rootMod,
                crate,
                modData.isDeeplyEnabledByCfg,
                isIncludedByIncludeMacro = virtualFile is VirtualFileWithId
                    && virtualFile.id != modData.fileId
                    && virtualFile.fileSystem !is MacroExpansionFileSystem
            )
        }
        // Else try injected crate, included file, or fill file info with just project and workspace

        // [ModData] may be not found because some [CrateDefMap]s are not up-to-date,
        // so we have to fallback to use [declaration]
        val declaration = declaration
        if (declaration != null) {
            val declarationFile = declaration.contextualFile
            val parentCachedData = (declarationFile as? RsFile)?.cachedData ?: return EMPTY_CACHED_DATA
            val isDeeplyEnabledByCfg = parentCachedData.isDeeplyEnabledByCfg
                && declaration.existsAfterExpansion
                && (parentCachedData.crate?.let { declarationFile.isEnabledByCfgSelf(it) } ?: true)
            return parentCachedData.copy(isDeeplyEnabledByCfg = isDeeplyEnabledByCfg)
        }

        val possibleCrateRoot = this
        val crateRootVFile = possibleCrateRoot.virtualFile ?: return EMPTY_CACHED_DATA

        val crate = project.crateGraph.findCrateByRootMod(crateRootVFile)
        if (crate != null) {
            // `possibleCrateRoot` is a "real" crate root only if we're able to find a `crate` for it
            val isEnabledByCfg = possibleCrateRoot.isEnabledByCfgSelf(crate)
            return CachedData(crate.cargoProject, crate.cargoWorkspace, possibleCrateRoot, crate, isEnabledByCfg)
        }

        val injectedFromFile = crateRootVFile.getInjectedFromIfDoctestInjection(project)
        if (injectedFromFile != null) {
            val cached = injectedFromFile.cachedData
            // Doctest injection file should be a crate root to resolve absolute paths inside injection.
            // Doctest contains a single "extern crate $crateName;" declaration at the top level, so
            // we should be able to resolve it by an absolute path
            val doctestCrate = cached.crate?.let { DoctestCrate.inCrate(it, possibleCrateRoot) }
            return cached.copy(crateRoot = possibleCrateRoot, crate = doctestCrate)
        }

        val includingMod = RsIncludeMacroIndex.getIncludedFrom(possibleCrateRoot)?.containingMod
        if (includingMod != null) {
            return (includingMod.contextualFile as? RsFile)?.cachedData?.copy(isIncludedByIncludeMacro = true)
                ?: EMPTY_CACHED_DATA
        }

        // This occurs if the file is not included to the project's module structure, i.e. it's
        // most parent module is not mentioned in the `Cargo.toml` as a crate root of some target
        // (usually lib.rs or main.rs). It's anyway useful to know cargoProject&workspace in this case
        val cargoProject = project.cargoProjects.findProjectForFile(crateRootVFile) ?: return EMPTY_CACHED_DATA
        val workspace = cargoProject.workspace ?: return CachedData(cargoProject)
        return CachedData(cargoProject, workspace)
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
            val modData = findModDataFor(this)
            if (modData != null) {
                val parenModData = modData.parent ?: return null
                return parenModData.toRsMod(project).firstOrNull()
            }

            val includedFrom = RsIncludeMacroIndex.getIncludedFrom(this) ?: return declaration?.containingMod
            return includedFrom.containingMod.`super`
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
    val crate: Crate? = null,
    val isDeeplyEnabledByCfg: Boolean = true,
    val isIncludedByIncludeMacro: Boolean = false
)

private val EMPTY_CACHED_DATA: CachedData = CachedData()

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

val RsElement.isValidProjectMemberAndContainingCrate: Pair<Boolean, Crate?>
    get() {
        val file = containingRsFileSkippingCodeFragments ?: return true to null
        if (!file.isDeeplyEnabledByCfg) return false to null
        val crate = file.crate ?: return false to null
        if (!existsAfterExpansion(crate)) return false to null

        return true to crate
    }

/** Usually used to filter out test/bench non-workspace crates */
fun shouldIndexFile(project: Project, file: VirtualFile): Boolean {
    val index = ProjectFileIndex.getInstance(project)
    return (index.isInContent(file) || index.isInLibrary(file))
        && !FileTypeManager.getInstance().isFileIgnored(file)
}
