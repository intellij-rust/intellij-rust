/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class FlattenUseStatementsIntentionTest : RsIntentionTestBase(FlattenUseStatementsIntention()) {
    fun `test use statement with alias`() = doAvailableTest("""
        use std::collections::{
            /*caret*/HashMap as HM,
            HashSet as HS
        };

    """, """
        use /*caret*/std::collections::HashMap as HM;
        use std::collections::HashSet as HS;
    """)

    fun `test attributes and visibility`() = doAvailableTest("""
        #[cfg(foo)]
        pub use std::collections::{/*caret*/HashMap, HashSet};
    """, """
        #[cfg(foo)]
        pub use /*caret*/std::collections::HashMap;
        #[cfg(foo)]
        pub use std::collections::HashSet;
    """)

    fun `test multiple attributes`() = doAvailableTest("""
        #[a]
        #[b]
        #[c]
        use std::collections::{/*caret*/HashMap, HashSet};
    """, """
        #[a]
        #[b]
        #[c]
        use /*caret*/std::collections::HashMap;
        #[a]
        #[b]
        #[c]
        use std::collections::HashSet;
    """)

    fun `test simple use statements`() = doAvailableTest("""
        use std::{
            /*caret*/error,
            io
        };

    """, """
        use /*caret*/std::error;
        use std::io;
    """)

    fun `test caret last path`() = doAvailableTest("""
        use std::{
            error,
            /*caret*/io
        };

    """, """
        use /*caret*/std::error;
        use std::io;
    """)

    fun `test example use statements`() = doAvailableTest("""
        use std::{
            /*caret*/error::Error,
            io::Write,
            path::PathBuf
        };

    """, """
        use /*caret*/std::error::Error;
        use std::io::Write;
        use std::path::PathBuf;
    """)

    fun `test nest statements which only be able to nested with selected one`() = doAvailableTest("""
        use std::{
            /*caret*/error::Error,
            io::Write
        };
        use quux::spam;
        use quux::eggs;
    """, """
        use /*caret*/std::error::Error;
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path`() = doAvailableTest("""
        use std::{
            sync::{
                /*caret*/mpsc,
                Arc
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::mpsc,
            sync::Arc,
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path without last comma`() = doAvailableTest("""
        use std::{
            sync::{
                /*caret*/mpsc,
                Arc
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::mpsc,
            sync::Arc,
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path with last position of caret`() = doAvailableTest("""
        use std::{
            sync::{
                /*caret*/mpsc,
                Arc
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::mpsc,
            sync::Arc,
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path with many useSpeck`() = doAvailableTest("""
        use std::{
            sync::{
                /*caret*/mpsc,
                Arc,
                B,
                C,
                D,
                E,
                F,
                G
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::mpsc,
            sync::Arc,
            sync::B,
            sync::C,
            sync::D,
            sync::E,
            sync::F,
            sync::G,
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test very nested inner path`() = doAvailableTest("""
        use std::{
            b1::{
                c1::{
                    d1::{
                        f::{
                            /*caret*/x,
                            y
                        }
                    },
                    d2
                },
                c2
            },
            b2
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            b1::{
                c1::{
                    d1::{
                        /*caret*/f::x,
                        f::y,
                    },
                    d2
                },
                c2
            },
            b2
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test very nested inner path but caret is middle of it`() = doAvailableTest("""
        use std::{
            b1::{
                c1::{
                    d1::{
                        /*caret*/f::g1,
                        f::g2
                    },
                    d2,
                    d3,
                    d4
                },
                c2::d5
            },
            b2
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            b1::{
                c1::{
                    d1::f::g1,
                    d1::f::g2,
                    d2,
                    d3,
                    d4
                },
                c2::d5
            },
            b2
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test cannot nest if non common base path exist`() = doAvailableTest("""
        use a1::/*caret*/b::c;
        use a2::b::c;
    """, """
        use a1::/*caret*/b::c;
        use a2::b::c;
    """)

    fun `test starts with colon colon`() = doAvailableTest("""
        use ::a1::{
            /*caret*/b1::c,
            b2::c
        };

    """, """
        use /*caret*/::a1::b1::c;
        use ::a1::b2::c;
    """)

    fun `test starts with colon colon with no colon colon`() = doAvailableTest("""
        use ::a1/*caret*/::b::c;
        use a1::b::c;
    """, """
        use ::a1/*caret*/::b::c;
        use a1::b::c;
    """)

    fun `test converts self to module`() = doAvailableTest("""
        use foo::{
            /*caret*/self,
            foo,
            bar
        };

    """, """
        use foo;
        use foo::foo;
        use foo::bar;
    """)
}
