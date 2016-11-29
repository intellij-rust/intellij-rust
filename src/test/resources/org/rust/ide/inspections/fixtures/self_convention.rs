struct T;

impl T {
    fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self</warning>) -> T { T() }

    fn into_u32(self) -> u32 { 0 }
    fn into_u16(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">&self</warning>) -> u16 { 0 }

    fn <warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">into_without_self</warning>() -> u16 { 0 }

    fn to_something(<warning descr="methods called `to_*` usually take self by reference; consider choosing a less ambiguous name">self</warning>) -> u32 { 0 }

    fn is_awesome(<warning descr="methods called `is_*` usually take self by reference or no self; consider choosing a less ambiguous name">self</warning>) {}
}

#[derive(Copy)]
struct Copyable;
impl Copyable {
    fn is_ok(self) {}
}
