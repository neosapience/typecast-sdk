package io.typecast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TTS model version to use for speech synthesis.
 */
@Serializable
enum class TTSModel(val value: String) {
    @SerialName("ssfm-v30")
    SSFM_V30("ssfm-v30"),
    
    @SerialName("ssfm-v21")
    SSFM_V21("ssfm-v21");
    
    override fun toString(): String = value
}

/**
 * Available emotion presets for speech synthesis.
 */
@Serializable
enum class EmotionPreset(val value: String) {
    @SerialName("normal")
    NORMAL("normal"),
    
    @SerialName("happy")
    HAPPY("happy"),
    
    @SerialName("sad")
    SAD("sad"),
    
    @SerialName("angry")
    ANGRY("angry"),
    
    @SerialName("whisper")
    WHISPER("whisper"),
    
    @SerialName("toneup")
    TONEUP("toneup"),
    
    @SerialName("tonedown")
    TONEDOWN("tonedown");
    
    override fun toString(): String = value
}

/**
 * Audio output format.
 */
@Serializable
enum class AudioFormat(val value: String) {
    @SerialName("wav")
    WAV("wav"),
    
    @SerialName("mp3")
    MP3("mp3");
    
    override fun toString(): String = value
}

/**
 * Gender classification for voices.
 */
@Serializable
enum class GenderEnum(val value: String) {
    @SerialName("male")
    MALE("male"),
    
    @SerialName("female")
    FEMALE("female");
    
    override fun toString(): String = value
}

/**
 * Age group classification for voices.
 */
@Serializable
enum class AgeEnum(val value: String) {
    @SerialName("child")
    CHILD("child"),
    
    @SerialName("teenager")
    TEENAGER("teenager"),
    
    @SerialName("young_adult")
    YOUNG_ADULT("young_adult"),
    
    @SerialName("middle_age")
    MIDDLE_AGE("middle_age"),
    
    @SerialName("elder")
    ELDER("elder");
    
    override fun toString(): String = value
}

/**
 * Use case categories for voices.
 */
@Serializable
enum class UseCaseEnum(val value: String) {
    @SerialName("Announcer")
    ANNOUNCER("Announcer"),
    
    @SerialName("Anime")
    ANIME("Anime"),
    
    @SerialName("Audiobook")
    AUDIOBOOK("Audiobook"),
    
    @SerialName("Conversational")
    CONVERSATIONAL("Conversational"),
    
    @SerialName("Documentary")
    DOCUMENTARY("Documentary"),
    
    @SerialName("E-learning")
    E_LEARNING("E-learning"),
    
    @SerialName("Rapper")
    RAPPER("Rapper"),
    
    @SerialName("Game")
    GAME("Game"),
    
    @SerialName("Tiktok/Reels")
    TIKTOK_REELS("Tiktok/Reels"),
    
    @SerialName("News")
    NEWS("News"),
    
    @SerialName("Podcast")
    PODCAST("Podcast"),
    
    @SerialName("Voicemail")
    VOICEMAIL("Voicemail"),
    
    @SerialName("Ads")
    ADS("Ads");
    
    override fun toString(): String = value
}

/**
 * Language code following ISO 639-3 standard.
 */
@Serializable
enum class LanguageCode(val value: String) {
    @SerialName("eng")
    ENG("eng"),
    
    @SerialName("kor")
    KOR("kor"),
    
    @SerialName("jpn")
    JPN("jpn"),
    
    @SerialName("spa")
    SPA("spa"),
    
    @SerialName("deu")
    DEU("deu"),
    
    @SerialName("fra")
    FRA("fra"),
    
    @SerialName("ita")
    ITA("ita"),
    
    @SerialName("pol")
    POL("pol"),
    
    @SerialName("nld")
    NLD("nld"),
    
    @SerialName("rus")
    RUS("rus"),
    
    @SerialName("ell")
    ELL("ell"),
    
    @SerialName("tam")
    TAM("tam"),
    
    @SerialName("tgl")
    TGL("tgl"),
    
    @SerialName("fin")
    FIN("fin"),
    
    @SerialName("zho")
    ZHO("zho"),
    
    @SerialName("slk")
    SLK("slk"),
    
    @SerialName("ara")
    ARA("ara"),
    
    @SerialName("hrv")
    HRV("hrv"),
    
    @SerialName("ukr")
    UKR("ukr"),
    
    @SerialName("ind")
    IND("ind"),
    
    @SerialName("dan")
    DAN("dan"),
    
    @SerialName("swe")
    SWE("swe"),
    
    @SerialName("msa")
    MSA("msa"),
    
    @SerialName("ces")
    CES("ces"),
    
    @SerialName("por")
    POR("por"),
    
    @SerialName("bul")
    BUL("bul"),
    
    @SerialName("ron")
    RON("ron"),
    
    // ssfm-v30 additional languages
    @SerialName("ben")
    BEN("ben"),
    
    @SerialName("hin")
    HIN("hin"),
    
    @SerialName("hun")
    HUN("hun"),
    
    @SerialName("nan")
    NAN("nan"),
    
    @SerialName("nor")
    NOR("nor"),
    
    @SerialName("pan")
    PAN("pan"),
    
    @SerialName("tha")
    THA("tha"),
    
    @SerialName("tur")
    TUR("tur"),
    
    @SerialName("vie")
    VIE("vie"),
    
    @SerialName("yue")
    YUE("yue");
    
    override fun toString(): String = value
}
