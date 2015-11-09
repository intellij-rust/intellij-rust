/// Default implementation of `ExecEngine`.
#[derive(Clone, Copy)]
pub struct ProcessEngine;

pub struct Numbers(f64, i8);

/// Prototype for a command that must be executed.
#[derive(Clone)]
pub struct CommandPrototype {
    ty: CommandType,
    builder: ProcessBuilder,
}
