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
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.readRustPath
import org.rust.lang.core.symbols.writeRustPath
import org.rust.lang.core.types.RustUnknownType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.readRustUnresolvedType
import org.rust.lang.core.types.unresolved.writeRustUnresolvedType
import org.rust.lang.core.types.util.type
import org.rust.utils.readNullable
import org.rust.utils.writeNullable


class RustFileStub : PsiFileStubImpl<RustFile> {
    val attributes: RustFile.Attributes

    constructor(file: RustFile) : this(file, file.attributes)

    constructor(file: RustFile?, attributes: RustFile.Attributes) : super(file) {
        this.attributes = attributes
    }

    override fun getType() = Type

    object Type : IStubFileElementType<RustFileStub>(RustLanguage) {
        // Bump this number if Stub structure changes
        override fun getStubVersion(): Int = 40

        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> = RustFileStub(file as RustFile)
        }

        override fun serialize(stub: RustFileStub, dataStream: StubOutputStream) {
            dataStream.writeEnum(stub.attributes)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustFileStub {
            return RustFileStub(null, dataStream.readEnum(RustFile.Attributes.values()))
        }

        override fun getExternalId(): String = "Rust.file"
    }
}


fun factory(name: String): RustStubElementType<*, *> = when (name) {
    "EXTERN_CRATE_ITEM" -> RustExternCrateItemElementStub.Type
    "USE_ITEM" -> RustUseItemElementStub.Type

    "STRUCT_ITEM" -> RustStructItemElementStub.Type
    "UNION_ITEM" -> RustUnionItemElementStub.Type
    "ENUM_ITEM" -> RustEnumItemElementStub.Type
    "ENUM_BODY" -> RustEnumBodyElementStub.Type
    "ENUM_VARIANT" -> RustEnumVariantElementStub.Type

    "MOD_DECL_ITEM" -> RustModDeclItemElementStub.Type
    "MOD_ITEM" -> RustModItemElementStub.Type

    "TRAIT_ITEM" -> RustTraitItemElementStub.Type
    "IMPL_ITEM" -> RustImplItemElementStub.Type

    "FUNCTION" -> RustFunctionElementStub.Type
    "CONSTANT" -> RustConstantElementStub.Type
    "TYPE_ALIAS" -> RustTypeAliasElementStub.Type
    "FOREIGN_MOD_ITEM" -> RustForeignModItemElementStub.Type

    "BLOCK_FIELDS" -> RustBlockFieldsElementStub.Type
    "FIELD_DECL" -> RustFieldDeclElementStub.Type
    "ALIAS" -> RustAliasElementStub.Type

    "USE_GLOB_LIST" -> RustUseGlobListElementStub.Type
    "USE_GLOB" -> RustUseGlobElementStub.Type

    "PATH" -> RustPathElementStub.Type

    "VEC_TYPE" -> RustTypeElementStub.VecType
    "REF_LIKE_TYPE" -> RustTypeElementStub.RefLikeType
    "BARE_FN_TYPE" -> RustTypeElementStub.BareFnType
    "TUPLE_TYPE" -> RustTypeElementStub.TupleType
    "BASE_TYPE" -> RustTypeElementStub.BaseType
    "TYPE_WITH_BOUNDS_TYPE" -> RustTypeElementStub.TypeWithBoundsType
    "FOR_IN_TYPE" -> RustTypeElementStub.ForInType
    "IMPL_TRAIT_TYPE" -> RustTypeElementStub.ImplTraitType

    "GENERIC_PARAMS" -> RustGenericParamsElementStub.Type
    "TYPE_PARAM" -> RustTypeParamElementStub.Type

    else -> error("Unknown element $name")
}


class RustExternCrateItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustExternCrateItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustExternCrateItemElementStub, RustExternCrateItemElement>("EXTERN_CRATE_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustExternCrateItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustExternCrateItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustExternCrateItemElementStub) =
            RustExternCrateItemElementImpl(stub, this)

        override fun createStub(psi: RustExternCrateItemElement, parentStub: StubElement<*>?) =
            RustExternCrateItemElementStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RustExternCrateItemElementStub, sink: IndexSink) = sink.indexExternCrate(stub)
    }
}


class RustUseItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val isPublic: Boolean,
    val isStarImport: Boolean
) : RustElementStub<RustUseItemElement>(parent, elementType),
    RustVisibilityStub {

    object Type : RustStubElementType<RustUseItemElementStub, RustUseItemElement>("USE_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustUseItemElementStub(parentStub, this,
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustUseItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeBoolean(stub.isPublic)
                writeBoolean(stub.isStarImport)
            }

        override fun createPsi(stub: RustUseItemElementStub) =
            RustUseItemElementImpl(stub, this)

        override fun createStub(psi: RustUseItemElement, parentStub: StubElement<*>?) =
            RustUseItemElementStub(parentStub, this, psi.isPublic, psi.isStarImport)

        override fun indexStub(stub: RustUseItemElementStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RustStructItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustStructItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustStructItemElementStub, RustStructItemElement>("STRUCT_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustStructItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustStructItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustStructItemElementStub): RustStructItemElement =
            RustStructItemElementImpl(stub, this)

        override fun createStub(psi: RustStructItemElement, parentStub: StubElement<*>?) =
            RustStructItemElementStub(parentStub, this, psi.name, psi.isPublic)


        override fun indexStub(stub: RustStructItemElementStub, sink: IndexSink) = sink.indexStructItem(stub)
    }
}


class RustUnionItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustUnionItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustUnionItemElementStub, RustUnionItemElement>("UNION_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustUnionItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustUnionItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustUnionItemElementStub) =
            RustUnionItemElementImpl(stub, this)

        override fun createStub(psi: RustUnionItemElement, parentStub: StubElement<*>?) =
            RustUnionItemElementStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RustUnionItemElementStub, sink: IndexSink) = sink.indexUnionItem(stub)
    }
}


class RustEnumItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustEnumItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustEnumItemElementStub, RustEnumItemElement>("ENUM_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustEnumItemElementStub =
            RustEnumItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustEnumItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustEnumItemElementStub) =
            RustEnumItemElementImpl(stub, this)

        override fun createStub(psi: RustEnumItemElement, parentStub: StubElement<*>?) =
            RustEnumItemElementStub(parentStub, this, psi.name, psi.isPublic)


        override fun indexStub(stub: RustEnumItemElementStub, sink: IndexSink) = sink.indexEnumItem(stub)

    }
}


class RustEnumBodyElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RustEnumBodyElement>(parent, elementType) {

    object Type : RustStubElementType.Trivial<RustEnumBodyElementStub, RustEnumBodyElement>(
        "ENUM_BODY",
        ::RustEnumBodyElementStub,
        ::RustEnumBodyElementImpl
    )
}


class RustEnumVariantElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RustEnumVariantElement>(parent, elementType),
    RustNamedStub {

    object Type : RustStubElementType<RustEnumVariantElementStub, RustEnumVariantElement>("ENUM_VARIANT") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustEnumVariantElementStub =
            RustEnumVariantElementStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RustEnumVariantElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RustEnumVariantElementStub) =
            RustEnumVariantElementImpl(stub, this)

        override fun createStub(psi: RustEnumVariantElement, parentStub: StubElement<*>?) =
            RustEnumVariantElementStub(parentStub, this, psi.name)


        override fun indexStub(stub: RustEnumVariantElementStub, sink: IndexSink) {
            // NOP
        }
    }
}


class RustModDeclItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean,
    val pathAttribute: String?,
    val isLocal: Boolean
) : StubBase<RustModDeclItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustModDeclItemElementStub, RustModDeclItemElement>("MOD_DECL_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustModDeclItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean(),
                dataStream.readUTFFast().let { if (it == "") null else it },
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustModDeclItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
                writeUTFFast(stub.pathAttribute ?: "")
                writeBoolean(stub.isLocal)
            }

        override fun createPsi(stub: RustModDeclItemElementStub) =
            RustModDeclItemElementImpl(stub, this)

        override fun createStub(psi: RustModDeclItemElement, parentStub: StubElement<*>?) =
            RustModDeclItemElementStub(parentStub, this, psi.name, psi.isPublic, psi.pathAttribute, psi.isLocal)

        override fun indexStub(stub: RustModDeclItemElementStub, sink: IndexSink) = sink.indexModDeclItem(stub)
    }
}


class RustModItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustModItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustModItemElementStub, RustModItemElement>("MOD_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustModItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustModItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustModItemElementStub): RustModItemElement =
            RustModItemElementImpl(stub, this)

        override fun createStub(psi: RustModItemElement, parentStub: StubElement<*>?) =
            RustModItemElementStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RustModItemElementStub, sink: IndexSink) = sink.indexModItem(stub)
    }
}


class RustTraitItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustTraitItemElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustTraitItemElementStub, RustTraitItemElement>("TRAIT_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustTraitItemElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustTraitItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustTraitItemElementStub): RustTraitItemElement =
            RustTraitItemElementImpl(stub, this)

        override fun createStub(psi: RustTraitItemElement, parentStub: StubElement<*>?) =
            RustTraitItemElementStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RustTraitItemElementStub, sink: IndexSink) = sink.indexTraitItem(stub)
    }
}


class RustImplItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val type: RustUnresolvedType,
    val traitRef: RustPath?
) : RustElementStub<RustImplItemElement>(parent, elementType) {
    object Type : RustStubElementType<RustImplItemElementStub, RustImplItemElement>("IMPL_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustImplItemElementStub(parentStub, this,
                dataStream.readRustUnresolvedType(),
                dataStream.readNullable { readRustPath() }
            )

        override fun serialize(stub: RustImplItemElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeRustUnresolvedType(stub.type)
                writeNullable(stub.traitRef) { writeRustPath(it) }
            }

        override fun createPsi(stub: RustImplItemElementStub): RustImplItemElement =
            RustImplItemElementImpl(stub, this)

        override fun createStub(psi: RustImplItemElement, parentStub: StubElement<*>?) =
            RustImplItemElementStub(parentStub, this,
                psi.type?.type ?: RustUnknownType, psi.traitRef?.path?.asRustPath)

        override fun indexStub(stub: RustImplItemElementStub, sink: IndexSink) = sink.indexImplItem(stub)
    }
}


class RustFunctionElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean,
    val isAbstract: Boolean,
    val isStatic: Boolean,
    val isTest: Boolean,
    val role: RustFunctionRole
) : StubBase<RustFunctionElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustFunctionElementStub, RustFunctionElement>("FUNCTION") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustFunctionElementStub(parentStub, this,
                dataStream.readName()?.string,
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readEnum(RustFunctionRole.values())
            )

        override fun serialize(stub: RustFunctionElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
                writeBoolean(stub.isAbstract)
                writeBoolean(stub.isStatic)
                writeBoolean(stub.isTest)
                writeEnum(stub.role)
            }

        override fun createPsi(stub: RustFunctionElementStub) =
            RustFunctionElementImpl(stub, this)

        override fun createStub(psi: RustFunctionElement, parentStub: StubElement<*>?) =
            RustFunctionElementStub(parentStub, this,
                psi.name, psi.isPublic, psi.isAbstract, psi.isStatic, psi.isTest, psi.role)

        override fun indexStub(stub: RustFunctionElementStub, sink: IndexSink) = sink.indexFunction(stub)
    }
}


class RustConstantElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustConstantElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustConstantElementStub, RustConstantElement>("CONSTANT") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustConstantElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustConstantElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun createPsi(stub: RustConstantElementStub) =
            RustConstantElementImpl(stub, this)

        override fun createStub(psi: RustConstantElement, parentStub: StubElement<*>?) =
            RustConstantElementStub(parentStub, this, psi.name, psi.isPublic)

        override fun indexStub(stub: RustConstantElementStub, sink: IndexSink) = sink.indexConstant(stub)
    }
}


class RustTypeAliasElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean,
    val role: RustTypeAliasRole
) : StubBase<RustTypeAliasElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustTypeAliasElementStub, RustTypeAliasElement>("TYPE_ALIAS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustTypeAliasElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean(),
                dataStream.readEnum(RustTypeAliasRole.values())
            )

        override fun serialize(stub: RustTypeAliasElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
                writeEnum(stub.role)
            }

        override fun createPsi(stub: RustTypeAliasElementStub) =
            RustTypeAliasElementImpl(stub, this)

        override fun createStub(psi: RustTypeAliasElement, parentStub: StubElement<*>?) =
            RustTypeAliasElementStub(parentStub, this, psi.name, psi.isPublic, psi.role)

        override fun indexStub(stub: RustTypeAliasElementStub, sink: IndexSink) = sink.indexTypeAlias(stub)
    }
}


class RustForeignModItemElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RustForeignModItemElement>(parent, elementType) {

    object Type : RustStubElementType.Trivial<RustForeignModItemElementStub, RustForeignModItemElement>(
        "FOREIGN_MOD_ITEM",
        ::RustForeignModItemElementStub,
        ::RustForeignModItemElementImpl
    )
}


class RustBlockFieldsElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RustBlockFieldsElement>(parent, elementType) {

    object Type : RustStubElementType.Trivial<RustBlockFieldsElementStub, RustBlockFieldsElement>(
        "BLOCK_FIELDS",
        ::RustBlockFieldsElementStub,
        ::RustBlockFieldsElementImpl
    )
}


class RustFieldDeclElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    override val isPublic: Boolean
) : StubBase<RustFieldDeclElement>(parent, elementType),
    RustNamedStub,
    RustVisibilityStub {

    object Type : RustStubElementType<RustFieldDeclElementStub, RustFieldDeclElement>("FIELD_DECL") {
        override fun createPsi(stub: RustFieldDeclElementStub) =
            RustFieldDeclElementImpl(stub, this)

        override fun createStub(psi: RustFieldDeclElement, parentStub: StubElement<*>?) =
            RustFieldDeclElementStub(parentStub, this, psi.name, psi.isPublic)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustFieldDeclElementStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustFieldDeclElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isPublic)
            }

        override fun indexStub(stub: RustFieldDeclElementStub, sink: IndexSink) = sink.indexFieldDecl(stub)
    }
}


class RustAliasElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RustAliasElement>(parent, elementType),
    RustNamedStub {

    object Type : RustStubElementType<RustAliasElementStub, RustAliasElement>("ALIAS") {
        override fun createPsi(stub: RustAliasElementStub) =
            RustAliasElementImpl(stub, this)

        override fun createStub(psi: RustAliasElement, parentStub: StubElement<*>?) =
            RustAliasElementStub(parentStub, this, psi.name)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustAliasElementStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RustAliasElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun indexStub(stub: RustAliasElementStub, sink: IndexSink) = sink.indexAlias(stub)
    }
}


class RustUseGlobListElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RustUseGlobListElement>(parent, elementType) {

    object Type : RustStubElementType.Trivial<RustUseGlobListElementStub, RustUseGlobListElement>(
        "USE_GLOB_LIST",
        ::RustUseGlobListElementStub,
        ::RustUseGlobListElementImpl
    )
}


class RustUseGlobElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val referenceName: String
) : StubBase<RustUseGlobElement>(parent, elementType) {

    object Type : RustStubElementType<RustUseGlobElementStub, RustUseGlobElement>("USE_GLOB") {
        override fun createPsi(stub: RustUseGlobElementStub) =
            RustUseGlobElementImpl(stub, this)

        override fun createStub(psi: RustUseGlobElement, parentStub: StubElement<*>?) =
            RustUseGlobElementStub(parentStub, this, psi.referenceName)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustUseGlobElementStub(parentStub, this,
                dataStream.readName()!!.string
            )

        override fun serialize(stub: RustUseGlobElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.referenceName)
            }

        override fun indexStub(stub: RustUseGlobElementStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RustPathElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val referenceName: String,
    val isCrateRelative: Boolean,
    val hasGenericArgs: Boolean
) : StubBase<RustPathElement>(parent, elementType) {

    object Type : RustStubElementType<RustPathElementStub, RustPathElement>("PATH") {
        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun createPsi(stub: RustPathElementStub) =
            RustPathElementImpl(stub, this)

        override fun createStub(psi: RustPathElement, parentStub: StubElement<*>?) =
            RustPathElementStub(parentStub, this, psi.referenceName, psi.isCrateRelative, psi.genericArgs != null)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustPathElementStub(parentStub, this,
                dataStream.readName()!!.string,
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RustPathElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.referenceName)
                writeBoolean(stub.isCrateRelative)
                writeBoolean(stub.hasGenericArgs)
            }

        override fun indexStub(stub: RustPathElementStub, sink: IndexSink) {
            //NOP
        }
    }
}


class RustTypeElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RustTypeElement>(parent, elementType) {

    abstract class Type<PsiT : RustCompositeElement>(
        debugName: String,
        psiCtor: (RustTypeElementStub, IStubElementType<*, *>) -> PsiT
    ) : RustStubElementType.Trivial<RustTypeElementStub, PsiT>(debugName, ::RustTypeElementStub, psiCtor) {
        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)
    }

    object VecType : Type<RustVecTypeElement>(
        "VEC_TYPE",
        ::RustVecTypeElementImpl
    )

    object RefLikeType : Type<RustRefLikeTypeElement>(
        "REF_LIKE_TYPE",
        ::RustRefLikeTypeElementImpl
    )

    object BareFnType : Type<RustBareFnTypeElement>(
        "BARE_FN_TYPE",
        ::RustBareFnTypeElementImpl
    )

    object TupleType : Type<RustTupleTypeElement>(
        "TUPLE_TYPE",
        ::RustTupleTypeElementImpl
    )

    object BaseType : Type<RustBaseTypeElement>(
        "BASE_TYPE",
        ::RustBaseTypeElementImpl
    )

    object TypeWithBoundsType : Type<RustTypeWithBoundsTypeElement>(
        "TYPE_WITH_BOUNDS_TYPE",
        ::RustTypeWithBoundsTypeElementImpl
    )

    object ForInType : Type<RustForInTypeElement>(
        "FOR_IN_TYPE",
        ::RustForInTypeElementImpl
    )

    object ImplTraitType : Type<RustImplTraitTypeElement>(
        "IMPL_TRAIT_TYPE",
        ::RustImplTraitTypeElementImpl
    )
}

class RustGenericParamsElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RustGenericParamsElement>(parent, elementType) {
    object Type : RustStubElementType.Trivial<RustGenericParamsElementStub, RustGenericParamsElement>(
        "GENERIC_PARAMS",
        ::RustGenericParamsElementStub,
        ::RustGenericParamsElementImpl
    )
}


class RustTypeParamElementStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RustTypeParamElement>(parent, elementType),
    RustNamedStub {

    object Type : RustStubElementType<RustTypeParamElementStub, RustTypeParamElement>("TYPE_PARAM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RustTypeParamElementStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RustTypeParamElementStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RustTypeParamElementStub): RustTypeParamElement =
            RustTypeParamElementImpl(stub, this)

        override fun createStub(psi: RustTypeParamElement, parentStub: StubElement<*>?) =
            RustTypeParamElementStub(parentStub, this, psi.name)

        override fun indexStub(stub: RustTypeParamElementStub, sink: IndexSink) {
            // NOP
        }
    }
}


private fun StubInputStream.readNameAsString(): String? = readName()?.string

private fun <E : Enum<E>> StubOutputStream.writeEnum(e: E) = writeByte(e.ordinal)
private fun <E : Enum<E>> StubInputStream.readEnum(values: Array<E>) = values[readByte().toInt()]
