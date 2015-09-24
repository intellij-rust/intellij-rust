type T = Fn(f64) -> f64;

type S = Box<A + Copy>;

type U = Box<Fn(f64, f64) -> f64 + Send + Sync>