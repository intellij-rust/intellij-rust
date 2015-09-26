// Trait returns whatever is inside.
trait Contains {
    type A;
    // Return inner element.
    fn inner(&self) -> Self::A;
}