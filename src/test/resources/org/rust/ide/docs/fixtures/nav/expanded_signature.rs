/// Docs
#[cfg(test)]
/// More Docs
pub const unsafe extern "C" fn foo<T, U, V>(
 x: T,
 y: u32,
 z: u64
) -> (
 u32,
 u64,
 u64
) where T: Clone,
        U: Debug,
        V: Display
{
    92
}

fn main() {
    <caret>foo()
}
