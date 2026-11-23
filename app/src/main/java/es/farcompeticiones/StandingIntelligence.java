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
    private static String key(String name) {
        if (name==null) return "";
        String normalized=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","");
        return normalized.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
    }

    private static boolean score(String value) {
        return value!=null && value.matches("(?s).*\\d+\\s*[-–:]\\s*\\d+.*");
    }

    /** Detalle auditable de encuentros sin marcador, tal como aparecen en MatchReady. */
    static String pendingMatches(String season,String category,String competition,int phase,
                                 JSONArray standings,JSONObject data) {
        if(!CompetitionRules.hasM14Rules(season,category)||phase!=1)return "";
        int cutoff="Primera División".equals(competition)?5:
                competition.startsWith("Segunda División · Grupo")?3:0;
        ArrayList<Team> all=teams(standings);
        if(cutoff==0||all.size()<=cutoff)return "";
        String[] targets={all.get(cutoff-1).name,all.get(cutoff).name};
        JSONArray tables=data.optJSONArray("tables");
        if(tables==null)return "";
        StringBuilder result=new StringBuilder();
        for(String target:targets){
            java.util.HashSet<String> unique=new java.util.HashSet<>();
            StringBuilder matches=new StringBuilder();
            for(int i=0;i<tables.length();i++){
                JSONObject table=tables.optJSONObject(i);
                if(table==null||!"matches".equals(table.optString("kind")))continue;
                String heading=table.optString("heading","");
                String low=heading.toLowerCase(java.util.Locale.ROOT);
                if(low.matches("(?s).*(?:2[.ªº ]*fase|segunda fase).*"))continue;
                JSONArray games=table.optJSONArray("rows");
                if(games==null)continue;
                for(int j=0;j<games.length();j++){
                    JSONObject game=games.optJSONObject(j);
                    if(game==null||score(game.optString("score","")))continue;
                    String home=game.optString("home",""),away=game.optString("away","");
                    if(!key(target).equals(key(home))&&!key(target).equals(key(away)))continue;
                    String id=key(home)+"|"+key(away)+"|"+game.optString("when","");
                    if(!unique.add(id))continue;
                    String opponent=key(target).equals(key(home))?away:home;
                    if(matches.length()>0)matches.append("\\n");
                    matches.append("• ").append(heading.isEmpty()?"Jornada no identificada":heading)
                           .append(" · ").append(game.optString("when","Fecha sin confirmar"))
                           .append(" · ").append(opponent)
                           .append(key(target).equals(key(home))?" (local)":" (visitante)");
                }
            }
            if(result.length()>0)result.append("\\n");
            result.append(target).append(": ");
            result.append(matches.length()==0?"ningún encuentro sin marcador identificado":matches);
        }
        return result.toString();
    }

    /**
     * Escenarios de puntuación máxima usando solo encuentros aún sin resultado
     * en el calendario oficial descargado. No son pronósticos de puesto.
     */
    static String remainingScenario(String season,String category,String competition,int phase,
                                    JSONArray standings,JSONObject data) {
        if (!CompetitionRules.hasM14Rules(season,category) || phase!=1) return "";
        int cutoff="Primera División".equals(competition)?5:
                competition.startsWith("Segunda División · Grupo")?3:0;
        if(cutoff==0)return "";
        ArrayList<Team> teams=teams(standings);
        if(teams.size()<=cutoff)return "";
        JSONArray tables=data.optJSONArray("tables");
        if(tables==null)return "";
        java.util.HashMap<String,Integer> pending=new java.util.HashMap<>();
        java.util.HashSet<String> unique=new java.util.HashSet<>();
        int matchTables=0;
        for(int i=0;i<tables.length();i++){
            JSONObject table=tables.optJSONObject(i);
            if(table==null||!"matches".equals(table.optString("kind")))continue;
            String phaseHeading=table.optString("heading","").toLowerCase(java.util.Locale.ROOT);
            // Sin etiqueta explicita de primera fase no podemos garantizar a que fase pertenece.\n            if(!phaseHeading.matches("(?s).*(?:1[.ªº ]*fase|primera fase).*"))continue;
            matchTables++;
            JSONArray games=table.optJSONArray("rows");
            if(games==null)continue;
            for(int j=0;j<games.length();j++){
                JSONObject game=games.optJSONObject(j);
                if(game==null||score(game.optString("score","")))continue;
                String home=key(game.optString("home","")),away=key(game.optString("away",""));
                if(home.isEmpty()||away.isEmpty()||home.equals(away))continue;
                String id=home+"|"+away+"|"+game.optString("when","");
                if(!unique.add(id))continue;

                pending.put(home,pending.containsKey(home)?pending.get(home)+1:1);
                pending.put(away,pending.containsKey(away)?pending.get(away)+1:1);
            }
        }
        if(matchTables==0)return "No se pueden verificar los partidos pendientes: el calendario no identifica expresamente la 1.ª fase.";
        StringBuilder result=new StringBuilder("Escenario de puntos al terminar la 1.ª fase: ");
        for(int i=0;i<teams.size();i++){
            Team team=teams.get(i);
            int remaining=pending.containsKey(key(team.name))?pending.get(key(team.name)):0;
            if(i>0)result.append(" · ");
            result.append(team.name).append(": ").append(remaining)
                  .append(" partidos, ").append(team.points).append("–")
                  .append(team.points+5*remaining).append(" pts");
        }
        result.append(". Intervalos teóricos con hasta 5 puntos por encuentro. ")
              .append("No determinan posiciones ni clasificación matemática: ")
              .append("faltan verificar el calendario completo, los desempates y las resoluciones oficiales.");
        return result.toString();
    }

    /**
     * Cotas conservadoras de puestos: cada rival puede obtener entre 0 y 5 puntos
     * por encuentro pendiente. No resuelve empates ni simula resultados incompatibles.
     */
    static JSONObject compactAnalysis(String season,String category,String competition,int phase,
                                      JSONArray standings,JSONObject data) {
        if(!CompetitionRules.hasM14Rules(season,category)||phase!=1)return null;
        ArrayList<Team> teams=teams(standings);
        if(teams.size()<2)return null;
        JSONArray tables=data.optJSONArray("tables");
        if(tables==null)return null;
        java.util.HashMap<String,Integer> pending=new java.util.HashMap<>();
        java.util.HashSet<String> unique=new java.util.HashSet<>();
        int validTables=0;
        java.util.ArrayList<String[]> fixtures=new java.util.ArrayList<>();
        for(int i=0;i<tables.length();i++){
            JSONObject table=tables.optJSONObject(i);
            if(table==null||!"matches".equals(table.optString("kind")))continue;
            String heading=table.optString("heading","").toLowerCase(java.util.Locale.ROOT);
            if(!heading.matches("(?s).*(?:1[.ªº ]*fase|primera fase).*"))continue;
            validTables++;
            JSONArray games=table.optJSONArray("rows");
            if(games==null)continue;
            for(int j=0;j<games.length();j++){
                JSONObject game=games.optJSONObject(j);
                if(game==null||score(game.optString("score","")))continue;
                String home=key(game.optString("home","")),away=key(game.optString("away",""));
                if(home.isEmpty()||away.isEmpty()||home.equals(away))continue;
                String id=home+"|"+away+"|"+game.optString("when","");
                if(!unique.add(id))continue;
                pending.put(home,pending.containsKey(home)?pending.get(home)+1:1);
                pending.put(away,pending.containsKey(away)?pending.get(away)+1:1);
            }
        }
        if(validTables==0)return null;
        JSONObject result=new JSONObject();
        JSONArray lines=new JSONArray();
        try{
            for(Team team:teams){
                int remaining=pending.containsKey(key(team.name))?pending.get(key(team.name)):0;
                int maximum=team.points+5*remaining;
                int best=1,worst=1;
                for(Team other:teams){
                    if(other==team)continue;
                    int otherRemaining=pending.containsKey(key(other.name))?pending.get(key(other.name)):0;
                    // Cotas matematicas conservadoras: el calendario puede
                    // estrecharse en fases posteriores con un solucionador exacto.
                    if(other.points>=maximum)best++;
                    if(other.points+5*otherRemaining>=team.points)worst++;
                }
                JSONObject line=new JSONObject();
                line.put("team",team.name);
                line.put("points",team.points);
                line.put("remaining",remaining);
                line.put("maximum",maximum);
                line.put("best",best);
                line.put("worst",worst);
                // Escenarios compatibles: un unico resultado para cada partido
                // actualiza simultaneamente a sus dos participantes.
                int observedBest=teams.size(),observedWorst=1;
                java.util.Random random=new java.util.Random(173L);
                int[][] outcomes={{5,0},{4,0},{5,1},{4,1},{2,2},{0,5},{0,4},{1,5},{1,4}};
                for(int trial=0;trial<3000;trial++){
                    java.util.HashMap<String,Integer> totals=new java.util.HashMap<>();
                    for(Team current:teams)totals.put(key(current.name),current.points);
                    for(String[] fixture:fixtures){
                        int[] points=outcomes[random.nextInt(outcomes.length)];
                        if(totals.containsKey(fixture[0]))totals.put(fixture[0],totals.get(fixture[0])+points[0]);
                        if(totals.containsKey(fixture[1]))totals.put(fixture[1],totals.get(fixture[1])+points[1]);
                    }
                    int own=totals.get(key(team.name));
                    int rankLow=1,rankHigh=1;
                    for(Team rival:teams){
                        if(rival==team)continue;
                        int value=totals.get(key(rival.name));
                        if(value>own)rankLow++;
                        if(value>=own)rankHigh++;
                    }
                    if(rankLow<observedBest)observedBest=rankLow;
                    if(rankHigh>observedWorst)observedWorst=rankHigh;
                }
                line.put("scenarioBest",observedBest);
                line.put("scenarioWorst",observedWorst);
                lines.put(line);
            }
            result.put("teams",lines);
            result.put("fixtures",unique.size());
            return result;
        }catch(org.json.JSONException ex){return null;}
    }

}
