package com.neosapience.models;

/**
 * ISO 639-3 language codes supported by Typecast TTS.
 * 
 * <p>SSFM v2.1 supports 27 languages, SSFM v3.0 supports all 37 languages.</p>
 */
public enum LanguageCode {
    /** English */
    ENG("eng"),
    /** Korean */
    KOR("kor"),
    /** Spanish */
    SPA("spa"),
    /** German */
    DEU("deu"),
    /** French */
    FRA("fra"),
    /** Italian */
    ITA("ita"),
    /** Polish */
    POL("pol"),
    /** Dutch */
    NLD("nld"),
    /** Russian */
    RUS("rus"),
    /** Japanese */
    JPN("jpn"),
    /** Greek */
    ELL("ell"),
    /** Tamil */
    TAM("tam"),
    /** Tagalog */
    TGL("tgl"),
    /** Finnish */
    FIN("fin"),
    /** Chinese (Mandarin) */
    ZHO("zho"),
    /** Slovak */
    SLK("slk"),
    /** Arabic */
    ARA("ara"),
    /** Croatian */
    HRV("hrv"),
    /** Ukrainian */
    UKR("ukr"),
    /** Indonesian */
    IND("ind"),
    /** Danish */
    DAN("dan"),
    /** Swedish */
    SWE("swe"),
    /** Malay */
    MSA("msa"),
    /** Czech */
    CES("ces"),
    /** Portuguese */
    POR("por"),
    /** Bulgarian */
    BUL("bul"),
    /** Romanian */
    RON("ron"),
    /** Bengali */
    BEN("ben"),
    /** Hindi */
    HIN("hin"),
    /** Hungarian */
    HUN("hun"),
    /** Min Nan Chinese */
    NAN("nan"),
    /** Norwegian */
    NOR("nor"),
    /** Punjabi */
    PAN("pan"),
    /** Thai */
    THA("tha"),
    /** Turkish */
    TUR("tur"),
    /** Vietnamese */
    VIE("vie"),
    /** Cantonese */
    YUE("yue");

    private final String value;

    LanguageCode(String value) {
        this.value = value;
    }

    /**
     * Returns the ISO 639-3 code.
     * 
     * @return the language code string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a LanguageCode from an ISO 639-3 string.
     * 
     * @param value the ISO 639-3 code
     * @return the corresponding LanguageCode
     * @throws IllegalArgumentException if the code is not supported
     */
    public static LanguageCode fromValue(String value) {
        for (LanguageCode code : values()) {
            if (code.value.equalsIgnoreCase(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown language code: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
