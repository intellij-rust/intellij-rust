pub trait Duplicator {
    fn duplicate(self) -> (Self, Self) where Self: Sized;
}
