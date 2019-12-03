/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.BitUtil
import com.intellij.util.io.DataInputOutputUtil.readNullable
import com.intellij.util.io.DataInputOutputUtil.writeNullable
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.*
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger
import org.rust.stdext.makeBitMask


class RsFileStub : PsiFileStubImpl<RsFile> {
    val attributes: RsFile.Attributes

    constructor(file: RsFile) : this(file, file.attributes)

    constructor(file: RsFile?, attributes: RsFile.Attributes) : super(file) {
        this.attributes = attributes
    }

    override fun getType() = Type

    object Type : IStubFileElementType<RsFileStub>(RsLanguage) {
        // Bump this number if Stub structure changes
        override fun getStubVersion(): Int = 186

        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): StubElement<*> = RsFileStub(file as RsFile)
            override fun skipChildProcessingWhenBuildingStubs(parent: ASTNode, node: ASTNode): Boolean {
                val elementType = node.elementType
                return elementType == RsElementTypes.MACRO_ARGUMENT || elementType == RsElementTypes.MACRO_BODY
            }
        }

        override fun serialize(stub: RsFileStub, dataStream: StubOutputStream) {
            dataStream.writeEnum(stub.attributes)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsFileStub {
            return RsFileStub(null, dataStream.readEnum())
        }

        override fun getExternalId(): String = "Rust.file"

//        Uncomment to find out what causes switch to the AST
//
//        private val PARESED = com.intellij.util.containers.ContainerUtil.newConcurrentSet<String>()
//        override fun doParseContents(chameleon: ASTNode, psi: com.intellij.psi.PsiElement): ASTNode? {
//            val path = psi.containingFile?.virtualFile?.path
//            if (path != null && PARESED.add(path)) {
//                println("Parsing (${PARESED.size}) $path")
//                val trace = java.io.StringWriter().also { writer ->
//                    Exception().printStackTrace(java.io.PrintWriter(writer))
//                    writer.toString()
//                }
//                println(trace)
//                println()
//            }
//            return super.doParseContents(chameleon, psi)
//        }
    }
}


