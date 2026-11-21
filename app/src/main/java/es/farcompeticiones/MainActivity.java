package es.farcompeticiones;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;

public class MainActivity extends Activity {
    final int bg=Color.rgb(15,25,38),panel=Color.rgb(27,43,59),accent=Color.rgb(102,225,164);
    LinearLayout root,body;
    String category="M14", page="home";
    WebView browser;
    TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(15,12,15,12);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);home();}
    void layout(String heading){
        browser=null;
        ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,20,16,20);root.setBackgroundColor(bg);sc.addView(root);setContentView(sc);
        TextView title=txt(heading,23,Color.WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        body=new LinearLayout(this);body.setOrientation(1);root.addView(body);
    }
    void button(String title,Runnable click){
        Button b=new Button(this);b.setText(title);body.addView(b);b.setOnClickListener(v->click.run());
    }
    void home(){
        page="home";layout("FAR COMPETICIONES");
        body.addView(txt("Rugby andaluz · Consulta de competiciones",14,accent));
        body.addView(txt("Selecciona una categoría",18,Color.WHITE));
        String[] cats={"M14","M16","M18","Sénior masculino","Sénior femenino","Otras categorías"};
        Spinner sp=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats);sp.setAdapter(adapter);body.addView(sp);
        int index=0;for(int i=0;i<cats.length;i++)if(cats[i].equals(category))index=i;
        sp.setSelection(index);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){category=cats[pos];list();}
        });
        button("Abrir portal público MatchReady",()->openBrowser("https://farugby.matchready.es/"));
        button("Abrir web oficial FAR",()->openExternal("https://farugby.com/"));
        body.addView(txt("Los resultados no se inventan. Las consultas web requieren conexión.",13,Color.LTGRAY));
    }
    void list(){
        while(body.getChildCount()>3)body.removeViewAt(3);
        body.addView(txt("Competiciones · "+category,19,accent));
        String[] entries=category.equals("M14")?new String[]{"Primera División","Segunda División · Grupos","Segunda fase","Promoción","Copa FAR"}:category.equals("M16")?new String[]{"Primera División","Segunda División · Grupos","Otras fases"}:category.equals("M18")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:new String[]{"Consultar competiciones oficiales"};
        for(String entry:entries){TextView card=txt(entry+"  ›",17,Color.WHITE);card.setBackgroundColor(panel);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,5,0,5);body.addView(card,lp);card.setOnClickListener(v->details(entry));}
        body.addView(txt("Estructura orientativa: las divisiones y grupos deben verificarse para cada temporada.",12,Color.LTGRAY));
    }
    String farUrl(){return category.equals("M14")?"https://farugby.com/categoria-m14/":category.equals("M16")?"https://farugby.com/categoria-m16/":category.equals("M18")?"https://farugby.com/categoria-m18/":"https://farugby.com/";}
    void details(String competition){
        page="detail";layout(category+" · "+competition);
        body.addView(txt("FUENTES OFICIALES",17,accent));
        button("Consultar categoría en FAR",()->openBrowser(farUrl()));
        button("Consultar MatchReady",()->openBrowser("https://farugby.matchready.es/"));
        body.addView(txt("¿Tienes el enlace público de esta competición en MatchReady? Puedes guardarlo aquí para acceder directamente.",14,Color.LTGRAY));
        String key="url_"+category+"_"+competition;
        EditText url=new EditText(this);url.setSingleLine(true);url.setText(getPreferences(0).getString(key,""));url.setHint("https://farugby.matchready.es/...");url.setTextColor(Color.WHITE);url.setHintTextColor(Color.LTGRAY);body.addView(url);
        button("Guardar y abrir enlace",()->{
            String link=url.getText().toString().trim();
            if(!link.startsWith("https://farugby.matchready.es/")){Toast.makeText(this,"Introduce un enlace HTTPS público de farugby.matchready.es",Toast.LENGTH_LONG).show();return;}
            getPreferences(0).edit().putString(key,link).apply();openBrowser(link);
        });
        button("Volver a categorías",()->home());
        body.addView(txt("La importación automática y la clasificación integrada siguen en desarrollo.",13,Color.LTGRAY));
    }
    void openExternal(String url){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    void openBrowser(String url){
        page="browser";layout("CONSULTA OFICIAL");
        button("Volver",()->home());
        button("Abrir en navegador externo",()->openExternal(url));
        browser=new WebView(this);browser.getSettings().setJavaScriptEnabled(true);browser.getSettings().setDomStorageEnabled(true);
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String host=r.getUrl().getHost();return host==null||!(host.equals("farugby.com")||host.endsWith(".farugby.com")||host.equals("farugby.matchready.es"));
            }
        });
        browser.setWebChromeClient(new WebChromeClient());
        body.addView(browser,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.72)));
        browser.loadUrl(url);
    }
    @Override public void onBackPressed(){if(browser!=null&&browser.canGoBack())browser.goBack();else home();}
    @Override protected void onDestroy(){if(browser!=null)browser.destroy();super.onDestroy();}
}
