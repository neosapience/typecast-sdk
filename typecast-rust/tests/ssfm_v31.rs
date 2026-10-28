use typecast_rust::TTSModel;

#[test]
fn v31_round_trip_keeps_the_existing_default() {
    assert_eq!(
        serde_json::to_string(&TTSModel::SsfmV31).unwrap(),
        "\"ssfm-v31\""
    );
    assert_eq!(
        serde_json::from_str::<TTSModel>("\"ssfm-v31\"").unwrap(),
        TTSModel::SsfmV31
    );
    assert_eq!(TTSModel::default(), TTSModel::SsfmV30);
}
