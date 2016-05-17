/// Outer comment
/// 111
#[doc = "outer attribute"]
/// Second outer comment
/// 222
#[doc = "second outer attribute"]
fn <caret>overly_documented() {
    #![doc = "inner attribute"]
    //! Inner comment
}


