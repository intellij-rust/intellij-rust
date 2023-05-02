/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.rust.ide.fixes.*
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.lang.utils.areUnstableFeaturesAvailable

class RsAttrErrorAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val rsHolder = RsAnnotationHolder(holder)
        when (element) {
            is RsMetaItem -> {
                val context = ProcessingContext()
                if (!element.isRootMetaItem(context)) return

                checkMetaBadDelim(element, holder)
                checkAttrTemplateCompatible(element, holder)
                checkLiteralSuffix(element, holder)

                when (element.name) {
                    "deprecated" -> checkDeprecatedAttr(element, holder)
                    "feature" -> {
                        checkFeatureAttribute(element, rsHolder, context)
                    }
                    "cfg", "cfg_attr" -> checkRootCfgPredicate(element, rsHolder)
                }
            }

            is RsDocAndAttributeOwner -> checkRsDocAndAttributeOwner(element, holder)
        }
    }
}

private fun checkDeprecatedAttr(element: RsMetaItem, holder: AnnotationHolder) {
    if (element.templateType != AttributeTemplateType.List) return
    val usedArgs = mutableSetOf<String>()
    for (arg in element.metaItemArgsList) {
        if (arg !is RsMetaItem) continue
        val argName = arg.name ?: continue
        when (argName) {
            // "suggestion" argument is used only in compiler development
            "since", "note", "suggestion" -> {
                if (arg.eq == null) {
                    RsDiagnostic.IncorrectItemInDeprecatedAttr(arg).addToHolder(holder)
                } else {
                    if (argName in usedArgs) {
                        // TODO: Remove element with commas and newlines
                        RsDiagnostic.MultipleItemsInDeprecatedAttr(arg, argName).addToHolder(holder)
                    }
                    usedArgs.add(argName)
                }
            }

            else -> {
                RsDiagnostic.UnknownItemInDeprecatedAttr(arg, argName).addToHolder(holder)
            }
        }
    }
}

private fun checkRsDocAndAttributeOwner(element: RsDocAndAttributeOwner, holder: AnnotationHolder) {
    val attrs = element.queryAttributes.metaItems
    val duplicates = attrs.filter { it.name in RS_BUILTIN_ATTRIBUTES }.groupBy { it.name }

    fun getFix(item: RsMetaItem): RemoveElementFix {
        val parent = item.parent
        return if (parent is RsAttr) RemoveElementFix(parent) else RemoveElementFix(item)
    }

    for ((name, entries) in duplicates) {
        if (name == null) continue
        if (entries.size == 1) continue
        val attrInfo = (RS_BUILTIN_ATTRIBUTES[name] as? BuiltinAttributeInfo) ?: continue
        when (attrInfo.duplicates) {
            AttributeDuplicates.DuplicatesOk -> continue

            AttributeDuplicates.WarnFollowing -> {
                RsDiagnostic.UnusedAttribute(entries.first(), getFix(entries.last())).addToHolder(holder)
            }

            AttributeDuplicates.WarnFollowingWordOnly -> {
                val wordStyleArgs = entries.filter { it.templateType == AttributeTemplateType.Word }
                if (wordStyleArgs.size == 1) continue
                RsDiagnostic.UnusedAttribute(wordStyleArgs.first(), getFix(wordStyleArgs.last())).addToHolder(holder)
            }

            AttributeDuplicates.ErrorFollowing -> {
                RsDiagnostic.MultipleAttributes(entries.first(), name, getFix(entries.last())).addToHolder(holder)
            }

            AttributeDuplicates.ErrorPreceding -> {
                RsDiagnostic.MultipleAttributes(entries.last(), name, getFix(entries.first())).addToHolder(holder)
            }

            AttributeDuplicates.FutureWarnFollowing -> {
                RsDiagnostic.UnusedAttribute(entries.first(), getFix(entries.last()), isFutureWarning = true)
                    .addToHolder(holder)
            }

            AttributeDuplicates.FutureWarnPreceding -> {
                RsDiagnostic.UnusedAttribute(entries.last(), getFix(entries.first()), isFutureWarning = true)
                    .addToHolder(holder)
            }
        }

    }
}

private fun checkLiteralSuffix(metaItem: RsMetaItem, holder: AnnotationHolder) {
    val name = metaItem.name ?: return
    if (name !in RS_BUILTIN_ATTRIBUTES) return
    val exprList = metaItem.metaItemArgs?.litExprList ?: return
    for (expr in exprList) {
        val kind = expr.kind
        if (kind is RsLiteralWithSuffix) {
            val suffix = kind.suffix ?: continue
            val editedText = expr.text.removeSuffix(suffix)
            val fix = SubstituteTextFix.replace(
                "Remove suffix", metaItem.containingFile, expr.textRange, editedText
            )
            RsDiagnostic.AttributeSuffixedLiteral(expr, fix).addToHolder(holder)
        }
    }
}

private fun checkAttrTemplateCompatible(metaItem: RsMetaItem, holder: AnnotationHolder) {
    val name = metaItem.name ?: return
    val attrInfo = (RS_BUILTIN_ATTRIBUTES[name] as? BuiltinAttributeInfo) ?: return
    val template = attrInfo.template
    var isError = false
    when (metaItem.templateType) {
        AttributeTemplateType.List -> {
            if (template.list == null) {
                isError = true
            }
        }

        AttributeTemplateType.NameValueStr -> {
            if (template.nameValueStr == null) {
                isError = true
            }
        }

        AttributeTemplateType.Word -> {
            if (!template.word) {
                isError = true
            }
        }
    }
    if (isError) {
        emitMalformedAttribute(metaItem, name, template, holder)
    }
}

