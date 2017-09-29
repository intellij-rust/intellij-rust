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
import com.intellij.psi.util.PsiTreeUtil
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

    override val `super`: RsMod?
        get() {
            // XXX: without this we'll close over `thisFile`, and it's verboten
            // to store references to PSI inside `CachedValueProvider` other than
            // the key PSI element
            val originalFile = originalFile
            return CachedValuesManager.getCachedValue(originalFile, {
                CachedValueProvider.Result.create(
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

    override val isCrateRoot: Boolean
        get() {
            val file = originalFile.virtualFile ?: return false
            return cargoWorkspace?.isCrateRoot(file) ?: false
        }

    override val innerAttrList: List<RsInnerAttr>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RsInnerAttr::class.java)

    val attributes: Attributes
        get() {
            val stub = stub
            if (stub != null) return stub.attributes
            if (queryAttributes.hasAtomAttribute("no_core")) return Attributes.NO_CORE
            if (queryAttributes.hasAtomAttribute("no_std")) return Attributes.NO_STD
            return Attributes.NONE
        }

    enum class Attributes {
        NO_CORE, NO_STD, NONE
    }

    override val functionList: List<RsFunction> get() = itemsCache.functionList
    override val modItemList: List<RsModItem> get() = itemsCache.modItemList
    override val constantList: List<RsConstant> get() = itemsCache.constantList
    override val structItemList: List<RsStructItem> get() = itemsCache.structItemList
    override val enumItemList: List<RsEnumItem> get() = itemsCache.enumItemList
    override val implItemList: List<RsImplItem> get() = itemsCache.implItemList
    override val traitItemList: List<RsTraitItem> get() = itemsCache.traitItemList
    override val typeAliasList: List<RsTypeAlias> get() = itemsCache.typeAliasList
    override val useItemList: List<RsUseItem> get() = itemsCache.useItemList
    override val modDeclItemList: List<RsModDeclItem> get() = itemsCache.modDeclItemList
    override val externCrateItemList: List<RsExternCrateItem> get() = itemsCache.externCrateItemList
    override val foreignModItemList: List<RsForeignModItem> get() = itemsCache.foreignModItemList
    override val macroDefinitionList: List<RsMacroDefinition> get() = itemsCache.macroDefinitionList

    private class ItemsCache(
        val functionList: List<RsFunction>,
        val modItemList: List<RsModItem>,
        val constantList: List<RsConstant>,
        val structItemList: List<RsStructItem>,
        val enumItemList: List<RsEnumItem>,
        val implItemList: List<RsImplItem>,
        val traitItemList: List<RsTraitItem>,
        val typeAliasList: List<RsTypeAlias>,
        val useItemList: List<RsUseItem>,
        val modDeclItemList: List<RsModDeclItem>,
        val externCrateItemList: List<RsExternCrateItem>,
        val foreignModItemList: List<RsForeignModItem>,
        val macroDefinitionList: List<RsMacroDefinition>
    )

    @Volatile
    private var _itemsCache: ItemsCache? = null

    override fun subtreeChanged() {
        super.subtreeChanged()
        _itemsCache = null
    }

    private val itemsCache: ItemsCache
        get() {
            var cached = _itemsCache
            if (cached != null) return cached
            // Might calculate cache twice concurrently, but that's ok.
            // Can't race with subtreeChanged.
            val functionList = mutableListOf<RsFunction>()
            val modItemList = mutableListOf<RsModItem>()
            val constantList = mutableListOf<RsConstant>()
            val structItemList = mutableListOf<RsStructItem>()
            val enumItemList = mutableListOf<RsEnumItem>()
            val implItemList = mutableListOf<RsImplItem>()
            val traitItemList = mutableListOf<RsTraitItem>()
            val typeAliasList = mutableListOf<RsTypeAlias>()
            val useItemList = mutableListOf<RsUseItem>()
            val modDeclItemList = mutableListOf<RsModDeclItem>()
            val externCrateItemList = mutableListOf<RsExternCrateItem>()
            val foreignModItemList = mutableListOf<RsForeignModItem>()
            val macroDefinitionList = mutableListOf<RsMacroDefinition>()

            fun add(psi: PsiElement) {
                when (psi) {
                    is RsFunction -> functionList.add(psi)
                    is RsModItem -> modItemList.add(psi)
                    is RsConstant -> constantList.add(psi)
                    is RsStructItem -> structItemList.add(psi)
                    is RsEnumItem -> enumItemList.add(psi)
                    is RsImplItem -> implItemList.add(psi)
                    is RsTraitItem -> traitItemList.add(psi)
                    is RsTypeAlias -> typeAliasList.add(psi)
                    is RsUseItem -> useItemList.add(psi)
                    is RsModDeclItem -> modDeclItemList.add(psi)
                    is RsExternCrateItem -> externCrateItemList.add(psi)
                    is RsForeignModItem -> foreignModItemList.add(psi)
                    is RsMacroDefinition -> macroDefinitionList.add(psi)
                }
            }

            val stub = stub
            if (stub != null) {
                stub.childrenStubs.forEach { add(it.psi) }
            } else {
                var child = firstChild
                while (child != null) {
                    add(child)
                    child = child.nextSibling
                }
            }

            cached = ItemsCache(
                functionList,
                modItemList,
                constantList,
                structItemList,
                enumItemList,
                implItemList,
                traitItemList,
                typeAliasList,
                useItemList,
                modDeclItemList,
                externCrateItemList,
                foreignModItemList,
                macroDefinitionList
            )
            _itemsCache = cached

            return cached
        }
}


val PsiFile.rustMod: RsMod? get() = this as? RsFile

val VirtualFile.isNotRustFile: Boolean get() = !isRustFile
val VirtualFile.isRustFile: Boolean get() = fileType == RsFileType
