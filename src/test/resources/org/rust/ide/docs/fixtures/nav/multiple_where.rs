/// Docs
#[cfg(test)]
/// More Docs
pub const unsafe extern "C" fn foo<T, U, V>(x: T) -> u32 where T: Clone,
                                                               U: Debug,
                                                               V: Display
{
    92
}

fn main() {
    <caret>foo()
}
