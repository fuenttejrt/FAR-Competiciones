package es.farcompeticiones;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Reglas por temporada y categoria. Nunca aplicar una circular a otra temporada.
 * Las posiciones son orientativas hasta que la FAR confirme los resultados.
 */
final class CompetitionRules {
    private CompetitionRules() {}

    static boolean hasM14Rules(String season, String category) {
        return "2026/27".equals(season) && "M14".equals(category);
    }

    /** Duración confirmada para el formato ordinario de esta competición; -1 = sin verificar. */
    static int matchMinutes(String season, String category, String competition) {
        if ("2026/27".equals(season) && "M14".equalsIgnoreCase(category)
                ) return 36;
        return -1;
    }

    /** Mitad de partido, solo cuando la duración oficial está verificada. */
    static int halfMinutes(String season, String category, String competition) {
        int total = matchMinutes(season, category, competition);
        return total > 0 ? total / 2 : -1;
    }

    /**
     * FER, Variaciones a las Reglas M14 (noviembre 2021), regla 9.29:
     * suspensión por amarilla = 5 minutos de tiempo de juego.
     * La circular FAR 10/2026-27 remite a las variaciones M14.
     * -1 para temporadas/categorías sin norma contrastada.
     */
    static int temporarySuspensionMinutes(String season,String category){
        return hasM14Rules(season,category)?5:-1;
    }

    /** 0: no confirmada, 1: primera fase, 2: segunda fase. */
    static int phase(String season, String category, String competition, JSONObject data, JSONObject standing) {
        if (!hasM14Rules(season, category)) return 0;
        String name = competition.toLowerCase(Locale.ROOT);
        if (name.contains("segunda división a") || name.contains("segunda división b")
                || name.contains("segunda a") || name.contains("segunda b")) return 2;
        String heading = standing.optString("heading", "").toLowerCase(Locale.ROOT);
        if (heading.matches("(?s).*(?:2[.ªº ]*fase|segunda fase).*")) return 2;
        if (heading.matches("(?s).*(?:1[.ªº ]*fase|primera fase).*")) return 1;
        JSONArray all = data.optJSONArray("tables");
        boolean first = false, second = false;
        if (all != null) for (int i = 0; i < all.length(); i++) {
            JSONObject table = all.optJSONObject(i);
            if (table == null || !"matches".equals(table.optString("kind"))) continue;
            String h = table.optString("heading", "").toLowerCase(Locale.ROOT);
            if (h.matches("(?s).*(?:1[.ªº ]*fase|primera fase).*")) first = true;
            if (h.matches("(?s).*(?:2[.ªº ]*fase|segunda fase).*")) second = true;
        }
        if (first && !second) return 1;
        if (second && !first) return 2;
        return 0;
    }

    static String positionGuide(String season, String category, String competition, int phase, int rank) {
        if (!hasM14Rules(season, category) || rank < 1) return "";
        if (phase == 1) {
            if ("Primera División".equals(competition)) {
                if (rank <= 5) return "Referencia 1.ª fase: permanencia en Primera";
                if (rank == 6) return "Referencia 1.ª fase: triangular de promoción";
            }
            if (competition.startsWith("Segunda División · Grupo")) {
                if (rank == 1) return "Referencia 1.ª fase: triangular de promoción";
                if (rank == 2 || rank == 3) return "Referencia 1.ª fase: Segunda A";
                if (rank <= 6) return "Referencia 1.ª fase: Segunda B";
            }
        }
        if (phase == 2) {
            String name = competition.toLowerCase(Locale.ROOT);
            if (name.contains("primera división") && rank == 6) return "promoción final";
            if ((name.contains("segunda división a") || name.contains("segunda a")) && rank == 1) return "promoción final";
        }
        return "";
    }
}
