type FunType = Fn(f64) -> f64;

type Sum = Box<A + Copy>;

type FunSum = Box<Fn(f64, f64) -> f64 + Send + Sync>

type Shl = Vec<Vec<f64>>;