mod m {
    pub struct Matrix<T> {
        data: Vec<T>
    }

    pub fn apply(){
        let _ = <caret>Matrix::<f32>::fill();
    }
}
