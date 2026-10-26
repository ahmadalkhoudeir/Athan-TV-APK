import com.alkhoudeir.athantv.core.PrayerCalc;
import com.alkhoudeir.athantv.core.PrayerCalc.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class CalcTest {
    static int total = 0, exact = 0, worst = 0;
    static Map<String,Integer> worstBy = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        run(a[0] + "/isna_newbritain_2026-10.csv", 41.6612, -72.7795, "America/New_York", Method.ISNA, Madhab.STANDARD);
        run(a[0] + "/mwl_hanafi_newbritain_2026-06.csv", 41.6612, -72.7795, "America/New_York", Method.MWL, Madhab.HANAFI);
        run(a[0] + "/makkah_ummalqura_2026-03.csv", 21.4225, 39.8262, "Asia/Riyadh", Method.MAKKAH, Madhab.STANDARD);
        System.out.printf("ALL: %d values, %d exact (%.1f%%), worst diff %d min%n", total, exact, 100.0*exact/total, worst);
        System.out.printf("Qibla New Britain CT: %.2f deg, %.0f km%n",
            PrayerCalc.qiblaBearing(41.6612, -72.7795), PrayerCalc.distanceToKaabaKm(41.6612, -72.7795));
        System.out.printf("Qibla New York City (40.7128,-74.0060): %.2f deg%n", PrayerCalc.qiblaBearing(40.7128, -74.0060));
        System.out.printf("Qibla London (51.5074,-0.1278): %.2f deg%n", PrayerCalc.qiblaBearing(51.5074, -0.1278));
        // DST transition check: US clocks fall back on 1 Nov 2026
        for (String d : new String[]{"2026-10-31","2026-11-01","2026-11-02"}) {
            DayTimes t = PrayerCalc.compute(LocalDate.parse(d), 41.6612, -72.7795, ZoneId.of("America/New_York"), Method.ISNA, Madhab.STANDARD, HighLatRule.ANGLE_BASED, false);
            System.out.println("DST check " + d + " Fajr " + t.get(Event.FAJR).toLocalTime() + " Dhuhr " + t.get(Event.DHUHR).toLocalTime() + " " + t.get(Event.DHUHR).getOffset());
        }
        if (worst > 1) System.exit(1);
    }

    static void run(String file, double lat, double lng, String tz, Method m, Madhab mad) throws Exception {
        String[] names = {"Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"};
        int fileWorst = 0, fileExact = 0, fileTotal = 0;
        for (String line : Files.readAllLines(Paths.get(file))) {
            String[] p = line.split(",");
            String[] dmy = p[0].split("-");
            LocalDate date = LocalDate.of(Integer.parseInt(dmy[2]), Integer.parseInt(dmy[1]), Integer.parseInt(dmy[0]));
            boolean ramadan = Integer.parseInt(p[7]) == 9;
            DayTimes t = PrayerCalc.compute(date, lat, lng, ZoneId.of(tz), m, mad, HighLatRule.ANGLE_BASED, ramadan);
            for (int i = 0; i < 6; i++) {
                LocalTime ref = LocalTime.parse(p[i + 1]);
                LocalTime got = t.get(Event.values()[i]).toLocalTime();
                int diff = Math.abs(got.toSecondOfDay() - ref.toSecondOfDay()) / 60;
                total++; fileTotal++;
                if (diff == 0) { exact++; fileExact++; }
                if (diff > 0) System.out.println("  diff " + diff + " min: " + p[0] + " " + names[i] + " ours=" + got + " aladhan=" + ref);
                fileWorst = Math.max(fileWorst, diff);
            }
        }
        worst = Math.max(worst, fileWorst);
        System.out.printf("%s: %d/%d exact, worst %d min%n", Paths.get(file).getFileName(), fileExact, fileTotal, fileWorst);
    }
}
