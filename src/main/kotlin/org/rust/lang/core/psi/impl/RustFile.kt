package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.cargoProject
import org.rust.lang.RustFileType
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustFileStub
import org.rust.lang.core.stubs.index.RustModulesIndex
import org.rust.lang.core.symbols.RustPath

class RustFile(
    fileViewProvider: FileViewProvider
) : PsiFileBase(fileViewProvider, RustLanguage),
    RustMod,
    RustInnerAttributeOwner {

    override fun getReference(): RustReference? = null

    override fun getFileType(): FileType = RustFileType

    override fun getStub(): RustFileStub? = super.getStub() as RustFileStub?

    override fun getOriginalFile(): RustFile = super.getOriginalFile() as RustFile

    override val `super`: RustMod?
        get() {
            // XXX: without this we'll close over `thisFile`, and it's verboten
            // to store references to PSI inside `CachedValueProvider` other than
            // the key PSI element
            val originalFile = originalFile
            return CachedValuesManager.getCachedValue(originalFile, CachedValueProvider {
                CachedValueProvider.Result.create(
                    RustModulesIndex.getSuperFor(originalFile),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            })
        }

    override val modName: String? = if (name != RustMod.MOD_RS) FileUtil.getNameWithoutExtension(name) else parent?.name

    override val crateRelativePath: RustPath.CrateRelative? get() = RustPsiImplUtil.modCrateRelativePath(this)

    override val ownsDirectory: Boolean
        get() = name == RustMod.MOD_RS || isCrateRoot

    override val ownedDirectory: PsiDirectory?
        get() = originalFile.parent

    override val isCrateRoot: Boolean get() {
        val file = originalFile.virtualFile ?: return false
        return module?.cargoProject?.isCrateRoot(file) ?: false
    }

    override val innerAttrList: List<RustInnerAttrElement>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustInnerAttrElement::class.java)

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

    override val functionList: List<RustFunctionElement> get() = findItems(FUNCTION)
    override val modItemList: List<RustModItemElement> get() = findItems(MOD_ITEM)
    override val constantList: List<RustConstantElement> get() = findItems(CONSTANT)
    override val structItemList: List<RustStructItemElement> get() = findItems(STRUCT_ITEM)
    override val enumItemList: List<RustEnumItemElement> get() = findItems(ENUM_ITEM)
    override val unionItemList: List<RustUnionItemElement> get() = findItems(UNION_ITEM)
    override val implItemList: List<RustImplItemElement> get() = findItems(IMPL_ITEM)
    override val traitItemList: List<RustTraitItemElement> get() = findItems(TRAIT_ITEM)
    override val typeAliasList: List<RustTypeAliasElement> get() = findItems(TYPE_ALIAS)
    override val useItemList: List<RustUseItemElement> get() = findItems(USE_ITEM)
    override val modDeclItemList: List<RustModDeclItemElement> get() = findItems(MOD_DECL_ITEM)
    override val externCrateItemList: List<RustExternCrateItemElement> get() = findItems(EXTERN_CRATE_ITEM)
    override val foreignModItemList: List<RustForeignModItemElement> get() = findItems(FOREIGN_MOD_ITEM)

    private inline fun <reified T : RustCompositeElement> findItems(elementType: IElementType): List<T> {
        val stub = stub
        return if (stub != null) {
            @Suppress("UNCHECKED_CAST")
            stub.getChildrenByType(elementType, { kotlin.arrayOfNulls<T>(it) })
                .asList() as List<T>
        } else {
            PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)
        }
    }
}


val PsiFile.rustMod: RustMod? get() = this as? RustFile

val VirtualFile.isNotRustFile: Boolean get() = !isRustFile
val VirtualFile.isRustFile: Boolean get() = fileType == RustFileType
