// Although enum variants are always implicitly public,
// we parse visibility for them to produce semantic [E0449] error as the compiler does
enum E {
    pub V1,
    pub(crate) V2,
    pub(in a) V3
}
