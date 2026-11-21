package es.farcompeticiones;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
    LinearLayout root, content;
    int bg=Color.rgb(15,25,38), panel=Color.rgb(27,43,59), accent=Color.rgb(102,225,164);
    @Override public void onCreate(Bundle b){super.onCreate(b); build();}
    TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setPadding(16,14,16,14);return t;}
    void build(){
        ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(18,24,18,24);root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
        TextView title=text("FAR COMPETICIONES",24,Color.WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(text("Rugby andaluz · Temporada 2026/27",13,accent));
        root.addView(text("Selecciona una categoría",17,Color.WHITE));
        Spinner categories=new Spinner(this);String[] names={"M14","M16","M18","Sénior masculino","Sénior femenino","Otras categorías"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);categories.setAdapter(adapter);root.addView(categories);
        content=new LinearLayout(this);content.setOrientation(1);root.addView(content);
        categories.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){render(names[pos]);}});
        render("M14");
    }
    void render(String cat){content.removeAllViews();content.addView(text("Competiciones · "+cat,20,Color.WHITE));
        String[] items=cat.equals("M14")?new String[]{"1.ª División","2.ª División · Primera fase · Grupo 1","2.ª División · Primera fase · Grupo 2","2.ª División · Segunda fase · Grupo A","2.ª División · Segunda fase · Grupo B","Promoción de ascenso/descenso","Copa FAR"}:cat.equals("M18")?new String[]{"1.ª División","2.ª División · Grupo 1","2.ª División · Grupo 2","Copa FAR"}:new String[]{"Competiciones pendientes de catalogar"};
        for(String item:items){TextView card=text(item+"   ›",17,Color.WHITE);card.setBackgroundColor(panel);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,6,0,6);content.addView(card,lp);card.setOnClickListener(v->details(cat,item));}
        content.addView(text("Catálogo inicial basado en la estructura publicada por FAR. Aún no se han importado partidos ni clasificaciones.",13,Color.LTGRAY));
    }
    void details(String cat,String item){content.removeAllViews();content.addView(text(cat+" · "+item,21,Color.WHITE));
        content.addView(text("CALENDARIO   |   RESULTADOS   |   CLASIFICACIÓN   |   FASES",14,accent));
        content.addView(text("Datos oficiales pendientes de conexión. No se muestran marcadores ni equipos ficticios.",15,Color.LTGRAY));
        Button official=new Button(this);official.setText("Consultar página oficial FAR");content.addView(official);official.setOnClickListener(v->{String slug=cat.equals("M14")?"categoria-m14":cat.equals("M18")?"categoria-m18":"";startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://farugby.com/"+slug+"/")));});
        Button back=new Button(this);back.setText("Volver a competiciones");content.addView(back);back.setOnClickListener(v->render(cat));
    }
}