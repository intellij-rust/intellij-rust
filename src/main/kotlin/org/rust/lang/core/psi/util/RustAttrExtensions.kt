package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.*

/**
 * Appends a new meta item `new` to an attribute with a list of arguments:
 *
 * ```
 * #[attr]              ->  #[attr(new)]
 * #[attr="something"]  ->  #[attr(new)]
 * #[attr(something)]   ->  #[attr(something, new)]
 * #[attr(something]    ->  #[attr(something, new]
 * #[attr()]            ->  #[attr(new)]
 * #[attr(]             ->  #[attr(new]
 * ```
 *
 * @return `meta` argument or its copy if insertion was successful, null otherwise.
 */
fun RustAttr.appendMetaToList(meta: RustMetaItem): RustMetaItem? {
    // extract attribute internals
    val attrMeta = when (this) {
        is RustInnerAttr -> metaItem
        is RustOuterAttr -> metaItem
        else             -> return null
    } ?: return null

    if (attrMeta.lparen == null && attrMeta.rparen == null) {
        // if both parens are missing, remove everything inside the attribute except its identifier and add `meta`
        // between parens to it
        if (attrMeta.identifier.nextSibling != null) {
            attrMeta.deleteChildRange(attrMeta.identifier.nextSibling, attrMeta.lastChild)
        }

        val tempMeta = RustElementFactory.createMeta(this.project, "whatever(item)") ?: return null
        tempMeta.metaItem!!.replace(meta)

        val newLeftParen = attrMeta.addRangeAfter(tempMeta.lparen, tempMeta.rparen, attrMeta.identifier)
        return newLeftParen.nextSibling as RustMetaItem

    }

    if (attrMeta.lparen != null) {
        // if left paren is present, add `meta` to the end of the list of items
        val leftParen = attrMeta.lparen!!

        if (leftParen.nextSibling != null && leftParen.nextSibling != attrMeta.rparen) {
            // if there are other meta items in parens, add `meta` after the last one of them
            val elementToInsertAfter = attrMeta.rparen?.prevSibling ?: attrMeta.lastChild!!

            // we also need a comma, so first we insert a comma and a temporary meta item from another well-formed meta
            val tempMeta = RustElementFactory.createMeta(this.project, "whatever(item,item)") ?: return null
            val start = tempMeta.lparen!!.nextSibling!!.nextSibling!!
            val end = tempMeta.rparen!!.prevSibling!!
            val insertedStart = attrMeta.addRangeAfter(start, end, elementToInsertAfter)

            // replace the appended item with `meta`
            return insertedStart.nextSibling!!.replace(meta) as RustMetaItem

        } else {
            // otherwise just add `meta` right after the left paren
            return attrMeta.addAfter(meta, leftParen) as RustMetaItem
        }
    }

    return null
}
