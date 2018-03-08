/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes


internal class ConvertToBorrowedTyWithMutFixTest : ConvertToTyUsingTraitFixTestBase(
    true, "BorrowMut", "borrow_mut", "use std::borrow::BorrowMut;")
