/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.index.RsModulesIndex

class RsFile(
    fileViewProvider: FileViewProvider
) : PsiFileBase(fileViewProvider, RsLanguage),
    RsMod,
    RsInnerAttributeOwner {

    override fun getReference(): RsReference? = null

    override val containingMod: RsMod get() = this

    override val crateRoot: RsMod?
        get() = superMods.lastOrNull()?.takeIf { it.isCrateRoot }

    override fun getFileType(): FileType = RsFileType

    override fun getStub(): RsFileStub? = super.getStub() as RsFileStub?

    override fun getOriginalFile(): RsFile = super.getOriginalFile() as RsFile

    override fun setName(name: String): PsiElement {
        val nameWithExtension = if ('.' !in name) "$name.rs" else name
        return super.setName(nameWithExtension)
    }

    override val `super`: RsMod? get() = declaration?.containingMod

    override val modName: String? = if (name != RsMod.MOD_RS) FileUtil.getNameWithoutExtension(name) else parent?.name

    override val crateRelativePath: String? get() = RsPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean
        get() = name == RsMod.MOD_RS || isCrateRoot

    override val ownedDirectory: PsiDirectory?
        get() = originalFile.parent

    override val isCrateRoot: Boolean
        get() {
            val file = originalFile.virtualFile ?: return false
            return cargoWorkspace?.isCrateRoot(file) ?: false
        }

    override val isPublic: Boolean get() {
        if (isCrateRoot) return true
        return declaration?.isPublic == true
    }

    override val innerAttrList: List<RsInnerAttr>
        get() = childrenOfType()

    val attributes: Attributes
        get() {
            val stub = stub
            if (stub != null) return stub.attributes
            if (queryAttributes.hasAtomAttribute("no_core")) return Attributes.NO_CORE
            if (queryAttributes.hasAtomAttribute("no_std")) return Attributes.NO_STD
            return Attributes.NONE
        }

    val declaration: RsModDeclItem? get() {
        // XXX: without this we'll close over `thisFile`, and it's verboten
        // to store references to PSI inside `CachedValueProvider` other than
        // the key PSI element
        val originalFile = originalFile
        return CachedValuesManager.getCachedValue(originalFile, {
            CachedValueProvider.Result.create(
                RsModulesIndex.getDeclarationFor(originalFile),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        })
    }

    enum class Attributes {
        NO_CORE, NO_STD, NONE
    }
}

val PsiFile.rustMod: RsMod? get() = this as? RsFile

val VirtualFile.isNotRustFile: Boolean get() = !isRustFile
val VirtualFile.isRustFile: Boolean get() = fileType == RsFileType


