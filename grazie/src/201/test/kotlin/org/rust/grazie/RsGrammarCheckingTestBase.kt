/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.ide.GrazieInspection
import org.rust.ide.inspections.RsInspectionsTestBase

// BACKCOMPAT: 2019.3. Inline
abstract class RsGrammarCheckingTestBase : RsInspectionsTestBase(GrazieInspection::class)
