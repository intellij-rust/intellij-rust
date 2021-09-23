/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.refactoring.rename.ResolveSnapshotProvider

// BACKOMPAT: 2021.2. Inline it
typealias ResolveSnapshots = MutableList<in ResolveSnapshotProvider.ResolveSnapshot>
