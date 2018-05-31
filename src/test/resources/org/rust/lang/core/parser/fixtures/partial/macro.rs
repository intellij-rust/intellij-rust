macro_rules! newtype_index {
    ($()* ${'$'}type:ident) => (
        pub struct ${'$'}type(usize);
    );
}

