struct S1;
pub struct S2;
pub(crate) struct S3;
pub(self) struct S4;
mod a {
    pub (super) struct S5;
    mod b {
        pub(in super::super) struct S6;
    }
}
