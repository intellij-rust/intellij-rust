/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.impls

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.mir.dataflow.framework.Analysis
import org.rust.lang.core.mir.dataflow.move.MoveData
import org.rust.lang.core.mir.schemas.MirBody
import java.util.*
import kotlin.streams.asSequence

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class MaybeUninitializedPlacesTest : MirDataflowTestBase<BitSet>() {
    override fun createAnalysis(body: MirBody): Analysis<BitSet> =
        MaybeUninitializedPlaces(MoveData.gatherMoves(body))

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

    fun `test empty function`() = doTest("""
        fn main() {}
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19

            bb0: {                               // {_0}
                _0 = const ();                   // -_0
                return;
            }                                    // {}
        }
    """)

    fun `test uninitialized variable`() = doTest("""
        fn main() {
            let a: i32;
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1}
                StorageLive(_1);
                _0 = const ();                   // -_0
                StorageDead(_1);
                return;
            }                                    // {_1}
        }
    """)

    fun `test initialized variable`() = doTest("""
        fn main() {
            let a: i32 = 1;
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1}
                StorageLive(_1);
                _1 = const 1_i32;                // -_1
                FakeRead(ForLet(None), _1);
                _0 = const ();                   // -_0
                StorageDead(_1);                 // +_1
                return;
            }                                    // {_1}
        }
    """)

    fun `test variable initialized on a separate line`() = doTest("""
        fn main() {
            let a: i32;
            a = 1;
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1}
                StorageLive(_1);
                _1 = const 1_i32;                // -_1
                _0 = const ();                   // -_0
                StorageDead(_1);                 // +_1
                return;
            }                                    // {_1}
        }
    """)

    fun `test two uninitialized variables`() = doTest("""
        fn main() {
            let a: i32;
            let b: i32;
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
                let _2: i32;                     // in scope 1 at src/main.rs:4:17: 4:18
                scope 2 {
                    debug b => _2;               // in scope 2 at src/main.rs:4:17: 4:18
                }
            }

            bb0: {                               // {_0, _1, _2}
                StorageLive(_1);
                StorageLive(_2);
                _0 = const ();                   // -_0
                StorageDead(_2);
                StorageDead(_1);
                return;
            }                                    // {_1, _2}
        }
    """)

    fun `test variable initialized in one branch`() = doTest("""
        fn main() {
            let a: i32;
            if true {
                a = 1;
            } else {}
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            let mut _2: bool;                    // in scope 0 at src/main.rs:4:16: 4:20
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1, _2}
                StorageLive(_1);
                StorageLive(_2);
                _2 = const true;                 // -_2
                switchInt(move _2) -> [0: bb2, otherwise: bb1]; // +_2
            }                                    // {_0, _1, _2}

            bb1: {                               // {_0, _1, _2}
                _1 = const 1_i32;                // -_1
                _0 = const ();                   // -_0
                goto -> bb4;
            }                                    // {_2}

            bb2: {                               // {_0, _1, _2}
                goto -> bb3;
            }                                    // {_0, _1, _2}

            bb3: {                               // {_0, _1, _2}
                _0 = const ();                   // -_0
                goto -> bb4;
            }                                    // {_1, _2}

            bb4: {                               // {_1, _2}
                StorageDead(_2);
                StorageDead(_1);
                return;
            }                                    // {_1, _2}
        }
    """)

    fun `test variable initialized in all branches`() = doTest("""
        fn main() {
            let a: i32;
            if true {
                a = 1;
            } else {
                a = 1;
            }
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            let mut _2: bool;                    // in scope 0 at src/main.rs:4:16: 4:20
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1, _2}
                StorageLive(_1);
                StorageLive(_2);
                _2 = const true;                 // -_2
                switchInt(move _2) -> [0: bb2, otherwise: bb1]; // +_2
            }                                    // {_0, _1, _2}

            bb1: {                               // {_0, _1, _2}
                _1 = const 1_i32;                // -_1
                _0 = const ();                   // -_0
                goto -> bb4;
            }                                    // {_2}

            bb2: {                               // {_0, _1, _2}
                goto -> bb3;
            }                                    // {_0, _1, _2}

            bb3: {                               // {_0, _1, _2}
                _1 = const 1_i32;                // -_1
                _0 = const ();                   // -_0
                goto -> bb4;
            }                                    // {_2}

            bb4: {                               // {_2}
                StorageDead(_2);
                StorageDead(_1);                 // +_1
                return;
            }                                    // {_1, _2}
        }
    """)

    fun `test loop`() = doTest("""
        fn main() {
            let a: i32;
            loop {
                a = 1;
            }
        }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            let mut _2: !;                       // in scope 0 at src/main.rs:4:13: 6:14
            let mut _3: ();                      // in scope 0 at src/main.rs:2:9: 7:10
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1, _2, _3}
                StorageLive(_1);
                StorageLive(_2);
                goto -> bb1;
            }                                    // {_0, _1, _2, _3}

            bb1: {                               // {_0, _1, _2, _3}
                falseUnwind -> [real: bb2, unwind: bb5];
            }                                    // {_0, _1, _2, _3}

            bb2: {                               // {_0, _1, _2, _3}
                _1 = const 1_i32;                // -_1
                _3 = const ();                   // -_3
                goto -> bb1;
            }                                    // {_0, _2}

            bb3: {                               // {}
                unreachable;
            }                                    // {}

            bb4: {                               // {}
                StorageDead(_2);                 // +_2
                StorageDead(_1);                 // +_1
                return;
            }                                    // {_1, _2}

            bb5 (cleanup): {                     // {_0, _1, _2, _3}
                resume;
            }                                    // {_0, _1, _2, _3}
        }
    """)

    fun `test function call return value`() = doTest("""
        fn main() {
            let a: i32;
            a = foo();
        }
        fn foo() -> i32 { 0 }
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:19: 2:19
            let _1: i32;                         // in scope 0 at src/main.rs:3:17: 3:18
            let mut _2: i32;                     // in scope 0 at src/main.rs:4:17: 4:22
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:3:17: 3:18
            }

            bb0: {                               // {_0, _1, _2}
                StorageLive(_1);
                StorageLive(_2);
                _2 = foo() -> [return: bb1, unwind: bb2];
            }                                    // {_0, _1, _2}

            bb1: {                               // {_0, _1}
                _1 = move _2;                    // +_2, -_1
                StorageDead(_2);
                _0 = const ();                   // -_0
                StorageDead(_1);                 // +_1
                return;
            }                                    // {_1, _2}

            bb2 (cleanup): {                     // {_0, _1, _2}
                resume;
            }                                    // {_0, _1, _2}
        }
    """)

    fun `test function call moves argument`() = doTest("""
        struct S;
        fn main() {
            let a = S;
            foo(a);
        }
        fn foo(a: S) {}
    """, """
        fn main() -> () {
            let mut _0: ();                      // return place in scope 0 at src/main.rs:3:19: 3:19
            let _1: S;                           // in scope 0 at src/main.rs:4:17: 4:18
            let _2: ();                          // in scope 0 at src/main.rs:5:13: 5:19
            let mut _3: S;                       // in scope 0 at src/main.rs:5:17: 5:18
            scope 1 {
                debug a => _1;                   // in scope 1 at src/main.rs:4:17: 4:18
            }

            bb0: {                               // {_0, _1, _2, _3}
                StorageLive(_1);
                _1 = S;                          // -_1
                FakeRead(ForLet(None), _1);
                StorageLive(_2);
                StorageLive(_3);
                _3 = move _1;                    // +_1, -_3
                _2 = foo(move _3) -> [return: bb1, unwind: bb2]; // +_3
            }                                    // {_0, _1, _2, _3}

            bb1: {                               // {_0, _1, _3}
                StorageDead(_3);
                StorageDead(_2);                 // +_2
                _0 = const ();                   // -_0
                StorageDead(_1);
                return;
            }                                    // {_1, _2, _3}

            bb2 (cleanup): {                     // {_0, _1, _2, _3}
                resume;
            }                                    // {_0, _1, _2, _3}
        }
    """)

    fun `test function with arguments`() = doTest("""
        fn foo(n: i32, m: i32, mut k: i32) {
            k = n;
        }
    """, """
        fn foo(_1: i32, _2: i32, _3: i32) -> () {
            debug n => _1;                       // in scope 0 at src/main.rs:2:16: 2:17
            debug m => _2;                       // in scope 0 at src/main.rs:2:24: 2:25
            debug k => _3;                       // in scope 0 at src/main.rs:2:32: 2:37
            let mut _0: ();                      // return place in scope 0 at src/main.rs:2:44: 2:44
            let mut _4: i32;                     // in scope 0 at src/main.rs:3:17: 3:18

            bb0: {                               // {_0, _4}
                StorageLive(_4);
                _4 = _1;                         // -_4
                _3 = move _4;                    // +_4
                StorageDead(_4);
                _0 = const ();                   // -_0
                return;
            }                                    // {_4}
        }
    """)
}
