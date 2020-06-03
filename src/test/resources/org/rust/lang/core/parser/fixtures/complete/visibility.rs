struct S1;
pub struct S2;
pub(crate) struct S3;
pub(self) struct S4;
mod a {
    pub (super) struct S5;
    pub(in a) struct S6;
    mod b {
        pub(in super::super) struct S7;
        // Syntactically invalid
        pub(a::b) struct S8;
    }
}
crate struct S9;

struct S10(crate ::S1); // path `crate::S1`
struct S11(crate S1); // vis `crate`

crate::macro1!();
