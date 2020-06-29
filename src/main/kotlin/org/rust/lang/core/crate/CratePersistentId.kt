/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate

/**
 * Persistent [Crate] identifier. Guaranteed to be positive 32-bit integer.
 * This id can be saved to a disk and then used to find the crate
 *
 * See [Crate.id]
 *
 * See [CrateGraphService.findCrateById]
 */
typealias CratePersistentId = Int
