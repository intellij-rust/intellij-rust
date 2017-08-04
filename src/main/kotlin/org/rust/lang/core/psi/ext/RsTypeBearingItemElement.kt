/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

/**
 * Type-bearing element is actually an element designating entity that may be constituent of
 * some type.
 *
 * Typical residents are: [org.rust.lang.core.psi.RsStructItem],
 * [org.rust.lang.core.psi.RsEnumItem], [org.rust.lang.core.psi.RsFunction], etc.
 */
interface RsTypeBearingItemElement : RsItemElement, RsNameIdentifierOwner
