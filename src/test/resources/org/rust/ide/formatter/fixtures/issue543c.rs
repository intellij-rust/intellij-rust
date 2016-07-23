fn main() {
    if previous_end < token.start {
        space_tokens.push((
            insert_at,
            Token::new(Rule::text, previous_end, token.start)
        ));
    }
}