fun factory(name: String): RsStubElementType<*, *> = when (name) {
    "EXTERN_CRATE_ITEM" -> RsExternCrateItemStub.Type
    "USE_ITEM" -> RsUseItemStub.Type

    "STRUCT_ITEM" -> RsStructItemStub.Type
    "ENUM_ITEM" -> RsEnumItemStub.Type
    "ENUM_BODY" -> RsPlaceholderStub.Type("ENUM_BODY", ::RsEnumBodyImpl)
    "ENUM_VARIANT" -> RsEnumVariantStub.Type

    "MOD_DECL_ITEM" -> RsModDeclItemStub.Type
    "MOD_ITEM" -> RsModItemStub.Type

    "TRAIT_ITEM" -> RsTraitItemStub.Type
    "IMPL_ITEM" -> RsImplItemStub.Type
    "MEMBERS" -> RsPlaceholderStub.Type("MEMBERS", ::RsMembersImpl)
    "TRAIT_ALIAS" -> RsTraitAliasStub.Type
    "TRAIT_ALIAS_BOUNDS" -> RsPlaceholderStub.Type("TRAIT_ALIAS_BOUNDS", ::RsTraitAliasBoundsImpl)

    "FUNCTION" -> RsFunctionStub.Type
    "CONSTANT" -> RsConstantStub.Type
    "TYPE_ALIAS" -> RsTypeAliasStub.Type
    "FOREIGN_MOD_ITEM" -> RsPlaceholderStub.Type("FOREIGN_MOD_ITEM", ::RsForeignModItemImpl)

    "BLOCK_FIELDS" -> RsPlaceholderStub.Type("BLOCK_FIELDS", ::RsBlockFieldsImpl)
    "TUPLE_FIELDS" -> RsPlaceholderStub.Type("TUPLE_FIELDS", ::RsTupleFieldsImpl)
    "TUPLE_FIELD_DECL" -> RsPlaceholderStub.Type("TUPLE_FIELD_DECL", ::RsTupleFieldDeclImpl)
    "NAMED_FIELD_DECL" -> RsNamedFieldDeclStub.Type
    "ALIAS" -> RsAliasStub.Type

    "USE_SPECK" -> RsUseSpeckStub.Type
    "USE_GROUP" -> RsPlaceholderStub.Type("USE_GROUP", ::RsUseGroupImpl)

    "PATH" -> RsPathStub.Type
    "TYPE_QUAL" -> RsPlaceholderStub.Type("TYPE_QUAL", ::RsTypeQualImpl)

    "TRAIT_REF" -> RsPlaceholderStub.Type("TRAIT_REF", ::RsTraitRefImpl)
    "TYPE_REFERENCE" -> RsPlaceholderStub.Type("TYPE_REFERENCE", ::RsTypeReferenceImpl)

    "ARRAY_TYPE" -> RsArrayTypeStub.Type
    "REF_LIKE_TYPE" -> RsRefLikeTypeStub.Type
    "FN_POINTER_TYPE" -> RsPlaceholderStub.Type("FN_POINTER_TYPE", ::RsFnPointerTypeImpl)
    "TUPLE_TYPE" -> RsPlaceholderStub.Type("TUPLE_TYPE", ::RsTupleTypeImpl)
    "BASE_TYPE" -> RsBaseTypeStub.Type
    "FOR_IN_TYPE" -> RsPlaceholderStub.Type("FOR_IN_TYPE", ::RsForInTypeImpl)
    "TRAIT_TYPE" -> RsTraitTypeStub.Type
    "MACRO_TYPE" -> RsPlaceholderStub.Type("MACRO_TYPE", ::RsForInTypeImpl)

    "VALUE_PARAMETER_LIST" -> RsPlaceholderStub.Type("VALUE_PARAMETER_LIST", ::RsValueParameterListImpl)
    "VALUE_PARAMETER" -> RsValueParameterStub.Type
    "SELF_PARAMETER" -> RsSelfParameterStub.Type
    "VARIADIC" -> RsPlaceholderStub.Type("VARIADIC", ::RsVariadicImpl)
    "TYPE_PARAMETER_LIST" -> RsPlaceholderStub.Type("TYPE_PARAMETER_LIST", ::RsTypeParameterListImpl)
    "TYPE_PARAMETER" -> RsTypeParameterStub.Type
    "CONST_PARAMETER" -> RsConstParameterStub.Type
    "LIFETIME" -> RsLifetimeStub.Type
    "LIFETIME_PARAMETER" -> RsLifetimeParameterStub.Type
    "FOR_LIFETIMES" -> RsPlaceholderStub.Type("FOR_LIFETIMES", ::RsForLifetimesImpl)
    "TYPE_ARGUMENT_LIST" -> RsPlaceholderStub.Type("TYPE_ARGUMENT_LIST", ::RsTypeArgumentListImpl)
    "ASSOC_TYPE_BINDING" -> RsAssocTypeBindingStub.Type

    "TYPE_PARAM_BOUNDS" -> RsPlaceholderStub.Type("TYPE_PARAM_BOUNDS", ::RsTypeParamBoundsImpl)
    "POLYBOUND" -> RsPolyboundStub.Type
    "BOUND" -> RsPlaceholderStub.Type("BOUND", ::RsBoundImpl)
    "WHERE_CLAUSE" -> RsPlaceholderStub.Type("WHERE_CLAUSE", ::RsWhereClauseImpl)
    "WHERE_PRED" -> RsPlaceholderStub.Type("WHERE_PRED", ::RsWherePredImpl)

    "RET_TYPE" -> RsPlaceholderStub.Type("RET_TYPE", ::RsRetTypeImpl)

    "MACRO" -> RsMacroStub.Type
    "MACRO_2" -> RsMacro2Stub.Type
    "MACRO_CALL" -> RsMacroCallStub.Type

    "INCLUDE_MACRO_ARGUMENT" -> RsPlaceholderStub.Type("INCLUDE_MACRO_ARGUMENT", ::RsIncludeMacroArgumentImpl)
    "CONCAT_MACRO_ARGUMENT" -> RsPlaceholderStub.Type("CONCAT_MACRO_ARGUMENT", ::RsConcatMacroArgumentImpl)
    "ENV_MACRO_ARGUMENT" -> RsPlaceholderStub.Type("ENV_MACRO_ARGUMENT", ::RsEnvMacroArgumentImpl)

    "INNER_ATTR" -> RsInnerAttrStub.Type
    "OUTER_ATTR" -> RsPlaceholderStub.Type("OUTER_ATTR", ::RsOuterAttrImpl)

    "META_ITEM" -> RsMetaItemStub.Type
    "META_ITEM_ARGS" -> RsPlaceholderStub.Type("META_ITEM_ARGS", ::RsMetaItemArgsImpl)

    "BLOCK" -> RsBlockStubType

    "BINARY_OP" -> RsBinaryOpStub.Type

    "ARRAY_EXPR" -> RsExprStubType("ARRAY_EXPR", ::RsArrayExprImpl)
    "BINARY_EXPR" -> RsExprStubType("BINARY_EXPR", ::RsBinaryExprImpl)
    "BLOCK_EXPR" -> RsExprStubType("BLOCK_EXPR", ::RsBlockExprImpl)
    "BREAK_EXPR" -> RsExprStubType("BREAK_EXPR", ::RsBreakExprImpl)
    "CALL_EXPR" -> RsExprStubType("CALL_EXPR", ::RsCallExprImpl)
    "CAST_EXPR" -> RsExprStubType("CAST_EXPR", ::RsCastExprImpl)
    "CONT_EXPR" -> RsExprStubType("CONT_EXPR", ::RsContExprImpl)
    "DOT_EXPR" -> RsExprStubType("DOT_EXPR", ::RsDotExprImpl)
    "EXPR_STMT_OR_LAST_EXPR" -> RsExprStubType("EXPR_STMT_OR_LAST_EXPR", ::RsExprStmtOrLastExprImpl)
    "FOR_EXPR" -> RsExprStubType("FOR_EXPR", ::RsForExprImpl)
    "IF_EXPR" -> RsExprStubType("IF_EXPR", ::RsIfExprImpl)
    "INDEX_EXPR" -> RsExprStubType("INDEX_EXPR", ::RsIndexExprImpl)
    "LAMBDA_EXPR" -> RsExprStubType("LAMBDA_EXPR", ::RsLambdaExprImpl)
    "LIT_EXPR" -> RsLitExprStub.Type
    "LOOP_EXPR" -> RsExprStubType("LOOP_EXPR", ::RsLoopExprImpl)
    "MACRO_EXPR" -> RsExprStubType("MACRO_EXPR", ::RsMacroExprImpl)
    "MATCH_EXPR" -> RsExprStubType("MATCH_EXPR", ::RsMatchExprImpl)
    "PAREN_EXPR" -> RsExprStubType("PAREN_EXPR", ::RsParenExprImpl)
    "PATH_EXPR" -> RsExprStubType("PATH_EXPR", ::RsPathExprImpl)
    "RANGE_EXPR" -> RsExprStubType("RANGE_EXPR", ::RsRangeExprImpl)
    "RET_EXPR" -> RsExprStubType("RET_EXPR", ::RsRetExprImpl)
    "YIELD_EXPR" -> RsExprStubType("YIELD_EXPR", ::RsYieldExprImpl)
    "STRUCT_LITERAL" -> RsExprStubType("STRUCT_LITERAL", ::RsStructLiteralImpl)
    "TRY_EXPR" -> RsExprStubType("TRY_EXPR", ::RsTryExprImpl)
    "TUPLE_EXPR" -> RsExprStubType("TUPLE_EXPR", ::RsTupleExprImpl)
    "TUPLE_OR_PAREN_EXPR" -> RsExprStubType("TUPLE_OR_PAREN_EXPR", ::RsTupleOrParenExprImpl)
    "UNARY_EXPR" -> RsUnaryExprStub.Type
    "UNIT_EXPR" -> RsExprStubType("UNIT_EXPR", ::RsUnitExprImpl)
    "WHILE_EXPR" -> RsExprStubType("WHILE_EXPR", ::RsWhileExprImpl)

    "VIS" -> RsVisStub.Type
    "VIS_RESTRICTION" -> RsPlaceholderStub.Type("VIS_RESTRICTION", ::RsVisRestrictionImpl)

    else -> error("Unknown element $name")
}


class RsExternCrateItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsExternCrateItem>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsExternCrateItemStub, RsExternCrateItem>("EXTERN_CRATE_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsExternCrateItemStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsExternCrateItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsExternCrateItemStub) =
            RsExternCrateItemImpl(stub, this)

        override fun createStub(psi: RsExternCrateItem, parentStub: StubElement<*>?) =
            RsExternCrateItemStub(parentStub, this, psi.name)

        override fun indexStub(stub: RsExternCrateItemStub, sink: IndexSink) = sink.indexExternCrate(stub)
    }
}


class RsUseItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : RsElementStub<RsUseItem>(parent, elementType) {

    object Type : RsStubElementType<RsUseItemStub, RsUseItem>("USE_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsUseItemStub(parentStub, this)

        override fun serialize(stub: RsUseItemStub, dataStream: StubOutputStream) = Unit

        override fun createPsi(stub: RsUseItemStub) =
            RsUseItemImpl(stub, this)

        override fun createStub(psi: RsUseItem, parentStub: StubElement<*>?) =
            RsUseItemStub(parentStub, this)
    }
}

class RsUseSpeckStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val isStarImport: Boolean
) : RsElementStub<RsUseSpeck>(parent, elementType) {

    object Type : RsStubElementType<RsUseSpeckStub, RsUseSpeck>("USE_SPECK") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsUseSpeckStub(parentStub, this,
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsUseSpeckStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeBoolean(stub.isStarImport)
            }

        override fun createPsi(stub: RsUseSpeckStub) =
            RsUseSpeckImpl(stub, this)

        override fun createStub(psi: RsUseSpeck, parentStub: StubElement<*>?) =
            RsUseSpeckStub(parentStub, this, psi.isStarImport)

        override fun indexStub(stub: RsUseSpeckStub, sink: IndexSink) = sink.indexUseSpeck(stub)
    }
}

class RsStructItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    val isUnion: Boolean
) : StubBase<RsStructItem>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsStructItemStub, RsStructItem>("STRUCT_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsStructItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsStructItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isUnion)
            }

        override fun createPsi(stub: RsStructItemStub): RsStructItem =
            RsStructItemImpl(stub, this)

        override fun createStub(psi: RsStructItem, parentStub: StubElement<*>?) =
            RsStructItemStub(parentStub, this, psi.name, psi.kind == RsStructKind.UNION)


        override fun indexStub(stub: RsStructItemStub, sink: IndexSink) = sink.indexStructItem(stub)
    }
}


class RsEnumItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsEnumItem>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsEnumItemStub, RsEnumItem>("ENUM_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsEnumItemStub =
            RsEnumItemStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsEnumItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsEnumItemStub) =
            RsEnumItemImpl(stub, this)

        override fun createStub(psi: RsEnumItem, parentStub: StubElement<*>?) =
            RsEnumItemStub(parentStub, this, psi.name)


        override fun indexStub(stub: RsEnumItemStub, sink: IndexSink) = sink.indexEnumItem(stub)

    }
}


class RsEnumVariantStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsEnumVariant>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsEnumVariantStub, RsEnumVariant>("ENUM_VARIANT") {
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
            sink.indexEnumVariant(stub)
        }
    }
}


class RsModDeclItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    val isLocal: Boolean,    //TODO: get rid of it
    // Macro resolve optimization: stub field access is much faster than PSI traversing
    val hasMacroUse: Boolean
) : StubBase<RsModDeclItem>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsModDeclItemStub, RsModDeclItem>("MOD_DECL_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsModDeclItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsModDeclItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isLocal)
                writeBoolean(stub.hasMacroUse)
            }

        override fun createPsi(stub: RsModDeclItemStub) =
            RsModDeclItemImpl(stub, this)

        override fun createStub(psi: RsModDeclItem, parentStub: StubElement<*>?) =
            RsModDeclItemStub(parentStub, this, psi.name, psi.isLocal, psi.hasMacroUse)

        override fun indexStub(stub: RsModDeclItemStub, sink: IndexSink) = sink.indexModDeclItem(stub)
    }
}


class RsModItemStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsModItem>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsModItemStub, RsModItem>("MOD_ITEM") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsModItemStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsModItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsModItemStub): RsModItem =
            RsModItemImpl(stub, this)

        override fun createStub(psi: RsModItem, parentStub: StubElement<*>?) =
            RsModItemStub(parentStub, this, psi.name)

        override fun indexStub(stub: RsModItemStub, sink: IndexSink) = sink.indexModItem(stub)
    }
}


class RsTraitItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    private val flags: Int
) : StubBase<RsTraitItem>(parent, elementType),
    RsNamedStub {

    val isUnsafe: Boolean
        get() = BitUtil.isSet(flags, UNSAFE_MASK)
    val isAuto: Boolean
        get() = BitUtil.isSet(flags, AUTO_MASK)

    object Type : RsStubElementType<RsTraitItemStub, RsTraitItem>("TRAIT_ITEM") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsTraitItemStub {
            return RsTraitItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readUnsignedByte()
            )
        }

        override fun serialize(stub: RsTraitItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeByte(stub.flags)
            }

        override fun createPsi(stub: RsTraitItemStub): RsTraitItem =
            RsTraitItemImpl(stub, this)

        override fun createStub(psi: RsTraitItem, parentStub: StubElement<*>?): RsTraitItemStub {
            var flags = 0
            flags = BitUtil.set(flags, UNSAFE_MASK, psi.isUnsafe)
            flags = BitUtil.set(flags, AUTO_MASK, psi.isAuto)

            return RsTraitItemStub(parentStub, this, psi.name, flags)
        }

        override fun indexStub(stub: RsTraitItemStub, sink: IndexSink) = sink.indexTraitItem(stub)
    }

    companion object {
        private val UNSAFE_MASK: Int = makeBitMask(0)
        private val AUTO_MASK: Int = makeBitMask(1)
    }
}


class RsImplItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : RsElementStub<RsImplItem>(parent, elementType) {
    object Type : RsStubElementType<RsImplItemStub, RsImplItem>("IMPL_ITEM") {

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


class RsTraitAliasStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : RsElementStub<RsTraitAlias>(parent, elementType), RsNamedStub {

    object Type : RsStubElementType<RsTraitAliasStub, RsTraitAlias>("TRAIT_ALIAS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTraitAliasStub(parentStub, this, dataStream.readNameAsString())

        override fun serialize(stub: RsTraitAliasStub, dataStream: StubOutputStream) {
            with(dataStream) {
                writeName(stub.name)
            }
        }

        override fun createPsi(stub: RsTraitAliasStub): RsTraitAlias =
            RsTraitAliasImpl(stub, this)

        override fun createStub(psi: RsTraitAlias, parentStub: StubElement<*>?) =
            RsTraitAliasStub(parentStub, this, psi.name)

        override fun indexStub(stub: RsTraitAliasStub, sink: IndexSink) = sink.indexTraitAlias(stub)
    }
}


class RsFunctionStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    val abiName: String?,
    private val flags: Int
) : StubBase<RsFunction>(parent, elementType),
    RsNamedStub {

    val isAbstract: Boolean get() = BitUtil.isSet(flags, ABSTRACT_MASK) // TODO get rid of it
    val isTest: Boolean get() = BitUtil.isSet(flags, TEST_MASK) // TODO get rid of it
    val isBench: Boolean get() = BitUtil.isSet(flags, BENCH_MASK) // TODO get rid of it
    val isCfg: Boolean get() = BitUtil.isSet(flags, CFG_MASK) // TODO get rid of it
    val isConst: Boolean get() = BitUtil.isSet(flags, CONST_MASK)
    val isUnsafe: Boolean get() = BitUtil.isSet(flags, UNSAFE_MASK)
    val isExtern: Boolean get() = BitUtil.isSet(flags, EXTERN_MASK)
    val isVariadic: Boolean get() = BitUtil.isSet(flags, VARIADIC_MASK)
    val isAsync: Boolean get() = BitUtil.isSet(flags, ASYNC_MASK)
    // Method resolve optimization: stub field access is much faster than PSI traversing
    val hasSelfParameters: Boolean get() = BitUtil.isSet(flags, HAS_SELF_PARAMETER_MASK)

    object Type : RsStubElementType<RsFunctionStub, RsFunction>("FUNCTION") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsFunctionStub(parentStub, this,
                dataStream.readName()?.string,
                dataStream.readUTFFastAsNullable(),
                dataStream.readInt()
            )

        override fun serialize(stub: RsFunctionStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeUTFFastAsNullable(stub.abiName)
                writeInt(stub.flags)
            }

        override fun createPsi(stub: RsFunctionStub) =
            RsFunctionImpl(stub, this)

        override fun createStub(psi: RsFunction, parentStub: StubElement<*>?): RsFunctionStub {
            var flags = 0
            flags = BitUtil.set(flags, ABSTRACT_MASK, psi.isAbstract)
            flags = BitUtil.set(flags, TEST_MASK, psi.isTest)
            flags = BitUtil.set(flags, BENCH_MASK, psi.isBench)
            flags = BitUtil.set(flags, CFG_MASK, psi.queryAttributes.hasCfgAttr())
            flags = BitUtil.set(flags, CONST_MASK, psi.isConst)
            flags = BitUtil.set(flags, UNSAFE_MASK, psi.isUnsafe)
            flags = BitUtil.set(flags, EXTERN_MASK, psi.isExtern)
            flags = BitUtil.set(flags, VARIADIC_MASK, psi.isVariadic)
            flags = BitUtil.set(flags, ASYNC_MASK, psi.isAsync)
            flags = BitUtil.set(flags, HAS_SELF_PARAMETER_MASK, psi.hasSelfParameters)
            return RsFunctionStub(parentStub, this,
                name = psi.name,
                abiName = psi.abiName,
                flags = flags
            )
        }

        override fun indexStub(stub: RsFunctionStub, sink: IndexSink) = sink.indexFunction(stub)
    }

    companion object {
        private val ABSTRACT_MASK: Int = makeBitMask(0)
        private val TEST_MASK: Int = makeBitMask(1)
        private val BENCH_MASK: Int = makeBitMask(2)
        private val CFG_MASK: Int = makeBitMask(3)
        private val CONST_MASK: Int = makeBitMask(4)
        private val UNSAFE_MASK: Int = makeBitMask(5)
        private val EXTERN_MASK: Int = makeBitMask(6)
        private val VARIADIC_MASK: Int = makeBitMask(7)
        private val ASYNC_MASK: Int = makeBitMask(8)
        private val HAS_SELF_PARAMETER_MASK: Int = makeBitMask(9)
    }
}


class RsConstantStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    val isMut: Boolean,
    val isConst: Boolean
) : StubBase<RsConstant>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsConstantStub, RsConstant>("CONSTANT") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsConstantStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsConstantStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isMut)
                writeBoolean(stub.isConst)
            }

        override fun createPsi(stub: RsConstantStub) =
            RsConstantImpl(stub, this)

        override fun createStub(psi: RsConstant, parentStub: StubElement<*>?) =
            RsConstantStub(parentStub, this, psi.name, psi.isMut, psi.isConst)

        override fun indexStub(stub: RsConstantStub, sink: IndexSink) = sink.indexConstant(stub)
    }
}


class RsTypeAliasStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsTypeAlias>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsTypeAliasStub, RsTypeAlias>("TYPE_ALIAS") {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTypeAliasStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsTypeAliasStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsTypeAliasStub) =
            RsTypeAliasImpl(stub, this)

        override fun createStub(psi: RsTypeAlias, parentStub: StubElement<*>?) =
            RsTypeAliasStub(parentStub, this, psi.name)

        override fun indexStub(stub: RsTypeAliasStub, sink: IndexSink) = sink.indexTypeAlias(stub)
    }
}


class RsNamedFieldDeclStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsNamedFieldDecl>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsNamedFieldDeclStub, RsNamedFieldDecl>("NAMED_FIELD_DECL") {
        override fun createPsi(stub: RsNamedFieldDeclStub) =
            RsNamedFieldDeclImpl(stub, this)

        override fun createStub(psi: RsNamedFieldDecl, parentStub: StubElement<*>?) =
            RsNamedFieldDeclStub(parentStub, this, psi.name)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsNamedFieldDeclStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsNamedFieldDeclStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun indexStub(stub: RsNamedFieldDeclStub, sink: IndexSink) = sink.indexNamedFieldDecl(stub)
    }
}


class RsAliasStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsAlias>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsAliasStub, RsAlias>("ALIAS") {
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
    }
}


class RsPathStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val referenceName: String,
    val hasColonColon: Boolean,
    val kind: PathKind,
    val startOffset: Int
) : StubBase<RsPath>(parent, elementType) {

    object Type : RsStubElementType<RsPathStub, RsPath>("PATH") {
        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun createPsi(stub: RsPathStub) =
            RsPathImpl(stub, this)

        override fun createStub(psi: RsPath, parentStub: StubElement<*>?) =
            RsPathStub(parentStub, this, psi.referenceName, psi.hasColonColon, psi.kind, psi.startOffset)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsPathStub(parentStub, this,
                dataStream.readName()!!.string,
                dataStream.readBoolean(),
                dataStream.readEnum(),
                dataStream.readVarInt()
            )

        override fun serialize(stub: RsPathStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.referenceName)
                writeBoolean(stub.hasColonColon)
                writeEnum(stub.kind)
                writeVarInt(stub.startOffset)
            }
    }
}


class RsTypeParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    val isSized: Boolean
) : StubBase<RsTypeParameter>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsTypeParameterStub, RsTypeParameter>("TYPE_PARAMETER") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTypeParameterStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsTypeParameterStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.isSized)
            }

        override fun createPsi(stub: RsTypeParameterStub): RsTypeParameter =
            RsTypeParameterImpl(stub, this)

        override fun createStub(psi: RsTypeParameter, parentStub: StubElement<*>?) =
            RsTypeParameterStub(parentStub, this, psi.name, psi.isSized)
    }
}

class RsConstParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsTypeParameter>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsConstParameterStub, RsConstParameter>("CONST_PARAMETER") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsConstParameterStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsConstParameterStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsConstParameterStub): RsConstParameter =
            RsConstParameterImpl(stub, this)

        override fun createStub(psi: RsConstParameter, parentStub: StubElement<*>?) =
            RsConstParameterStub(parentStub, this, psi.name)
    }
}

class RsValueParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val patText: String?
) : StubBase<RsValueParameter>(parent, elementType) {

    object Type : RsStubElementType<RsValueParameterStub, RsValueParameter>("VALUE_PARAMETER") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsValueParameterStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsValueParameterStub, dataStream: StubOutputStream) =
            dataStream.writeName(stub.patText)

        override fun createPsi(stub: RsValueParameterStub): RsValueParameter =
            RsValueParameterImpl(stub, this)

        override fun createStub(psi: RsValueParameter, parentStub: StubElement<*>?) =
            RsValueParameterStub(parentStub, this, psi.patText)
    }
}


class RsSelfParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val isMut: Boolean,
    val isRef: Boolean,
    val isExplicitType: Boolean
) : StubBase<RsSelfParameter>(parent, elementType) {

    object Type : RsStubElementType<RsSelfParameterStub, RsSelfParameter>("SELF_PARAMETER") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsSelfParameterStub(parentStub, this,
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsSelfParameterStub, dataStream: StubOutputStream) =
            with(dataStream) {
                dataStream.writeBoolean(stub.isMut)
                dataStream.writeBoolean(stub.isRef)
                dataStream.writeBoolean(stub.isExplicitType)
            }

        override fun createPsi(stub: RsSelfParameterStub): RsSelfParameter =
            RsSelfParameterImpl(stub, this)

        override fun createStub(psi: RsSelfParameter, parentStub: StubElement<*>?) =
            RsSelfParameterStub(parentStub, this, psi.mutability.isMut, psi.isRef, psi.isExplicitType)
    }
}


class RsRefLikeTypeStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val isMut: Boolean,
    val isRef: Boolean,
    val isPointer: Boolean
) : StubBase<RsTypeElement>(parent, elementType) {

    object Type : RsStubElementType<RsRefLikeTypeStub, RsRefLikeType>("REF_LIKE_TYPE") {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsRefLikeTypeStub(parentStub, this,
                dataStream.readBoolean(),
                dataStream.readBoolean(),
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsRefLikeTypeStub, dataStream: StubOutputStream) = with(dataStream) {
            dataStream.writeBoolean(stub.isMut)
            dataStream.writeBoolean(stub.isRef)
            dataStream.writeBoolean(stub.isPointer)
        }

        override fun createPsi(stub: RsRefLikeTypeStub) = RsRefLikeTypeImpl(stub, this)

        override fun createStub(psi: RsRefLikeType, parentStub: StubElement<*>?) =
            RsRefLikeTypeStub(parentStub, this,
                psi.mutability.isMut,
                psi.isRef,
                psi.isPointer
            )
    }
}


class RsTraitTypeStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val isImpl: Boolean
) : StubBase<RsTypeElement>(parent, elementType) {

    object Type : RsStubElementType<RsTraitTypeStub, RsTraitType>("TRAIT_TYPE") {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsTraitTypeStub(parentStub, this,
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsTraitTypeStub, dataStream: StubOutputStream) = with(dataStream) {
            writeBoolean(stub.isImpl)
        }

        override fun createPsi(stub: RsTraitTypeStub) = RsTraitTypeImpl(stub, this)

        override fun createStub(psi: RsTraitType, parentStub: StubElement<*>?) =
            RsTraitTypeStub(parentStub, this,
                psi.isImpl
            )
    }
}

class RsBaseTypeStub private constructor(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val kind: RsBaseTypeStubKind
) : StubBase<RsBaseType>(parent, elementType) {

    object Type : RsStubElementType<RsBaseTypeStub, RsBaseType>("BASE_TYPE") {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsBaseTypeStub(parentStub, this, dataStream.readEnum())

        override fun serialize(stub: RsBaseTypeStub, dataStream: StubOutputStream) = with(dataStream) {
            writeEnum(stub.kind)
        }

        override fun createPsi(stub: RsBaseTypeStub) =
            RsBaseTypeImpl(stub, this)

        override fun createStub(psi: RsBaseType, parentStub: StubElement<*>?) =
            RsBaseTypeStub(parentStub, this, psi.stubKind)
    }
}

class RsArrayTypeStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val isSlice: Boolean
) : StubBase<RsArrayType>(parent, elementType) {

    object Type : RsStubElementType<RsArrayTypeStub, RsArrayType>("ARRAY_TYPE") {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsArrayTypeStub(parentStub, this, dataStream.readBoolean())

        override fun serialize(stub: RsArrayTypeStub, dataStream: StubOutputStream) = with(dataStream) {
            writeBoolean(stub.isSlice)
        }

        override fun createPsi(stub: RsArrayTypeStub) =
            RsArrayTypeImpl(stub, this)

        override fun createStub(psi: RsArrayType, parentStub: StubElement<*>?) =
            RsArrayTypeStub(parentStub, this, psi.isSlice)
    }
}

class RsLifetimeStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsLifetime>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsLifetimeStub, RsLifetime>("LIFETIME") {
        override fun createPsi(stub: RsLifetimeStub) =
            RsLifetimeImpl(stub, this)

        override fun createStub(psi: RsLifetime, parentStub: StubElement<*>?) =
            RsLifetimeStub(parentStub, this, psi.referenceName)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsLifetimeStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsLifetimeStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }
    }
}

class RsLifetimeParameterStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsLifetimeParameter>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsLifetimeParameterStub, RsLifetimeParameter>("LIFETIME_PARAMETER") {
        override fun createPsi(stub: RsLifetimeParameterStub) =
            RsLifetimeParameterImpl(stub, this)

        override fun createStub(psi: RsLifetimeParameter, parentStub: StubElement<*>?) =
            RsLifetimeParameterStub(parentStub, this, psi.name)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsLifetimeParameterStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsLifetimeParameterStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }
    }
}

class RsMacroStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?,
    val macroBody: String?
) : StubBase<RsMacro>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsMacroStub, RsMacro>("MACRO") {
        override fun shouldCreateStub(node: ASTNode): Boolean = node.psi.parent is RsMod

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsMacroStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readUTFFastAsNullable()
            )

        override fun serialize(stub: RsMacroStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeUTFFastAsNullable(stub.macroBody)
            }

        override fun createPsi(stub: RsMacroStub): RsMacro =
            RsMacroImpl(stub, this)

        override fun createStub(psi: RsMacro, parentStub: StubElement<*>?) =
            RsMacroStub(parentStub, this, psi.name, psi.macroBody?.text)

        override fun indexStub(stub: RsMacroStub, sink: IndexSink) = sink.indexMacro(stub)
    }
}

class RsMacro2Stub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsMacro2>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsMacro2Stub, RsMacro2>("MACRO_2") {
        override fun shouldCreateStub(node: ASTNode): Boolean = node.psi.parent is RsMod

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsMacro2Stub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsMacro2Stub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsMacro2Stub): RsMacro2 =
            RsMacro2Impl(stub, this)

        override fun createStub(psi: RsMacro2, parentStub: StubElement<*>?) =
            RsMacro2Stub(parentStub, this, psi.name)

        override fun indexStub(stub: RsMacro2Stub, sink: IndexSink) = sink.indexMacroDef(stub)
    }
}

class RsMacroCallStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val macroBody: String?,
    val bodyStartOffset: Int
) : StubBase<RsMacroCall>(parent, elementType) {

    object Type : RsStubElementType<RsMacroCallStub, RsMacroCall>("MACRO_CALL") {
        override fun shouldCreateStub(node: ASTNode): Boolean {
            val parent = node.psi.parent
            return parent is RsMod || parent is RsMembers || parent is RsMacroExpr && createStubIfParentIsStub(node)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsMacroCallStub(parentStub, this,
                dataStream.readUTFFastAsNullable(),
                dataStream.readVarInt()
            )

        override fun serialize(stub: RsMacroCallStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeUTFFastAsNullable(stub.macroBody)
                writeVarInt(stub.bodyStartOffset)
            }

        override fun createPsi(stub: RsMacroCallStub): RsMacroCall =
            RsMacroCallImpl(stub, this)

        override fun createStub(psi: RsMacroCall, parentStub: StubElement<*>?) =
            RsMacroCallStub(parentStub, this, psi.macroBody, psi.bodyTextRange?.startOffset ?: -1)

        override fun indexStub(stub: RsMacroCallStub, sink: IndexSink) = sink.indexMacroCall(stub)
    }
}

class RsInnerAttrStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>
) : StubBase<RsInnerAttr>(parent, elementType) {

    object Type : RsStubElementType<RsInnerAttrStub, RsInnerAttr>("INNER_ATTR") {
        override fun createPsi(stub: RsInnerAttrStub): RsInnerAttr = RsInnerAttrImpl(stub, this)

        override fun serialize(stub: RsInnerAttrStub, dataStream: StubOutputStream) {}

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsInnerAttrStub =
            RsInnerAttrStub(parentStub, this)

        override fun createStub(psi: RsInnerAttr, parentStub: StubElement<*>?): RsInnerAttrStub =
            RsInnerAttrStub(parentStub, this)

        override fun indexStub(stub: RsInnerAttrStub, sink: IndexSink) {
            sink.indexInnerAttr(stub)
        }
    }
}

class RsMetaItemStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val name: String?,
    val hasEq: Boolean
) : StubBase<RsMetaItem>(parent, elementType) {
    object Type : RsStubElementType<RsMetaItemStub, RsMetaItem>("META_ITEM") {
        override fun createStub(psi: RsMetaItem, parentStub: StubElement<*>?): RsMetaItemStub =
            RsMetaItemStub(parentStub, this, psi.name, psi.eq != null)

        override fun createPsi(stub: RsMetaItemStub): RsMetaItem = RsMetaItemImpl(stub, this)

        override fun serialize(stub: RsMetaItemStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
                writeBoolean(stub.hasEq)
            }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsMetaItemStub =
            RsMetaItemStub(parentStub, this,
                dataStream.readNameAsString(),
                dataStream.readBoolean())
    }
}

class RsBinaryOpStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val op: String
) : StubBase<RsBinaryOp>(parent, elementType) {
    object Type : RsStubElementType<RsBinaryOpStub, RsBinaryOp>("BINARY_OP") {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun serialize(stub: RsBinaryOpStub, dataStream: StubOutputStream) {
            dataStream.writeUTFFast(stub.op)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsBinaryOpStub =
            RsBinaryOpStub(parentStub, this, dataStream.readUTFFast())

        override fun createStub(psi: RsBinaryOp, parentStub: StubElement<*>?): RsBinaryOpStub =
            RsBinaryOpStub(parentStub, this, psi.op)

        override fun createPsi(stub: RsBinaryOpStub): RsBinaryOp = RsBinaryOpImpl(stub, this)
    }
}

object RsBlockStubType : RsPlaceholderStub.Type<RsBlock>("BLOCK", ::RsBlockImpl) {
    override fun shouldCreateStub(node: ASTNode): Boolean =
        createStubIfParentIsStub(node) || PsiTreeUtil.getChildOfType(node.psi, RsItemElement::class.java) != null
}

class RsExprStubType<PsiT : RsElement>(
    debugName: String,
    psiCtor: (RsPlaceholderStub, IStubElementType<*, *>) -> PsiT
) : RsPlaceholderStub.Type<PsiT>(debugName, psiCtor) {
    override fun shouldCreateStub(node: ASTNode): Boolean = shouldCreateExprStub(node)
}

class RsLitExprStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val kind: RsStubLiteralKind?
) : RsPlaceholderStub(parent, elementType) {
    object Type : RsStubElementType<RsLitExprStub, RsLitExpr>("LIT_EXPR") {

        override fun shouldCreateStub(node: ASTNode): Boolean = shouldCreateExprStub(node)

        override fun serialize(stub: RsLitExprStub, dataStream: StubOutputStream) {
            stub.kind.serialize(dataStream)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsLitExprStub =
            RsLitExprStub(parentStub, this, RsStubLiteralKind.deserialize(dataStream))

        override fun createStub(psi: RsLitExpr, parentStub: StubElement<*>?): RsLitExprStub =
            RsLitExprStub(parentStub, this, psi.stubKind)

        override fun createPsi(stub: RsLitExprStub): RsLitExpr = RsLitExprImpl(stub, this)
    }
}

private fun shouldCreateExprStub(node: ASTNode): Boolean {
    if (!createStubIfParentIsStub(node)) return false
    val element = node.psi.ancestors.firstOrNull {
        val parent = it.parent
        parent is RsItemElement || parent is RsMod
    }
    return element != null && !(element is RsBlock && element.parent is RsFunction)
}

class RsUnaryExprStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val operatorType: UnaryOperator
) : RsPlaceholderStub(parent, elementType) {
    object Type : RsStubElementType<RsUnaryExprStub, RsUnaryExpr>("UNARY_EXPR") {

        override fun shouldCreateStub(node: ASTNode): Boolean = shouldCreateExprStub(node)

        override fun serialize(stub: RsUnaryExprStub, dataStream: StubOutputStream) {
            dataStream.writeEnum(stub.operatorType)
        }

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsUnaryExprStub =
            RsUnaryExprStub(parentStub, this, dataStream.readEnum())

        override fun createStub(psi: RsUnaryExpr, parentStub: StubElement<*>?): RsUnaryExprStub =
            RsUnaryExprStub(parentStub, this, psi.operatorType)

        override fun createPsi(stub: RsUnaryExprStub): RsUnaryExpr = RsUnaryExprImpl(stub, this)
    }

}

