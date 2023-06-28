/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.impls

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.mir.dataflow.framework.Analysis
import org.rust.lang.core.mir.dataflow.framework.BorrowSet
import org.rust.lang.core.mir.dataflow.move.MoveData
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.psi.ext.RsFunctionOrLambda
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import java.util.*
import kotlin.streams.asSequence

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class BorrowsTest : MirDataflowTestBase<BitSet>() {
    override fun createAnalysis(body: MirBody): Analysis<BitSet> {
        val moveData = MoveData.gatherMoves(body)
        val localsAreInvalidatedAtExit = body.sourceElement.ancestorOrSelf<RsItemElement>() is RsFunctionOrLambda
        val borrowSet = BorrowSet.build(body, localsAreInvalidatedAtExit, moveData)
        return Borrows(borrowSet, emptyMap())
    }

    override fun formatState(state: BitSet): String {
        return state
            .stream()
            .asSequence()
            .joinToString(prefix = "{", separator = ", ", postfix = "}") { "_$it" }
    }

    override fun formatStateDiff(oldState: BitSet, newState: BitSet): String? {
        val diff = oldState.clone() as BitSet
        diff.xor(newState)
        val adds = mutableListOf<String>()
        val rems = mutableListOf<String>()
        for (i in diff.stream()) {
            if (newState.get(i)) {
                adds += "+_$i"
            } else {
                rems += "-_$i"
            }
        }
        return if (adds.isNotEmpty() || rems.isNotEmpty()) {
            (adds + rems).joinToString(separator = ", ")
        } else {
            null
        }
    }

    fun `test 1`() = doTest("""
        struct S;
        fn main() {
            let a = S;
            let b = &a;
            let c = a; // E0505
            let d = b;
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:3:19: 3:19
            let _1: S;                           // in scope 0 at src/main.rs:4:17: 4:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:4:17: 4:18
                let _2: &S;                      // in scope 1 at src/main.rs:5:17: 5:18
                scope 2 {
                    debug b => _2;               // in scope 2 at src/main.rs:5:17: 5:18
                    let _3: S;                   // in scope 2 at src/main.rs:6:17: 6:18
                    scope 3 {
                        debug c => _3;           // in scope 3 at src/main.rs:6:17: 6:18
                        let _4: &S;              // in scope 3 at src/main.rs:7:17: 7:18
                        scope 4 {
                            debug d => _4;       // in scope 4 at src/main.rs:7:17: 7:18
                        }
                    }
                }
            }

            bb0: {                               // {}
                StorageLive(_1);
                _1 = S;
                FakeRead(ForLet(None), _1);
                StorageLive(_2);
                _2 = &_1;                        // +_0
                FakeRead(ForLet(None), _2);
                StorageLive(_3);
                _3 = move _1;
                FakeRead(ForLet(None), _3);
                StorageLive(_4);
                _4 = _2;
                FakeRead(ForLet(None), _4);
                _0 = const ();
                StorageDead(_4);
                StorageDead(_3);
                StorageDead(_2);
                StorageDead(_1);                 // -_0
                return;
            }                                    // {}
        }
    """)

    fun `test 2`() = doTest("""
        struct S;
        fn main() {
            let a = S;
            let b = &a;
            let c = a; // false-positive E0505
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:3:19: 3:19
            let _1: S;                           // in scope 0 at src/main.rs:4:17: 4:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:4:17: 4:18
                let _2: &S;                      // in scope 1 at src/main.rs:5:17: 5:18
                scope 2 {
                    debug b => _2;               // in scope 2 at src/main.rs:5:17: 5:18
                    let _3: S;                   // in scope 2 at src/main.rs:6:17: 6:18
                    scope 3 {
                        debug c => _3;           // in scope 3 at src/main.rs:6:17: 6:18
                    }
                }
            }

            bb0: {                               // {}
                StorageLive(_1);
                _1 = S;
                FakeRead(ForLet(None), _1);
                StorageLive(_2);
                _2 = &_1;                        // +_0
                FakeRead(ForLet(None), _2);
                StorageLive(_3);
                _3 = move _1;
                FakeRead(ForLet(None), _3);
                _0 = const ();
                StorageDead(_3);
                StorageDead(_2);
                StorageDead(_1);                 // -_0
                return;
            }                                    // {}
        }
    """)

    fun `test 3`() = doTest("""
        struct S;
        fn main() {
            let a = S;
            let b = S;
            let ar = if false {
                &a
            } else {
                &b
            };
            let c = a; // E0505
            let d = ar;
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:3:19: 3:19
            let _1: S;                           // in scope 0 at src/main.rs:4:17: 4:18
            let mut _4: bool;                    // in scope 0 at src/main.rs:6:25: 6:30
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:4:17: 4:18
                let _2: S;                       // in scope 1 at src/main.rs:5:17: 5:18
                scope 2 {
                    debug b => _2;               // in scope 2 at src/main.rs:5:17: 5:18
                    let _3: &S;                  // in scope 2 at src/main.rs:6:17: 6:19
                    scope 3 {
                        debug ar => _3;          // in scope 3 at src/main.rs:6:17: 6:19
                        let _5: S;               // in scope 3 at src/main.rs:11:17: 11:18
                        scope 4 {
                            debug c => _5;       // in scope 4 at src/main.rs:11:17: 11:18
                            let _6: &S;          // in scope 4 at src/main.rs:12:17: 12:18
                            scope 5 {
                                debug d => _6;   // in scope 5 at src/main.rs:12:17: 12:18
                            }
                        }
                    }
                }
            }

            bb0: {                               // {}
                StorageLive(_1);
                _1 = S;
                FakeRead(ForLet(None), _1);
                StorageLive(_2);
                _2 = S;
                FakeRead(ForLet(None), _2);
                StorageLive(_3);
                StorageLive(_4);
                _4 = const false;
                switchInt(move _4) -> [0: bb2, otherwise: bb1];
            }                                    // {}

            bb1: {                               // {}
                _3 = &_1;                        // +_0
                goto -> bb4;
            }                                    // {_0}

            bb2: {                               // {}
                goto -> bb3;
            }                                    // {}

            bb3: {                               // {}
                _3 = &_2;                        // +_1
                goto -> bb4;
            }                                    // {_1}

            bb4: {                               // {_0, _1}
                StorageDead(_4);
                FakeRead(ForLet(None), _3);
                StorageLive(_5);
                _5 = move _1;
                FakeRead(ForLet(None), _5);
                StorageLive(_6);
                _6 = _3;
                FakeRead(ForLet(None), _6);
                _0 = const ();
                StorageDead(_6);
                StorageDead(_5);
                StorageDead(_3);
                StorageDead(_2);                 // -_1
                StorageDead(_1);                 // -_0
                return;
            }                                    // {}
        }
    """)

    fun `test function call`() = doTest("""
        struct S;
        fn main() {
            let a = S;
            let b = foo(&a);
            let c = a; // E0505
            let d = b;
        }
        fn foo<T>(a: T) -> T {}
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:3:19: 3:19
            let _1: S;                           // in scope 0 at src/main.rs:4:17: 4:18
            let mut _3: &S;                      // in scope 0 at src/main.rs:5:25: 5:27
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:4:17: 4:18
                let _2: &S;                      // in scope 1 at src/main.rs:5:17: 5:18
                scope 2 {
                    debug b => _2;               // in scope 2 at src/main.rs:5:17: 5:18
                    let _4: S;                   // in scope 2 at src/main.rs:6:17: 6:18
                    scope 3 {
                        debug c => _4;           // in scope 3 at src/main.rs:6:17: 6:18
                        let _5: &S;              // in scope 3 at src/main.rs:7:17: 7:18
                        scope 4 {
                            debug d => _5;       // in scope 4 at src/main.rs:7:17: 7:18
                        }
                    }
                }
            }

            bb0: {                               // {}
                StorageLive(_1);
                _1 = S;
                FakeRead(ForLet(None), _1);
                StorageLive(_2);
                StorageLive(_3);
                _3 = &_1;                        // +_0
                _2 = foo(move _3) -> [return: bb1, unwind: bb2];
            }                                    // {_0}

            bb1: {                               // {_0}
                StorageDead(_3);
                FakeRead(ForLet(None), _2);
                StorageLive(_4);
                _4 = move _1;
                FakeRead(ForLet(None), _4);
                StorageLive(_5);
                _5 = _2;
                FakeRead(ForLet(None), _5);
                _0 = const ();
                StorageDead(_5);
                StorageDead(_4);
                StorageDead(_2);
                StorageDead(_1);                 // -_0
                return;
            }                                    // {}

            bb2 (cleanup): {                     // {_0}
                resume;
            }                                    // {_0}
        }
    """)
}
