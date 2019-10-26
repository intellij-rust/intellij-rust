// False positive found during code review
let comment = Regex::new(r"(?m)^\s*//.*$").unwrap();


// From https://doc.rust-lang.org/reference.html#raw-string-literals
"foo"; r"foo";                     // foo
"\"foo\""; r#""foo""#;             // "foo"

"foo #\"# bar";
r##"foo #"# bar"##;                // foo #"# bar

"\x52"; "R"; r"R";                 // R
"\\x52"; r"\x52";                  // \x52


// From https://doc.rust-lang.org/reference.html#raw-byte-string-literals
b"foo"; br"foo";                     // foo
b"\"foo\""; br#""foo""#;             // "foo"

b"foo #\"# bar";
br##"foo #"# bar"##;                 // foo #"# bar

b"\x52"; b"R"; br"R";                // R
b"\\x52"; br"\x52";                  // \x52