sealed class RsStubLiteralKind(val kindOrdinal: Int) {
    class Boolean(val value: kotlin.Boolean) : RsStubLiteralKind(0)
    class Char(val value: kotlin.String?, val isByte: kotlin.Boolean) : RsStubLiteralKind(1)
    class String(val value: kotlin.String?, val isByte: kotlin.Boolean) : RsStubLiteralKind(2)
    class Integer(val value: Long?, val ty: TyInteger?) : RsStubLiteralKind(3)
    class Float(val value: Double?, val ty: TyFloat?) : RsStubLiteralKind(4)

    companion object {
        fun deserialize(dataStream: StubInputStream): RsStubLiteralKind? {
            with(dataStream) {
                return when (readByte().toInt()) {
                    0 -> Boolean(readBoolean())
                    1 -> Char(readUTFFastAsNullable(), readBoolean())
                    2 -> String(readUTFFastAsNullable(), readBoolean())
                    3 -> Integer(readLongAsNullable(), TyInteger.VALUES.getOrNull(readByte().toInt()))
                    4 -> Float(readDoubleAsNullable(), TyFloat.VALUES.getOrNull(readByte().toInt()))
                    else -> null
                }
            }
        }
    }
}

private fun RsStubLiteralKind?.serialize(dataStream: StubOutputStream) {
    if (this == null) {
        dataStream.writeByte(-1)
        return
    }
    dataStream.writeByte(kindOrdinal)
    when (this) {
        is RsStubLiteralKind.Boolean -> dataStream.writeBoolean(value)
        is RsStubLiteralKind.Char -> {
            dataStream.writeUTFFastAsNullable(value)
            dataStream.writeBoolean(isByte)
        }
        is RsStubLiteralKind.String -> {
            dataStream.writeUTFFastAsNullable(value)
            dataStream.writeBoolean(isByte)
        }
        is RsStubLiteralKind.Integer -> {
            dataStream.writeLongAsNullable(value)
            dataStream.writeByte(ty?.ordinal ?: -1)
        }
        is RsStubLiteralKind.Float -> {
            dataStream.writeDoubleAsNullable(value)
            dataStream.writeByte(ty?.ordinal ?: -1)
        }
    }
}

class RsAssocTypeBindingStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<RsAssocTypeBinding>(parent, elementType),
    RsNamedStub {

    object Type : RsStubElementType<RsAssocTypeBindingStub, RsAssocTypeBinding>("ASSOC_TYPE_BINDING") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsAssocTypeBindingStub(parentStub, this,
                dataStream.readNameAsString()
            )

        override fun serialize(stub: RsAssocTypeBindingStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeName(stub.name)
            }

        override fun createPsi(stub: RsAssocTypeBindingStub): RsAssocTypeBinding =
            RsAssocTypeBindingImpl(stub, this)

        override fun createStub(psi: RsAssocTypeBinding, parentStub: StubElement<*>?) =
            RsAssocTypeBindingStub(parentStub, this, psi.identifier.text)
    }
}

class RsPolyboundStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val hasQ: Boolean
) : StubBase<RsPolybound>(parent, elementType) {

    object Type : RsStubElementType<RsPolyboundStub, RsPolybound>("POLYBOUND") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsPolyboundStub(parentStub, this,
                dataStream.readBoolean()
            )

        override fun serialize(stub: RsPolyboundStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeBoolean(stub.hasQ)
            }

        override fun createPsi(stub: RsPolyboundStub): RsPolybound =
            RsPolyboundImpl(stub, this)

        override fun createStub(psi: RsPolybound, parentStub: StubElement<*>?) =
            RsPolyboundStub(parentStub, this, psi.hasQ)
    }
}

class RsVisStub(
    parent: StubElement<*>?, elementType: IStubElementType<*, *>,
    val kind: RsVisStubKind
) : StubBase<RsVis>(parent, elementType) {

    object Type : RsStubElementType<RsVisStub, RsVis>("VIS") {
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) =
            RsVisStub(parentStub, this,
                dataStream.readEnum()
            )

        override fun serialize(stub: RsVisStub, dataStream: StubOutputStream) =
            with(dataStream) {
                writeEnum(stub.kind)
            }

        override fun createPsi(stub: RsVisStub): RsVis =
            RsVisImpl(stub, this)

        override fun createStub(psi: RsVis, parentStub: StubElement<*>?) =
            RsVisStub(parentStub, this, psi.stubKind)
    }
}

private fun StubInputStream.readNameAsString(): String? = readName()?.string
private fun StubInputStream.readUTFFastAsNullable(): String? = readNullable(this, this::readUTFFast)
private fun StubOutputStream.writeUTFFastAsNullable(value: String?) = writeNullable(this, value, this::writeUTFFast)

private fun <E : Enum<E>> StubOutputStream.writeEnum(e: E) = writeByte(e.ordinal)
private inline fun <reified E : Enum<E>> StubInputStream.readEnum(): E = enumValues<E>()[readUnsignedByte()]

private fun StubOutputStream.writeLongAsNullable(value: Long?) = writeNullable(this, value, this::writeLong)
private fun StubInputStream.readLongAsNullable(): Long? = readNullable(this, this::readLong)

private fun StubOutputStream.writeDoubleAsNullable(value: Double?) = writeNullable(this, value, this::writeDouble)
private fun StubInputStream.readDoubleAsNullable(): Double? = readNullable(this, this::readDouble)
