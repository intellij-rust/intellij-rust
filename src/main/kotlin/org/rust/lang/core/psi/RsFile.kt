/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.RsConstants
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.index.RsIncludeMacroIndex
import org.rust.lang.core.stubs.index.RsModulesIndex
import org.rust.openapiext.toPsiFile

/**
 * This class was added in order to fix [RsCodeFragment] copying inside
 * [com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate.copyFile]
 *
 * The problem was because old [RsFile.getOriginalFile] casted `super.getOriginalFile()` to [RsFile],
 * but after manual [RsCodeFragment] copying new [RsFile] has [RsCodeFragment] as original file.
 */
abstract class RsFileBase(fileViewProvider: FileViewProvider)
    : PsiFileBase(fileViewProvider, RsLanguage), RsInnerAttributeOwner {

    override fun getReference(): RsReference? = null

    override fun getOriginalFile(): RsFileBase = super.getOriginalFile() as RsFileBase

    override fun getFileType(): FileType = RsFileType

    override fun getStub(): RsFileStub? = super.getStub() as RsFileStub?

    override val innerAttrList: List<RsInnerAttr>
        get() = stubChildrenOfType()
}

class RsFile(
    fileViewProvider: FileViewProvider
) : RsFileBase(fileViewProvider), RsMod {

    override val containingMod: RsMod get() = getOriginalOrSelf()

    val cargoProject: CargoProject? get() = cachedData.cargoProject
    val cargoWorkspace: CargoWorkspace? get() = cachedData.cargoWorkspace
    val cargoTarget: CargoWorkspace.Target? get() = cachedData.cargoTarget
    override val crateRoot: RsMod? get() = cachedData.crateRoot

    private val cachedData: CachedData
        get() {
            // [RsModulesIndex.getDeclarationFor] behaves differently depending on whether macros are expanding
            val key = if (project.macroExpansionManager.isResolvingMacro) CACHED_DATA_MACROS_KEY else CACHED_DATA_KEY
            return CachedValuesManager.getCachedValue(this, key) {
                CachedValueProvider.Result(doGetCachedData(), rustStructureOrAnyPsiModificationTracker)
            }
        }

    private fun doGetCachedData(): CachedData {
        val possibleCrateRoot = superMods.lastOrNull() as? RsFile ?: return EMPTY_CACHED_DATA
        val possibleCrateRootOriginal = possibleCrateRoot.originalFile as? RsFile ?: return EMPTY_CACHED_DATA

        val includingMod = RsIncludeMacroIndex.getIncludingMod(possibleCrateRootOriginal)
        if (includingMod != null) return (includingMod.contextualFile as? RsFile)?.cachedData ?: EMPTY_CACHED_DATA

        val crateRootVFile = possibleCrateRootOriginal.virtualFile ?: return EMPTY_CACHED_DATA

        val injectedFromFile = crateRootVFile.getInjectedFromIfDoctestInjection(project)
        if (injectedFromFile != null) {
            val cached = injectedFromFile.cachedData
            // Doctest injection file should be a crate root to resolve absolute paths inside injection.
            // Doctest contains a single "extern crate $crateName;" declaration at the top level, so
            // we should be able to resolve it by an absolute path
            return cached.copy(crateRoot = possibleCrateRootOriginal)
        }

        val cargoProject = project.cargoProjects.findProjectForFile(crateRootVFile) ?: return EMPTY_CACHED_DATA
        val workspace = cargoProject.workspace ?: return CachedData(cargoProject)
        val cargoTarget = workspace.findTargetByCrateRoot(crateRootVFile)

        return if (cargoTarget != null) {
            // `possibleCrateRoot` is a "real" crate root only if we're able to find a target for it
            CachedData(cargoProject, workspace, cargoTarget, possibleCrateRoot)
        } else {
            // This accurs if the file is not included to the project's module structure, i.e. it's
            // most parent module is not mentioned in the `Cargo.toml` as a crate root of some target
            // (usually lib.rs or main.rs). It's anyway useful to know cargoProject&workspace in this case
            CachedData(cargoProject, workspace)
        }
    }

    override fun setName(name: String): PsiElement {
        val nameWithExtension = if ('.' !in name) "$name.rs" else name
        return super.setName(nameWithExtension)
    }

    override val `super`: RsMod?
        get() {
            val includingMod = RsIncludeMacroIndex.getIncludingMod(this) ?: return declaration?.containingMod
            return includingMod.`super`
        }

    // We can't just return file name here because
    // if mod declaration has `path` attribute file name differs from mod name
    override val modName: String? get() {
        return declaration?.name ?: if (name != RsConstants.MOD_RS_FILE) FileUtil.getNameWithoutExtension(name) else parent?.name
    }

    override val pathAttribute: String?
        get() = declaration?.pathAttribute

    override val crateRelativePath: String? get() = RsPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean
        get() = name == RsConstants.MOD_RS_FILE || isCrateRoot

    override val isCrateRoot: Boolean
        get() = originalFile == crateRoot

    override val visibility: RsVisibility get() {
        if (isCrateRoot) return RsVisibility.Public
        return declaration?.visibility ?: RsVisibility.Private
    }

    val attributes: Attributes
        get() {
            val stub = greenStub as RsFileStub?
            if (stub != null) return stub.attributes
            if (queryAttributes.hasAtomAttribute("no_core")) return Attributes.NO_CORE
            if (queryAttributes.hasAtomAttribute("no_std")) return Attributes.NO_STD
            return Attributes.NONE
        }

    val declaration: RsModDeclItem? get() {
        // XXX: without this we'll close over `thisFile`, and it's verboten
        // to store references to PSI inside `CachedValueProvider` other than
        // the key PSI element
        val originalFile = originalFile as? RsFile ?: return null
        // [RsModulesIndex.getDeclarationFor] behaves differently depending on whether macros are expanding
        val key = if (project.macroExpansionManager.isResolvingMacro) MOD_DECL_MACROS_KEY else MOD_DECL_KEY
        return CachedValuesManager.getCachedValue(originalFile, key) {
            CachedValueProvider.Result.create(
                RsModulesIndex.getDeclarationFor(originalFile),
                originalFile.rustStructureOrAnyPsiModificationTracker
            )
        }
    }

    enum class Attributes {
        NO_CORE, NO_STD, NONE
    }
}

