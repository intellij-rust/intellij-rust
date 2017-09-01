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
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.*
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

    override fun getFileType(): FileType = RsFileType

    override fun getStub(): RsFileStub? = super.getStub() as RsFileStub?

    override fun getOriginalFile(): RsFile = super.getOriginalFile() as RsFile

    override fun setName(name: String): PsiElement {
        val nameWithExtension = if ('.' !in name) "$name.rs" else name
        return super.setName(nameWithExtension)
    }

    override val `super`: RsMod?
        get() {
            // XXX: without this we'll close over `thisFile`, and it's verboten
            // to store references to PSI inside `CachedValueProvider` other than
            // the key PSI element
            val originalFile = originalFile
            return CachedValuesManager.getCachedValue(originalFile, CachedValueProvider {
                com.intellij.psi.util.CachedValueProvider.Result.create(
                    RsModulesIndex.getSuperFor(originalFile),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            })
        }

    override val modName: String? = if (name != RsMod.MOD_RS) FileUtil.getNameWithoutExtension(name) else parent?.name

    override val crateRelativePath: String? get() = RsPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean
        get() = name == RsMod.MOD_RS || isCrateRoot

    override val ownedDirectory: PsiDirectory?
        get() = originalFile.parent

    override val isCrateRoot: Boolean get() {
        val file = originalFile.virtualFile ?: return false
        return cargoWorkspace?.isCrateRoot(file) ?: false
    }

    override val innerAttrList: List<RsInnerAttr>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RsInnerAttr::class.java)

    val attributes: Attributes get() {
        val stub = stub
        if (stub != null) return stub.attributes
        if (queryAttributes.hasAtomAttribute("no_core")) return Attributes.NO_CORE
        if (queryAttributes.hasAtomAttribute("no_std")) return Attributes.NO_STD
        return Attributes.NONE
    }

    enum class Attributes {
        NO_CORE, NO_STD, NONE
    }

    override val functionList: List<RsFunction> get() = findItems(FUNCTION)
    override val modItemList: List<RsModItem> get() = findItems(MOD_ITEM)
    override val constantList: List<RsConstant> get() = findItems(CONSTANT)
    override val structItemList: List<RsStructItem> get() = findItems(STRUCT_ITEM)
    override val enumItemList: List<RsEnumItem> get() = findItems(ENUM_ITEM)
    override val implItemList: List<RsImplItem> get() = findItems(IMPL_ITEM)
    override val traitItemList: List<RsTraitItem> get() = findItems(TRAIT_ITEM)
    override val typeAliasList: List<RsTypeAlias> get() = findItems(TYPE_ALIAS)
    override val useItemList: List<RsUseItem> get() = findItems(USE_ITEM)
    override val modDeclItemList: List<RsModDeclItem> get() = findItems(MOD_DECL_ITEM)
    override val externCrateItemList: List<RsExternCrateItem> get() = findItems(EXTERN_CRATE_ITEM)
    override val foreignModItemList: List<RsForeignModItem> get() = findItems(FOREIGN_MOD_ITEM)
    override val macroDefinitionList: List<RsMacroDefinition> get() = findItems(MACRO_DEFINITION)

    private inline fun <reified T : RsCompositeElement> findItems(elementType: IElementType): List<T> {
        val stub = stub
        return if (stub != null) {
            @Suppress("UNCHECKED_CAST")
            stub.getChildrenByType(elementType, { arrayOfNulls<T>(it) })
                .asList() as List<T>
        } else {
            PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)
        }
    }
}


val PsiFile.rustMod: RsMod? get() = this as? RsFile

val VirtualFile.isNotRustFile: Boolean get() = !isRustFile
val VirtualFile.isRustFile: Boolean get() = fileType == RsFileType
