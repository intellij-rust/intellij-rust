struct S { f: i32 }

fn main() {
    if if true { S {f:1}; true } else { S {f:1}; false } {
        ()
    } else {
        ()
    };

    if {S {f:1}; let _ = S {f:1}; true} {()};

    if { 1 } == 1 { 1; }
    if unsafe { 0 } == 0 { 0; }
}
