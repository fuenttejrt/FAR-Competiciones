package es.farcompeticiones;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

/** Pantalla independiente de consulta: Circulares FAR 10 y 2, temporada 2026/27. */
public final class M14RulesActivity extends Activity {
    private static final int BG=0xff0f1926, PANEL=0xff1b2b3b, GREEN=0xff66e1a4, MUTED=0xffb5c5d1;
    private LinearLayout content;
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
    public static void open(Activity activity){activity.startActivity(new Intent(activity,M14RulesActivity.class));}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(dp(12),dp(10),dp(12),dp(10));return t;
    }
    private GradientDrawable panel(){
        GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(10));return d;
    }
    private void accordion(String title,String details,boolean expanded){
        LinearLayout wrapper=new LinearLayout(this);wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackground(panel());
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.setMargins(0,0,0,dp(9));
        content.addView(wrapper,margin);
        TextView header=text((expanded?"▾  ":"▸  ")+title,15,GREEN,true);
        wrapper.addView(header);
        TextView description=text(details,14,Color.WHITE,false);
        description.setPadding(dp(16),dp(2),dp(16),dp(14));
        description.setVisibility(expanded?View.VISIBLE:View.GONE);
        wrapper.addView(description);
        header.setOnClickListener(v->{
            boolean show=description.getVisibility()!=View.VISIBLE;
            description.setVisibility(show?View.VISIBLE:View.GONE);
            header.setText((show?"▾  ":"▸  ")+title);
        });
    }
    private void phase(String name,String subtitle){
        TextView t=text(name+"\n"+subtitle,13,Color.WHITE,false);
        t.setBackground(panel());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(5));
        content.addView(t,p);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(BG);
        setContentView(screen);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView back=text("‹",30,Color.WHITE,true);top.addView(back);back.setOnClickListener(v->finish());
        LinearLayout headings=new LinearLayout(this);headings.setOrientation(LinearLayout.VERTICAL);
        TextView title=text("Sistema de competición",19,GREEN,true);title.setPadding(0,dp(8),0,0);headings.addView(title);
        TextView subtitle=text("M14 · Temporada 2026/27",12,MUTED,false);subtitle.setPadding(0,0,0,dp(8));headings.addView(subtitle);
        top.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        screen.addView(top);
        View line=new View(this);line.setBackgroundColor(PANEL);screen.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(8),dp(12),dp(20));scroll.addView(content);

        TextView intro=text("Campeonato Andaluz M14 XV · Circular FAR n.º 10",13,MUTED,false);
        intro.setPadding(dp(3),dp(4),dp(3),dp(12));content.addView(intro);
        content.addView(text("ESQUEMA DE FASES",14,GREEN,true));
        phase("1.ª FASE","Primera División · 6 equipos   |   Segunda División · 2 grupos de 6");
        content.addView(text("↓  Promoción: 6.º de Primera + 1.º de cada grupo de Segunda",12,GREEN,true));
        phase("2.ª FASE","Primera División · 6 equipos   |   Segunda División A y B");
        content.addView(text("↓  Promoción final: 6.º de Primera frente al 1.º de Segunda A",12,GREEN,true));
        content.addView(text("TOCA UN APARTADO PARA VER LAS NORMAS",12,MUTED,true));

        accordion("1 · Grupos de la primera fase",
            "Primera División (rendimiento): 6 equipos.\nSegunda División (geográfica): Grupo 1 y Grupo 2, con 6 equipos cada uno.\nLa primera fase se disputa en cinco concentraciones.",false);
        accordion("2 · Promoción entre fases",
            "Participan el 6.º de Primera División y los primeros clasificados de cada grupo de Segunda División.\nSe disputa un triangular; el vencedor ocupa una plaza en Primera División en la segunda fase.\nLos equipos de Segunda pueden renunciar a participar sin penalización; si renuncia uno, se juega un partido entre los otros dos.",false);
        accordion("3 · Segunda fase",
            "Primera División: los cinco primeros de la primera fase y el vencedor de la promoción.\nSegunda División A: los otros dos participantes de la promoción, más los segundos y terceros de los dos grupos geográficos.\nSegunda División B: los restantes equipos inscritos.\nCada grupo disputa cinco concentraciones.",false);
        accordion("4 · Promoción al terminar la segunda fase",
            "El 6.º de Primera División disputa una promoción contra el 1.º de Segunda División A.\nNo puede disputar esta promoción un equipo cuyo club ya tenga otro equipo en Primera División.",false);
        accordion("5 · Puntuación general",
            "Victoria: 4 puntos. Empate: 2. Derrota: 0.\nBonus ofensivo: +1 por ganar con tres o más ensayos de diferencia.\nBonus defensivo: +1 por perder por siete puntos o menos.\nLas sanciones y resultados administrativos pueden modificar la clasificación.",false);
        accordion("6 · Desempates",
            "En competiciones a un número par de vueltas: enfrentamiento directo (diferencia de tantos), más victorias, diferencia general de tantos, coeficiente de tantos y criterios disciplinarios, en el orden de la Circular 2.\nEn competiciones a un número impar de vueltas y entre grupos distintos existen reglas específicas. Consulta el texto oficial antes de aplicar un desempate.",false);
        accordion("7 · Regla particular de Primera División",
            "Si se mantienen los mismos seis equipos entre ambas fases, la clasificación final acumula los puntos de las jornadas. Si entra un equipo procedente de Segunda, se aplica la regla especial de cómputo duplicado de sus puntos descrita en la Circular 10.\nEsta pantalla explica la regla, pero no recalcula ni altera las clasificaciones oficiales de MatchReady.",false);
        accordion("8 · Normas de partido",
            "Las concentraciones suelen comprender dos partidos por equipo de 36 minutos. La Circular 10 establece un límite de cómputo del marcador al alcanzar una diferencia de 30 puntos, con la excepción prevista para una conversión.\nLas reglas de alineaciones, sustituciones y disciplina se consultan en la circular específica.",false);
        accordion("Fuentes y alcance",
            "Circular FAR n.º 10: Campeonato Andaluz M14 XV, temporada 2026/27.\nCircular FAR n.º 2: Normas generales de las competiciones, temporada 2026/27.\nLas normas específicas prevalecen cuando corresponda. Esta es una guía informativa; prevalecen las circulares y resoluciones oficiales.",false);
        Button source=new Button(this);source.setText("Consultar documentos oficiales FAR");source.setAllCaps(false);
        content.addView(source);
        source.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://farugby.matchready.es/es/public/documentation/1/index/"))));
    }
}
