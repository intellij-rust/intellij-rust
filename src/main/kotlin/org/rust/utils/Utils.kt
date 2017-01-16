package org.rust.utils

/**
 * Extramarital son of `sequenceOf` & `listOfNotNull`
 */
fun <T : Any> sequenceOfNotNull(vararg elements: T?): Sequence<T> = listOfNotNull(*elements).asSequence()

fun <T : Any> sequenceOfNotNull(element: T?): Sequence<T> = if (element != null) sequenceOf(element) else emptySequence()
