package com.alkhoudeir.athantv;

/**
 * Fixed religious texts shown on screen.
 *
 * Adhan wording follows the Sunni adhan as tabulated on Wikipedia ("Adhan"); each phrase is
 * listed once because the number of repetitions differs slightly between schools.
 * The after-adhan supplication is from Sahih al-Bukhari 614 (sunnah.com/bukhari:614);
 * the English line is our own plain rendering of its meaning.
 */
public final class Texts {

    private Texts() {}

    /** {Arabic, English meaning} pairs in recitation order. */
    public static final String[][] ADHAN = {
            {"اللّٰهُ أَكْبَرُ", "Allah is the Greatest"},
            {"أَشْهَدُ أَنْ لَا إِلٰهَ إِلَّا اللّٰهُ", "I bear witness that there is no god but Allah"},
            {"أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ اللّٰهِ", "I bear witness that Muhammad is the Messenger of Allah"},
            {"حَيَّ عَلَى الصَّلَاةِ", "Come to prayer"},
            {"حَيَّ عَلَى الْفَلَاحِ", "Come to success"},
            {"اللّٰهُ أَكْبَرُ", "Allah is the Greatest"},
            {"لَا إِلٰهَ إِلَّا اللّٰهُ", "There is no god but Allah"},
    };

    /** Inserted after "Come to success" for Fajr. */
    public static final String[] FAJR_ADDITION = {"الصَّلَاةُ خَيْرٌ مِنَ النَّوْمِ", "Prayer is better than sleep"};

    public static final String DUA_TITLE_AR = "الدعاء بعد الأذان";
    public static final String DUA_TITLE_EN = "Supplication after the athan";

    public static final String DUA_AR =
            "اللَّهُمَّ رَبَّ هَذِهِ الدَّعْوَةِ التَّامَّةِ وَالصَّلَاةِ الْقَائِمَةِ، "
                    + "آتِ مُحَمَّدًا الْوَسِيلَةَ وَالْفَضِيلَةَ، "
                    + "وَابْعَثْهُ مَقَامًا مَحْمُودًا الَّذِي وَعَدْتَهُ";

    public static final String DUA_EN =
            "O Allah, Lord of this complete call and of the prayer about to begin, grant Muhammad "
                    + "al-Wasilah and al-Fadilah, and raise him to the praised station You promised him.";

    public static final String DUA_SOURCE = "Sahih al-Bukhari 614";
}
