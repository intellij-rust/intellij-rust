package org.rust.lang.core.names

/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * @name        Non-qualified name-part
 * @qualifier   Qualified name-part
 */
data class RustQualifiedName(val part: RustNamePart, val qualifier: RustQualifiedName? = null)
