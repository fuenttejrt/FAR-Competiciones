package es.farcompeticiones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Guía informativa de la competición M14 2026/27, basada en circulares FAR 10 y 2. */
public final class M14RulesActivity {
    private static final int BG=0xff0f1926, PANEL=0xff1b2b3b, GREEN=0xff66e1a4;
    private M14RulesActivity(){}

    private static TextView text(Activity activity, String value, int size, boolean heading){
        TextView t=new TextView(activity);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(heading?GREEN:Color.WHITE);
        if(heading)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(16,heading?20:8,16,heading?8:12);
        return t;
    }
    private static void section(Activity a,LinearLayout parent,String heading,String detail){
        parent.addView(text(a,heading,17,true));
        TextView body=text(a,detail,14,false);
        body.setBackgroundColor(PANEL);
        parent.addView(body);
    }
    public static void open(Activity activity){
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(12,12,12,20);
        scroll.addView(content);

        content.addView(text(activity,"M14 · Sistema de competición",21,true));
        content.addView(text(activity,"Temporada 2026/27 · Campeonato Andaluz M14 XV\nGuía de consulta. Ante cualquier discrepancia prevalecen las circulares y resoluciones oficiales de la FAR.",13,false));

        section(activity,content,"1 · Grupos de la primera fase",
            "Primera División (rendimiento): 6 equipos.\nSegunda División (desarrollo): Grupo 1 y Grupo 2, con 6 equipos cada uno.\nLa primera fase se disputa en cinco concentraciones.");
        section(activity,content,"2 · Promoción entre fases",
            "Participan el 6.º de Primera División y los primeros clasificados de cada grupo de Segunda División.\nSe disputa un triangular; el vencedor ocupa una plaza en Primera División en la segunda fase.\nLos equipos de Segunda pueden renunciar a participar sin penalización; si renuncia uno, se juega un partido entre los otros dos.");
        section(activity,content,"3 · Segunda fase",
            "Primera División: los cinco primeros de la primera fase y el vencedor de la promoción.\nSegunda División A: los otros dos participantes de la promoción, más los segundos y terceros de los dos grupos geográficos.\nSegunda División B: los restantes equipos inscritos.\nCada grupo disputa cinco concentraciones.");
        section(activity,content,"4 · Promoción al terminar la segunda fase",
            "El 6.º de Primera División disputa una promoción contra el 1.º de Segunda División A.\nNo puede disputar esta promoción un equipo cuyo club ya tenga otro equipo en Primera División.");
        section(activity,content,"5 · Puntuación general",
            "Victoria: 4 puntos. Empate: 2. Derrota: 0.\nBonus ofensivo: +1 por ganar con tres o más ensayos de diferencia.\nBonus defensivo: +1 por perder por siete puntos o menos.\nLas sanciones y resultados administrativos pueden modificar la clasificación.");
        section(activity,content,"6 · Desempates",
            "En competiciones a un número par de vueltas: enfrentamiento directo (diferencia de tantos), más victorias, diferencia general de tantos, coeficiente de tantos y criterios disciplinarios, en el orden de la Circular 2.\nEn competiciones a un número impar de vueltas y entre grupos distintos existen reglas específicas. Consulta el texto oficial antes de aplicar un desempate.");
        section(activity,content,"7 · Regla particular de Primera División",
            "Si se mantienen los mismos seis equipos entre ambas fases, la clasificación final acumula los puntos de las jornadas. Si entra un equipo procedente de Segunda, se aplica la regla especial de cómputo duplicado de sus puntos descrita en la Circular 10.\nEsta pantalla explica la regla, pero no recalcula ni altera las clasificaciones oficiales de MatchReady.");
        section(activity,content,"8 · Normas de partido",
            "Las concentraciones suelen comprender dos partidos por equipo de 36 minutos. La Circular 10 establece un límite de cómputo del marcador al alcanzar una diferencia de 30 puntos, con la excepción prevista para una conversión.\nLas reglas de alineaciones, sustituciones y disciplina se consultan en la circular específica.");
        section(activity,content,"Fuentes",
            "Circular FAR n.º 10: Campeonato Andaluz M14 XV, temporada 2026/27.\nCircular FAR n.º 2: Normas generales de las competiciones, temporada 2026/27.\nLas normas específicas de la Circular 10 prevalecen sobre las generales cuando corresponda.");
        Button source=new Button(activity);
        source.setText("Consultar circulares oficiales FAR");
        source.setAllCaps(false);
        content.addView(source);
        source.setOnClickListener(v->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://farugby.matchready.es/es/public/documentation/1/index/"))));
        new AlertDialog.Builder(activity).setView(scroll).setPositiveButton("Cerrar",(d,w)->d.dismiss()).show();
    }
}
