struct F<
    const N0: S1 = S1,
    const N1: usize = 1,
    const N2: usize = { 1 + 1 },
    const N3: usize = 1 + 1,
    const N4: [usize; 1] = [1],
    const N5: usize = 1 as usize,
    const N6: usize = foo(42),
    const N7: S2 = S2 { x: 1 },
    const N8: usize = C.0,
    const N9: isize = -1,
    const N10: bool = !true
>();

fn main() {
    F::<{ S1 }, 1, { 1 + 1 }, 1 + 1, { [1] }, 1 as usize, { foo(42) }, { S2 { x: 1 } }, { C.0 }, -1, { !true }>();
}
