package es.farcompeticiones;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Lectura prudente de la clasificación oficial.
 * No declara plazas matemáticas sin calendario completo y desempates verificados.
 */
final class StandingIntelligence {
    private StandingIntelligence() {}

    static final class Team {
        final String name;
        final int position, points, played;
        Team(String name, int position, int points, int played) {
            this.name=name;this.position=position;this.points=points;this.played=played;
        }
    }

    private static int number(String text) {
        try { return Integer.parseInt(text.trim().replaceAll("[^0-9-]", "")); }
        catch (Exception ignored) { return -1; }
    }

    static ArrayList<Team> teams(JSONArray rows) {
        ArrayList<Team> result=new ArrayList<>();
        if (rows==null) return result;
        for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if (row==null) continue;
            int rank=number(row.optString("position",""));
            int points=number(row.optString("points",""));
            int played=number(row.optString("played",""));
            if (rank>0 && points>=0 && played>=0)
                result.add(new Team(row.optString("team",""),rank,points,played));
        }
        Collections.sort(result,Comparator.comparingInt(t->t.position));
        return result;
    }

    /** Solo distancias actuales en puntos, no pronósticos ni adjudicaciones. */
    static String summary(String season,String category,String competition,int phase,JSONArray rows) {
        if (!CompetitionRules.hasM14Rules(season,category) || phase!=1) return "";
        int cutoff;
        if ("Primera División".equals(competition)) cutoff=5;
        else if (competition.startsWith("Segunda División · Grupo")) cutoff=3;
        else return "";
        ArrayList<Team> all=teams(rows);
        if (all.size()<cutoff+1) return "";
        Team inside=all.get(cutoff-1),outside=all.get(cutoff);
        int gap=inside.points-outside.points;
        String destination="Primera División".equals(competition)?"acceso a Primera":"acceso a Segunda A";
        return "Corte provisional de "+destination+": "+inside.name+" ("+inside.points+
                " pts) / "+outside.name+" ("+outside.points+" pts). "+
                "Diferencia actual: "+gap+" punto"+(gap==1?"":"s")+
                ". No implica clasificación matemática.";
    }
}