private data class CachedData(
    val cargoProject: CargoProject? = null,
    val cargoWorkspace: CargoWorkspace? = null,
    val cargoTarget: CargoWorkspace.Target? = null,
    /**
     * May be not equal to [cargoTarget]'s [CargoWorkspace.Target.crateRoot].
     * For example, in the case of doctest injection
     */
    val crateRoot: RsFile? = null
)

private val EMPTY_CACHED_DATA: CachedData = CachedData()

private fun VirtualFile.getInjectedFromIfDoctestInjection(project: Project): RsFile? {
    if (!isDoctestInjection(project)) return null
    return ((this as? VirtualFileWindow)?.delegate?.toPsiFile(project) as? RsFile)
}

val PsiFile.rustFile: RsFile? get() = this as? RsFile

val VirtualFile.isNotRustFile: Boolean get() = !isRustFile
val VirtualFile.isRustFile: Boolean get() = fileType == RsFileType

private val MOD_DECL_KEY: Key<CachedValue<RsModDeclItem?>> = Key.create("MOD_DECL_KEY")
private val MOD_DECL_MACROS_KEY: Key<CachedValue<RsModDeclItem?>> = Key.create("MOD_DECL_MACROS_KEY")

private val CACHED_DATA_KEY: Key<CachedValue<CachedData>> = Key.create("CACHED_DATA_KEY")
private val CACHED_DATA_MACROS_KEY: Key<CachedValue<CachedData>> = Key.create("CACHED_DATA_MACROS_KEY")