private fun emitMalformedAttribute(
    metaItem: RsMetaItem,
    name: String,
    template: AttributeTemplate,
    holder: AnnotationHolder
) {
    val inner = if (metaItem.context is RsInnerAttr) "!" else ""
    var first = true
    val stringBuilder = StringBuilder()
    if (template.word) {
        first = false
        stringBuilder.append("#${inner}[${name}]")
    }
    if (template.list != null) {
        if (!first) stringBuilder.append(" or ")
        first = false
        stringBuilder.append("#${inner}[${name}(${template.list})]")
    }
    if (template.nameValueStr != null) {
        if (!first) stringBuilder.append(" or ")
        stringBuilder.append("#${inner}[${name} = \"${template.nameValueStr}\"]")
    }
    val msg = if (first) "Must be of the form" else "The following are the possible correct uses"
    RsDiagnostic.MalformedAttributeInput(metaItem, name, "$msg $stringBuilder").addToHolder(holder)
}

private fun setParen(text: String): String {
    val leftIdx = text.indexOfLast { it == '[' || it == '{' }
    val rightIdx = text.indexOfFirst { it == ']' || it == '}' }
    val chars = text.toCharArray()
    chars[leftIdx] = '('
    chars[rightIdx] = ')'
    return chars.concatToString()
}

private fun checkMetaBadDelim(element: RsMetaItem, holder: AnnotationHolder) {
    // When the wrong delimiter is used, RsMetaItem is not fully parsed, and we can't easily retrieve an attribute name
    // With the same reason we can't check the expression in cfg_attr(condition, expression)

    // element.path is used when cfg_attr is supplied
    val name = element.path ?: element.compactTT?.children?.firstOrNull {
        it.elementType == IDENTIFIER
    } ?: return
    if (name.text !in RS_BUILTIN_ATTRIBUTES) return
    val openDelim = element.compactTT?.children?.firstOrNull {
        it.elementType == RsElementTypes.EQ ||
            it.elementType == RsElementTypes.LBRACE ||
            it.elementType == RsElementTypes.LBRACK
    }
        ?: return
    if (openDelim.elementType == RsElementTypes.EQ) return
    val closingDelim =
        element.compactTT?.children?.lastOrNull { it.elementType == RsElementTypes.RBRACE || it.elementType == RsElementTypes.RBRACK }
            ?: return
    when (Pair(openDelim.elementType, closingDelim.elementType)) {
        Pair(RsElementTypes.LBRACE, RsElementTypes.RBRACE), Pair(RsElementTypes.LBRACK, RsElementTypes.RBRACK) -> {
            val fixedText = setParen(element.text)
            val fix = SubstituteTextFix.replace(
                "Replace brackets", element.containingFile, element.textRange, fixedText
            )
            RsDiagnostic.WrongMetaDelimiters(openDelim, closingDelim, fix).addToHolder(holder)
        }
    }
}

private fun checkFeatureAttribute(item: RsMetaItem, holder: RsAnnotationHolder, context: ProcessingContext) {
    // outer feature attributes are ignored by the compiler
    val attr = context.get(RsPsiPattern.META_ITEM_ATTR)
    if (attr !is RsInnerAttr) return
    val path = item.path ?: return
    // feature attributes in non-crate root modules are ignored by the compiler
    if (!item.containingMod.isCrateRoot) return
    val metaItemList = item.metaItemArgs?.metaItemList.orEmpty()
    // #![feature()] can be written even in stable channel
    if (metaItemList.isEmpty()) return
    val version = item.cargoProject?.rustcInfo?.version ?: return
    // we should annotate `feature` attribute if only we are sure that unstable features are not available
    if (item.areUnstableFeaturesAvailable(version) != ThreeState.NO) return

    val channelName = version.channel.channel ?: return

    val fix = if (item.parent == attr) RemoveAttrFix(attr) else null
    RsDiagnostic.FeatureAttributeInNonNightlyChannel(path, channelName, fix).addToHolder(holder)
}

private fun checkRootCfgPredicate(element: RsMetaItem, holder: RsAnnotationHolder) {
    val item = element.metaItemArgs?.metaItemList?.getOrNull(0) ?: return
    checkCfgPredicate(holder, item)
}

private fun checkCfgPredicate(holder: RsAnnotationHolder, item: RsMetaItem) {
    val itemName = item.name ?: return
    val args = item.metaItemArgs ?: return
    when (itemName) {
        "all", "any" -> args.metaItemList.forEach { checkCfgPredicate(holder, it) }
        "not" -> {
            if (args.metaItemList.size == 1) {
                val parameter = args.metaItemList.first()
                checkCfgPredicate(holder, parameter)
            } else {
                val fixes = listOfNotNull(ConvertMalformedCfgNotPatternToCfgAllPatternFix.createIfCompatible(item))
                RsDiagnostic.CfgNotPatternIsMalformed(item, fixes).addToHolder(holder)
            }
        }
        "version" -> { /* version is currently experimental */ }
        else -> {
            val path = item.path ?: return
            val fixes = NameSuggestionFix.createApplicable(path, itemName, listOf("all", "any", "not"), 1) { name ->
                RsPsiFactory(path.project).tryCreatePath(name) ?: error("Cannot create path out of $name")
            }
            RsDiagnostic.UnknownCfgPredicate(path, itemName, fixes).addToHolder(holder, checkExistsAfterExpansion = false)
        }
    }
}
