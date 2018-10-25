/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class NestUseStatementsIntentionTest : RsIntentionTestBase(NestUseStatementsIntention()) {
    fun `test simple use statements`() = doAvailableTest("""
        use /*caret*/std::error;
        use std::io;
    """, """
        use /*caret*/std::{
            error,
            io
        };

    """)

    fun `test caret last path`() = doAvailableTest("""
        use std::error/*caret*/;
        use std::io;
    """, """
        use /*caret*/std::{
            error,
            io
        };

    """)

    fun `test example use statements`() = doAvailableTest("""
        use /*caret*/std::error::Error;
        use std::io::Write;
        use std::path::PathBuf;
    """, """
        use /*caret*/std::{
            error::Error,
            io::Write,
            path::PathBuf
        };

    """)

    fun `test nest statements which only be able to nested with selected one`() = doAvailableTest("""
        use /*caret*/std::error::Error;
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use /*caret*/std::{
            error::Error,
            io::Write
        };
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path`() = doAvailableTest("""
        use std::{
            sync/*caret*/::mpsc,
            sync::Arc
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::{
                mpsc,
                Arc
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path without last comma`() = doAvailableTest("""
        use std::{
            sync/*caret*/::mpsc,
            sync::Arc
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::{
                mpsc,
                Arc
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path with last position of caret`() = doAvailableTest("""
        use std::{
            sync::mpsc,
            sync::Ar/*caret*/c
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::{
                mpsc,
                Arc
            }
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """)

    fun `test inner path with many useSpeck`() = doAvailableTest("""
        use std::{
            sync::mpsc,
            sync::Ar/*caret*/c,
            sync::B,
            sync::C,
            sync::D,
            sync::E,
            sync::F,
            sync::G
        };
        use std::io::Write;
        use quux::spam;
        use quux::eggs;
    """, """
        use std::{
            /*caret*/sync::{
                mpsc,
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
    """)

    fun `test very nested inner path`() = doAvailableTest("""
        use std::{
            b1::{
                c1::{
                    d1::{
                        f::/*caret*/x,
                        f::y
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
                        /*caret*/f::{
                            x,
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
    """)

    fun `test very nested inner path but caret is middle of it`() = doAvailableTest("""
        use std::{
            b1::{
                c1/*caret*/::{
                    d1::{
                        f::g1,
                        f::g2
                    },
                    d2
                },
                c1::x,
                c1::y,
                c2::z
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
                        f::g1,
                        f::g2
                    },
                    d2,
                    x,
                    y
                },
                c2::z
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
        use ::a1/*caret*/::b1::c;
        use ::a1::b2::c;
    """, """
        use /*caret*/::a1::{
            b1::c,
            b2::c
        };

    """)

    fun `test starts with colon colon with no colon colon`() = doAvailableTest("""
        use ::a1/*caret*/::b::c;
        use a1::b::c;
    """, """
        use ::a1/*caret*/::b::c;
        use a1::b::c;
    """)
}
