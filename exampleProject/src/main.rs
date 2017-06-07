pub trait Registry {
    fn query(&mut self,
             dep: &Dependency,
             f: &mut FnMut(Summary)) -> CargoResult<()>;
}
