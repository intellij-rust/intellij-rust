package org.rust.lang.core.psi

/**
 * Type-bearing element is actually an element designating entity that may be constituent of
 * some type.
 *
 * Typical residents are: [RsStructItem], [RsEnumItem], [RsFunction], etc.
 */
interface RsTypeBearingItemElement : RsItemElement, RsNamedElement
