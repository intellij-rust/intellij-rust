/// Trait for objects that can execute commands.
pub trait ExecEngine: Send + Sync {
    fn exec(&self, CommandPrototype) -> Result<(), ProcessError>;
    fn exec_with_output(&self, CommandPrototype) -> Result<Output, ProcessError>;
}

trait A {
    type B;
    const C: i32;
    const D: f64 = 92.92;
}

pub trait Registry {
    /// Attempt to find the packages that match a dependency request.
    fn query(&mut self, name: &Dependency) -> CargoResult<Vec<Summary>>;
}

trait FnBox<A, R> {
    fn call_box(self: Box<Self>, a: A) -> R;
}

trait T { }
trait P<X> { }
