/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ObjectUtils
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.*
import org.apache.commons.lang.text.StrBuilder
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import java.util.*
import javax.swing.Icon

class TomlSchemaCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val schemaService = JsonSchemaService.Impl.get(position.project)
        val rootSchema = schemaService.getSchemaObject(parameters.originalFile) ?: return
        val walker = JsonLikePsiWalker.getWalker(position, rootSchema) ?: return
        TomlSchemaCompletionWorker(rootSchema, walker, parameters, result).doCompletion()
    }
}

private class TomlSchemaCompletionWorker(
    private val rootSchema: JsonSchemaObject,
    private val walker: JsonLikePsiWalker,
    parameters: CompletionParameters,
    private val result: CompletionResultSet
) {

    val position = parameters.position
    val originalPosition = parameters.originalPosition ?: position
    val project = originalPosition.project

    fun doCompletion() {
        val element = walker.findElementToCheck(position) ?: return
        val isName = walker.isName(element)
        val pointerPosition = walker.findPosition(element, isName == ThreeState.NO)
        if (pointerPosition == null || pointerPosition.isEmpty && isName == ThreeState.NO) return
        val hasValue = walker.isPropertyWithValue(element)

        val schemas = JsonSchemaResolver(project, rootSchema, pointerPosition).resolve()
        val knownNames: Set<String> = HashSet()

        for (schema in schemas) {
            makeCompletion(element, schema, pointerPosition, hasValue)
        }
    }

    private fun makeCompletion(element: PsiElement, schema: JsonSchemaObject, pointerPosition: JsonPointerPosition, hasValue: Boolean) {
        val schemaProperties = schema.properties
        val properties = walker.getPropertyNamesOfParentObject(originalPosition, position)
        addKeyVariants(element, schemaProperties, properties, hasValue)
    }

    private fun addKeyVariants(element: PsiElement, schemaProperties: Map<String, JsonSchemaObject>, properties: Set<String>, hasValue: Boolean) {
        for ((key, schema) in schemaProperties) {
            if (key !in properties) {
                addPropertyVariant(element, key, schema, hasValue)
            }
        }
//        schemaProperties.keys.stream()
//            .filter { name: String -> name !in properties && name !in knownNames || adapter != null && name == adapter.name }
//            .forEach { name: String ->
//                knownNames.add(name)
//                addPropertyVariant(name, schemaProperties[name], hasValue, insertComma)
//            }
    }

//    private fun addKeyVariants(
//        schemaProperties: Map<String, JsonSchemaObject>,
//        insertComma: Boolean,
//        hasValue: Boolean,
//        properties: Collection<String>,
//        adapter: JsonPropertyAdapter?,
//        knownNames: MutableSet<String>
//    ) {
//        schemaProperties.keys.stream()
//            .filter { name: String -> name !in properties && name !in knownNames || adapter != null && name == adapter.name }
//            .forEach { name: String ->
//                knownNames.add(name)
//                addPropertyVariant(name, schemaProperties[name], hasValue, insertComma)
//            }
//    }

    private fun addPropertyVariant(element: PsiElement,
        key: String,
                                   jsonSchemaObject: JsonSchemaObject,
                                   hasValue: Boolean
//                                   ,
//                                   insertComma: Boolean
    ) {
        val variants = JsonSchemaResolver(project, jsonSchemaObject).resolve()
        val jsonSchemaObject = ObjectUtils.coalesce(ContainerUtil.getFirstItem(variants), jsonSchemaObject)

        var builder = LookupElementBuilder.create(key)
        val documentation = JsonSchemaDocumentationProvider.getBestDocumentation(true, jsonSchemaObject)
        val typeText = if (!documentation.isNullOrBlank()) {
            StringUtil.removeHtmlTags(documentation).substringBefore(". ")
        } else {
            jsonSchemaObject.getTypeDescription(true)
        }
        if (typeText != null) {
            builder = builder.withTypeText(typeText, true)
        }
        builder = builder.withIcon(getIcon(jsonSchemaObject.guessType()))
        if (jsonSchemaObject.deprecationMessage != null) {
            builder = builder.withStrikeoutness(true)
        }



        builder = builder.withInsertHandler(createInsertHandler(element, type))
//        builder = if (hasSameType(variants)) {
//            val type = jsonSchemaObject.guessType()
//            val values = jsonSchemaObject.enum
//            val defaultValue = jsonSchemaObject.default
//            val hasValues = !values.isNullOrEmpty()
//            if (type != null || hasValues || defaultValue != null) {
//                createPropertyInsertHandler()
//
//
//
//                builder.withInsertHandler(
//                    if (!hasValues || values!!.stream().map { v: Any -> v.javaClass }.distinct().count() == 1L) createPropertyInsertHandler(jsonSchemaObject, hasValue, insertComma) else createDefaultPropertyInsertHandler(true, insertComma))
//            } else {
//                builder.withInsertHandler(createDefaultPropertyInsertHandler(hasValue, insertComma))
//            }
//        } else {
//            builder.withInsertHandler(createDefaultPropertyInsertHandler(hasValue, insertComma))
//        }

        result.addElement(builder)
    }

    private fun createInsertHandler(element: PsiElement, type: JsonSchemaType?): InsertHandler<LookupElement> {
        return InsertHandler { context, item ->
            when (element) {
                is TomlKey -> {
                    val keyValue = element.parent as? TomlKeyValue ?: return@InsertHandler

                    if (keyValue.parent is PsiFile) {

                    }


                    val prefixToAdd = StringBuilder()
                    val suffixToAdd = StringBuilder()

                    if (keyValue.node.findChildByType(TomlElementTypes.EQ) == null) {
                        prefixToAdd.append(" = ")

                    }
                    val (toPrefix, toSuffix) = when (type) {
                        JsonSchemaType._string -> "\"" to "\""
                        JsonSchemaType._array -> "[" to "]"
                        JsonSchemaType._object -> "{" to "}"
                        else -> "" to ""
                    }

                    prefixToAdd.append(toPrefix)
                    suffixToAdd.append(toSuffix)

                    context.addSuffix(prefixToAdd.toString(), suffixToAdd.toString())

                }
                else -> Unit
            }

        }
    }



//    private fun createPropertyInsertHandler(
//        jsonSchemaObject: JsonSchemaObject
//    ): InsertHandler<LookupElement> {
//        var type = jsonSchemaObject.guessType()
//        val values = jsonSchemaObject.enum
//        if (type == null && values != null && !values.isEmpty()) type = JsonSchemaCompletionContributor.Worker.detectType(values)
//        val defaultValue = jsonSchemaObject.default
//        val defaultValueAsString = when (defaultValue) {
//            null, is JsonSchemaObject -> null
//            is String -> "\"$defaultValue\""
//            else -> defaultValue.toString()
//        }
//        val finalType = type
//        return InsertHandler { context, item ->
//            val editor = context.editor
//            val project = context.project
//            var stringToInsert: String? = null
//            val comma = if (insertComma) "," else ""
//            if (handleInsideQuotesInsertion(context, editor, hasValue)) return@InsertHandler
//            val element = context.file.findElementAt(editor.caretModel.offset)
//            val insertColon = element == null || ":" != element.text
//            if (!insertColon) {
//                editor.caretModel.moveToOffset(editor.caretModel.offset + 1)
//            }
//            if (finalType != null) {
//                var hadEnter: Boolean
//                when (finalType) {
//                    JsonSchemaType._object -> {
//                        EditorModificationUtil.insertStringAtCaret(editor, if (insertColon) ": " else " ",
//                            false, true,
//                            if (insertColon) 2 else 1)
//                        hadEnter = false
//                        val invokeEnter: Boolean = myWalker.hasWhitespaceDelimitedCodeBlocks()
//                        if (insertColon && invokeEnter) {
//                            JsonSchemaCompletionContributor.Worker.invokeEnterHandler(editor)
//                            hadEnter = true
//                        }
//                        if (insertColon) {
//                            stringToInsert = myWalker.getDefaultObjectValue() + comma
//                            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
//                                false, true,
//                                if (hadEnter) 0 else 1)
//                        }
//                        if (hadEnter || !insertColon) {
//                            EditorActionUtil.moveCaretToLineEnd(editor, false, false)
//                        }
//                        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
//                        if (!hadEnter && stringToInsert != null) {
//                            JsonSchemaCompletionContributor.formatInsertedString(context, stringToInsert.length)
//                        }
//                        if (stringToInsert != null && !invokeEnter) {
//                            JsonSchemaCompletionContributor.Worker.invokeEnterHandler(editor)
//                        }
//                    }
//                    JsonSchemaType._boolean -> {
//                        val value = (java.lang.Boolean.TRUE.toString() == defaultValueAsString).toString()
//                        stringToInsert = (if (insertColon) ": " else " ") + value + comma
//                        val model = editor.selectionModel
//                        EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
//                            false, true,
//                            stringToInsert.length - comma.length)
//                        JsonSchemaCompletionContributor.formatInsertedString(context, stringToInsert.length)
//                        val start = editor.selectionModel.selectionStart
//                        model.setSelection(start - value.length, start)
//                        AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
//                    }
//                    JsonSchemaType._array -> {
//                        EditorModificationUtil.insertStringAtCaret(editor, if (insertColon) ": " else " ",
//                            false, true,
//                            if (insertColon) 2 else 1)
//                        hadEnter = false
//                        if (insertColon && myWalker.hasWhitespaceDelimitedCodeBlocks()) {
//                            JsonSchemaCompletionContributor.Worker.invokeEnterHandler(editor)
//                            hadEnter = true
//                        }
//                        if (insertColon) {
//                            stringToInsert = myWalker.getDefaultArrayValue() + comma
//                            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
//                                false, true,
//                                if (hadEnter) 0 else 1)
//                        }
//                        if (hadEnter) {
//                            EditorActionUtil.moveCaretToLineEnd(editor, false, false)
//                        }
//                        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
//                        if (stringToInsert != null) {
//                            JsonSchemaCompletionContributor.formatInsertedString(context, stringToInsert.length)
//                        }
//                    }
//                    JsonSchemaType._string, JsonSchemaType._integer, JsonSchemaType._number -> JsonSchemaCompletionContributor.insertPropertyWithEnum(context, editor, defaultValueAsString, values, finalType, comma, myWalker, insertColon)
//                    else -> {
//                    }
//                }
//            } else {
//                JsonSchemaCompletionContributor.insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma, myWalker, insertColon)
//            }
//        }
//    }

    private fun getIcon(type: JsonSchemaType?): Icon {
        return when (type) {
            JsonSchemaType._object -> AllIcons.Json.Object
            JsonSchemaType._array -> AllIcons.Json.Array
            else -> AllIcons.Nodes.Property
        }
    }

    private fun hasSameType(variants: Collection<JsonSchemaObject>): Boolean {
        return variants.mapNotNull(JsonSchemaObject::guessType).distinct().count() <= 1
    }
}


fun InsertionContext.addSuffix(suffix: String) {
    document.insertString(selectionEndOffset, suffix)
    EditorModificationUtil.moveCaretRelatively(editor, suffix.length)
}

fun InsertionContext.addSuffix(prefix: String, suffix: String) {
    document.insertString(selectionEndOffset, prefix + suffix)
    EditorModificationUtil.moveCaretRelatively(editor, prefix.length)
}
