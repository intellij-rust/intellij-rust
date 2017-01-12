package org.rust.lang.core.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.*
import org.rust.lang.core.psi.impl.mixin.*


class RustFileStub : PsiFileStubImpl<RsFile> {
    val attributes: RsFile.Attributes

    constructor(file: RsFile) : this(file, file.attributes)

    constructor(file: RsFile?, attributes: RsFile.Attributes) : super(file) {
        this.attributes = attributes
    }

    override fun getType() = Type

    object Type : IStubFileElementType<RustFileStub>(RustLanguage) {
        // Bump this number if Stub structure changes
        override fun getStubVersion(): Int = 52

        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> = RustFileStub(file as RsFile)
        }

        override fun serialize(stub: RustFileStub, dataStream: StubOutputStream) {
            dataStream.writeEnum(stub.attributes)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustFileStub {
            return RustFileStub(null, dataStream.readEnum(RsFile.Attributes.values()))
        }

        override fun getExternalId(): String = "Rust.file"

//        Uncomment to find out what causes switch to the AST

//        private val PARESED = ContainerUtil.newConcurrentSet<String>()
//        override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
//            val path = psi.containingFile?.virtualFile?.path
//            if (path != null && PARESED.add(path)) {
//                println("Parsing (${PARESED.size}) ${path}")
//                //Exception().printStackTrace(System.out)
//                println()
//            }
//            return super.doParseContents(chameleon, psi)
//        }
    }
}


fun factory(name: String): RustStubElementType<*, *> = when (name) {
    "EXTERN_CRATE_ITEM" -> RsExternCrateItemStub.Type
    "USE_ITEM" -> RsUseItemStub.Type

    "STRUCT_ITEM" -> RsStructItemStub.Type
    "ENUM_ITEM" -> RsEnumItemStub.Type
    "ENUM_BODY" -> RustPlaceholderStub.Type("ENUM_BODY", ::RsEnumBodyImpl)
    "ENUM_VARIANT" -> RsEnumVariantStub.Type

    "MOD_DECL_ITEM" -> RsModDeclItemStub.Type
    "MOD_ITEM" -> RsModItemStub.Type

    "TRAIT_ITEM" -> RsTraitItemStub.Type
    "IMPL_ITEM" -> RsImplItemStub.Type

    "FUNCTION" -> RsFunctionStub.Type
    "CONSTANT" -> RsConstantStub.Type
    "TYPE_ALIAS" -> RsTypeAliasStub.Type
    "FOREIGN_MOD_ITEM" -> RustPlaceholderStub.Type("FOREIGN_MOD_ITEM", ::RsForeignModItemImpl)

    "BLOCK_FIELDS" -> RustPlaceholderStub.Type("BLOCK_FIELDS", ::RsBlockFieldsImpl)
    "FIELD_DECL" -> RsFieldDeclStub.Type
    "ALIAS" -> RsAliasStub.Type

    "USE_GLOB_LIST" -> RustPlaceholderStub.Type("USE_GLOB_LIST", ::RsUseGlobListImpl)
    "USE_GLOB" -> RsUseGlobStub.Type

    "PATH" -> RsPathStub.Type

    "TRAIT_REF" -> RustPlaceholderStub.Type("TRAIT_REF", ::RsTraitRefImpl)
    "VEC_TYPE" -> RustPlaceholderStub.Type("VEC_TYPE", ::RsVecTypeImpl)
    "REF_LIKE_TYPE" -> RustPlaceholderStub.Type("REF_LIKE_TYPE", ::RsRefLikeTypeImpl)
    "BARE_FN_TYPE" -> RustPlaceholderStub.Type("BARE_FN_TYPE", ::RsBareFnTypeImpl)
    "TUPLE_TYPE" -> RustPlaceholderStub.Type("TUPLE_TYPE", ::RsTupleTypeImpl)
    "BASE_TYPE" -> RustPlaceholderStub.Type("BASE_TYPE", ::RsBaseTypeImpl)
    "TYPE_WITH_BOUNDS_TYPE" -> RustPlaceholderStub.Type("TYPE_WITH_BOUNDS_TYPE", ::RsTypeWithBoundsTypeImpl)
    "FOR_IN_TYPE" -> RustPlaceholderStub.Type("FOR_IN_TYPE", ::RsForInTypeImpl)
    "IMPL_TRAIT_TYPE" -> RustPlaceholderStub.Type("IMPL_TRAIT_TYPE", ::RsImplTraitTypeImpl)

    "VALUE_PARAMETER_LIST" -> RustPlaceholderStub.Type("VALUE_PARAMETER_LIST", ::RsValueParameterListImpl)
    "VALUE_PARAMETER" -> RustPlaceholderStub.Type("VALUE_PARAMETER", ::RsValueParameterImpl)
    "SELF_PARAMETER" -> RsSelfParameterStub.Type
    "TYPE_PARAMETER" -> RsTypeParameterStub.Type
    "TYPE_PARAMETER_LIST" -> RustPlaceholderStub.Type("TYPE_PARAMETER_LIST", ::RsTypeParameterListImpl)
    "TYPE_ARGUMENT_LIST" -> RustPlaceholderStub.Type("TYPE_ARGUMENT_LIST", ::RsTypeArgumentListImpl)

    else -> error("Unknown element $name")
}


class RsExternCrateItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsExternCrateItem>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsExternCrateItemStub, RsExternCrateItem>("EXTERN_CRATE_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsExternCrateItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsExternCrateItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RsExternCrateItemStub) =
            RsExternCrateItemImpl(stub, this)

        override fun createStub(psi: RsExternCrateItem, parentStub: StubElement<*>?) =
            RsExternCrateItemStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RsExternCrateItemStub, sink: IndexSink) = sink.indexExternCrate(stub)
    }
}


class RsUseItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val isPublic: Boolean,
    val isStarImport: Boolean
) : RustElementStub<RsUseItem>(parent, elementType),
    RustVisibilityStub {

    object Type : RustStubElementType<RsUseItemStub, RsUseItem>("USE_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsUseItemStub(parentStub, this,
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsUseItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeBoolean(stub.isPublic)
                writeBoolean(stub.isStarImport)
            }

        override fun createPsi(stub: RsUseItemStub) =
            RsUseItemImpl(stub, this)

        override fun createStub(psi: RsUseItem, parentStub: StubElement<*>?) =
            RsUseItemStub(parentStub, this, psi.isPublic, psi.isStarImport)

        override fun indexStub(stub: RsUseItemStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RsStructItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsStructItem>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsStructItemStub, RsStructItem>("STRUCT_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsStructItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsStructItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RsStructItemStub): RsStructItem =
            RsStructItemImpl(stub, this)

        override fun createStub(psi: RsStructItem, parentStub: StubElement<*>?) =
            RsStructItemStub(parentStub, this, psi.name, psi.isPublic)


        override fun indexStub(stub: RsStructItemStub, sink: IndexSink) = sink.indexStructItem(stub)
    }
}


class RsEnumItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsEnumItem>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsEnumItemStub, RsEnumItem>("ENUM_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsEnumItemStub =
            RsEnumItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsEnumItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RsEnumItemStub) =
            RsEnumItemImpl(stub, this)

        override fun createStub(psi: RsEnumItem, parentStub: StubElement<*>?) =
            RsEnumItemStub(parentStub, this, psi.name, psi.isPublic)


        override fun indexStub(stub: RsEnumItemStub, sink: IndexSink) = sink.indexEnumItem(stub)

    }
}


class RsEnumVariantStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsEnumVariant>(parent, elementType),
    RustNamedStub {

    object Type : RustStubElementType<RsEnumVariantStub, RsEnumVariant>("ENUM_VARIANT") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsEnumVariantStub =
            RsEnumVariantStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsEnumVariantStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsEnumVariantStub) =
            RsEnumVariantImpl(stub, this)

        override fun createStub(psi: RsEnumVariant, parentStub: StubElement<*>?) =
            RsEnumVariantStub(parentStub, this, psi.name)


        override fun indexStub(stub: RsEnumVariantStub, sink: IndexSink) {
            // NOP
        }
    }
}


class RsModDeclItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean,
    val pathAttribute: String?,
    val isLocal: Boolean
) : StubBase<RsModDeclItem>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsModDeclItemStub, RsModDeclItem>("MOD_DECL_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsModDeclItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean(),
                dataStream.readUTFFast().let { if (it == "") null else it },
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsModDeclItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
                writeUTFFast(stub.pathAttribute ?: "")
                writeBoolean(stub.isLocal)
            }

        override fun createPsi(stub: RsModDeclItemStub) =
            RsModDeclItemImpl(stub, this)

        override fun createStub(psi: RsModDeclItem, parentStub: StubElement<*>?) =
            RsModDeclItemStub(parentStub, this, psi.name, psi.isPublic, psi.pathAttribute, psi.isLocal)

        override fun indexStub(stub: RsModDeclItemStub, sink: IndexSink) = sink.indexModDeclItem(stub)
    }
}


class RsModItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsModItem>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsModItemStub, RsModItem>("MOD_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsModItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsModItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RsModItemStub): RsModItem =
            RsModItemImpl(stub, this)

        override fun createStub(psi: RsModItem, parentStub: StubElement<*>?) =
            RsModItemStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RsModItemStub, sink: IndexSink) = sink.indexModItem(stub)
    }
}


class RsTraitItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsTraitItem>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsTraitItemStub, RsTraitItem>("TRAIT_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTraitItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsTraitItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RsTraitItemStub): RsTraitItem =
            RsTraitItemImpl(stub, this)

        override fun createStub(psi: RsTraitItem, parentStub: StubElement<*>?) =
            RsTraitItemStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RsTraitItemStub, sink: IndexSink) = sink.indexTraitItem(stub)
    }
}


class RsImplItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : RustElementStub<RsImplItem>(parent, elementType) {
    object Type : RustStubElementType<RsImplItemStub, RsImplItem>("IMPL_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsImplItemStub(parentStub, this)

        override fun serialize(stub: RsImplItemStub, dataStream: StubOutputStream) {
        }

        override fun createPsi(stub: RsImplItemStub): RsImplItem =
            RsImplItemImpl(stub, this)

        override fun createStub(psi: RsImplItem, parentStub: StubElement<*>?) =
            RsImplItemStub(parentStub, this)

        override fun indexStub(stub: RsImplItemStub, sink: IndexSink) = sink.indexImplItem(stub)
    }
}


class RsFunctionStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean,
    val isAbstract: Boolean,
    val isStatic: Boolean,
    val isTest: Boolean,
    val role: RustFunctionRole
) : StubBase<RsFunction>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsFunctionStub, RsFunction>("FUNCTION") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsFunctionStub(parentStub, this,
                dataStream.readName()?.string,
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readEnum(RustFunctionRole.values())
            )

        override fun serialize(stub: RsFunctionStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
                writeBoolean(stub.isAbstract)
                writeBoolean(stub.isStatic)
                writeBoolean(stub.isTest)
                writeEnum(stub.role)
            }

        override fun createPsi(stub: RsFunctionStub) =
            RsFunctionImpl(stub, this)

        override fun createStub(psi: RsFunction, parentStub: StubElement<*>?) =
            RsFunctionStub(parentStub, this,
                psi.name, psi.isPublic, psi.isAbstract, psi.isStatic, psi.isTest, psi.role)

        override fun indexStub(stub: RsFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}


class RsConstantStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsConstant>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsConstantStub, RsConstant>("CONSTANT") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsConstantStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsConstantStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RsConstantStub) =
            RsConstantImpl(stub, this)

        override fun createStub(psi: RsConstant, parentStub: StubElement<*>?) =
            RsConstantStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RsConstantStub, sink: IndexSink) = sink.indexConstant(stub)
    }
}


class RsTypeAliasStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean,
    val role: RustTypeAliasRole
) : StubBase<RsTypeAlias>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsTypeAliasStub, RsTypeAlias>("TYPE_ALIAS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTypeAliasStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean(),
                dataStream.readEnum(RustTypeAliasRole.values())
            )

        override fun serialize(stub: RsTypeAliasStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
                writeEnum(stub.role)
            }

        override fun createPsi(stub: RsTypeAliasStub) =
            RsTypeAliasImpl(stub, this)

        override fun createStub(psi: RsTypeAlias, parentStub: StubElement<*>?) =
            RsTypeAliasStub(parentStub, this, psi.name, psi.isPublic, psi.role)

        override fun indexStub(stub: RsTypeAliasStub, sink: IndexSink) = sink.indexTypeAlias(stub)
    }
}


class RsFieldDeclStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RsFieldDecl>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RsFieldDeclStub, RsFieldDecl>("FIELD_DECL") {
        override fun createPsi(stub: RsFieldDeclStub) =
            RsFieldDeclImpl(stub, this)

        override fun createStub(psi: RsFieldDecl, parentStub: StubElement<*>?) =
            RsFieldDeclStub(parentStub, this, psi.name, psi.isPublic)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsFieldDeclStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsFieldDeclStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun indexStub(stub: RsFieldDeclStub, sink: IndexSink) = sink.indexFieldDecl(stub)
    }
}


class RsAliasStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsAlias>(parent, elementType),
    RustNamedStub {

    object Type : RustStubElementType<RsAliasStub, RsAlias>("ALIAS") {
        override fun createPsi(stub: RsAliasStub) =
            RsAliasImpl(stub, this)

        override fun createStub(psi: RsAlias, parentStub: StubElement<*>?) =
            RsAliasStub(parentStub, this, psi.name)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsAliasStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsAliasStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun indexStub(stub: RsAliasStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RsUseGlobStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val referenceName: String
) : StubBase<RsUseGlob>(parent, elementType) {

    object Type : RustStubElementType<RsUseGlobStub, RsUseGlob>("USE_GLOB") {
        override fun createPsi(stub: RsUseGlobStub) =
            RsUseGlobImpl(stub, this)

        override fun createStub(psi: RsUseGlob, parentStub: StubElement<*>?) =
            RsUseGlobStub(parentStub, this, psi.referenceName)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsUseGlobStub(parentStub, this,
                dataStream.readName()!!.string
            )

        override fun serialize(stub: RsUseGlobStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.referenceName)
            }

        override fun indexStub(stub: RsUseGlobStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RsPathStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val referenceName: String,
    val isCrateRelative: Boolean
) : StubBase<RsPath>(parent, elementType) {

    object Type : RustStubElementType<RsPathStub, RsPath>("PATH") {
        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun createPsi(stub: RsPathStub) =
            RsPathImpl(stub, this)

        override fun createStub(psi: RsPath, parentStub: StubElement<*>?) =
            RsPathStub(parentStub, this, psi.referenceName, psi.isCrateRelative)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsPathStub(parentStub, this,
                dataStream.readName()!!.string,
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsPathStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.referenceName)
                writeBoolean(stub.isCrateRelative)
            }

        override fun indexStub(stub: RsPathStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RsTypeParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsTypeParameter>(parent, elementType),
    RustNamedStub {

    object Type : RustStubElementType<RsTypeParameterStub, RsTypeParameter>("TYPE_PARAMETER") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTypeParameterStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsTypeParameterStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsTypeParameterStub): RsTypeParameter =
            RsTypeParameterImpl(stub, this)

        override fun createStub(psi: RsTypeParameter, parentStub: StubElement<*>?) =
            RsTypeParameterStub(parentStub, this, psi.name)

        override fun indexStub(stub: RsTypeParameterStub, sink: IndexSink) {
            // NOP
        }
    }
}


class RsSelfParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val isMut: Boolean,
    val isRef: Boolean
) : StubBase<RsSelfParameter>(parent, elementType) {

    object Type : RustStubElementType<RsSelfParameterStub, RsSelfParameter>("SELF_PARAMETER") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsSelfParameterStub(parentStub, this,
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsSelfParameterStub, dataStream: StubOutputStream) =
            with(dataStream) {
                dataStream.writeBoolean(stub.isMut)
                dataStream.writeBoolean(stub.isRef)
            }

        override fun createPsi(stub: RsSelfParameterStub): RsSelfParameter =
            RsSelfParameterImpl(stub, this)

        override fun createStub(psi: RsSelfParameter, parentStub: StubElement<*>?) =
            RsSelfParameterStub(parentStub, this, psi.isRef, psi.isMut)

        override fun indexStub(stub: RsSelfParameterStub, sink: IndexSink) {
            // NOP
        }
    }
}


private fun StubInputStream.readNameAsString(): String? = readName()?.string

private fun <E : Enum<E>> StubOutputStream.writeEnum(e: E) = writeByte(e.ordinal)
private fun <E : Enum<E>> StubInputStream.readEnum(values: Array<E>) = values[readByte().toInt()]
