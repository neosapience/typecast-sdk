use typecast_rust::{Output, OutputStream};

#[test]
fn optional_zero_and_range() {
    assert!(serde_json::to_value(Output::new()).unwrap().get("remove_silence_ms").is_none());
    assert!(serde_json::to_value(OutputStream::new()).unwrap().get("remove_silence_ms").is_none());
    for value in [0, 300, 1000] {
        assert_eq!(serde_json::to_value(Output::new().remove_silence_ms(value)).unwrap()["remove_silence_ms"], value);
        assert_eq!(serde_json::to_value(OutputStream::new().remove_silence_ms(value)).unwrap()["remove_silence_ms"], value);
    }
    assert!(serde_json::to_value(Output::new().remove_silence_ms(1001)).is_err());
    assert!(serde_json::to_value(OutputStream::new().remove_silence_ms(1001)).is_err());
}
