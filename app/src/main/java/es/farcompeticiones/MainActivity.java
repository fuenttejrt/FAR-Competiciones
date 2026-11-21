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
import android.webkit.WebSettings;
import android.widget.ProgressBar;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    final int bg=Color.rgb(15,25,38),panel=Color.rgb(27,43,59),accent=Color.rgb(102,225,164);
    LinearLayout root,body;
    String category="M14", page="home", selectedCompetition="", selectedTab="Calendario", season="2026/27";
    WebView browser, dataBrowser;
    String lastInspectedStructure="";
    String lastBrowserUrl="";
    TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(15,12,15,12);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);home();}
    void layout(String heading){
        if(dataBrowser!=null){dataBrowser.stopLoading();dataBrowser.destroy();dataBrowser=null;}
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
        body.addView(txt("Temporada",17,Color.WHITE));
        String[] seasons={"2026/27","2025/26"};
        Spinner seasonSpinner=new Spinner(this);ArrayAdapter<String> seasonAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,seasons);seasonSpinner.setAdapter(seasonAdapter);body.addView(seasonSpinner);
        seasonSpinner.setSelection(season.equals("2025/26")?1:0);
        seasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){season=seasons[pos];if(body!=null&&body.getChildCount()>5)list();}
        });
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
        String last=getPreferences(0).getString("last_url","");
        if(last.startsWith("https://farugby.matchready.es/")||last.startsWith("https://farugby.com/"))button("Continuar última consulta",()->openBrowser(last));
        button("Abrir web oficial FAR",()->openExternal("https://farugby.com/"));
        body.addView(txt("Los resultados no se inventan. Las consultas web requieren conexión.",13,Color.LTGRAY));
    }
    void list(){
        while(body.getChildCount()>5)body.removeViewAt(5);
        body.addView(txt("Competiciones · "+category+" · "+season,19,accent));
        String[] entries;
        if(category.equals("M14")&&season.equals("2026/27"))entries=new String[]{"Primera División","Segunda División · Grupo 1","Segunda División · Grupo 2","Copa FAR"};
        else entries=category.equals("M14")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:category.equals("M16")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:category.equals("M18")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:new String[]{"Consultar competiciones oficiales"};
        for(String entry:entries){TextView card=txt(entry+"  ›",17,Color.WHITE);card.setBackgroundColor(panel);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,5,0,5);body.addView(card,lp);card.setOnClickListener(v->details(entry));}
        body.addView(txt(category.equals("M14")&&season.equals("2026/27")?"Competiciones contrastadas con la web oficial FAR.":"Estructura orientativa: verificar divisiones y grupos para esta temporada.",12,Color.LTGRAY));
    }
    String officialCalendar(){
        if(!season.equals("2026/27")||!category.equals("M14"))return "";
        if(selectedCompetition.equals("Primera División"))return "https://farugby.matchready.es/es/public/calendar/35/11/combined/";
        if(selectedCompetition.equals("Segunda División · Grupo 1"))return "https://farugby.matchready.es/es/public/calendar/35/12/combined/";
        if(selectedCompetition.equals("Segunda División · Grupo 2"))return "https://farugby.matchready.es/es/public/calendar/35/15/combined/";
        if(selectedCompetition.equals("Copa FAR"))return "https://farugby.matchready.es/es/public/calendar/35/83/combined/";
        return "";
    }
    String farUrl(){return "https://farugby.com/";}
    void details(String competition){
        selectedCompetition=competition;
        showCompetitionTab("Calendario");
    }
    void showCompetitionTab(String tab){
        selectedTab=tab;page="detail";layout(category+" · "+selectedCompetition+" · "+season);
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);body.addView(tabs);
        for(String item:new String[]{"Calendario","Resultados","Clasificación"}){
            Button b=new Button(this);b.setText(item);b.setTextSize(11);b.setAllCaps(false);
            if(item.equals(tab))b.setTextColor(Color.rgb(0,110,65));
            tabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->showCompetitionTab(item));
        }
        body.addView(txt(tab.toUpperCase(),18,accent));
        String official=officialCalendar();
        if(!official.isEmpty()){
            body.addView(txt("Fuente: calendario público oficial FAR / MatchReady · "+season,13,accent));
            button("Ver "+tab.toLowerCase()+" oficial",()->openBrowser(official));
            button("Actualizar datos de MatchReady",()->refreshNative(official));
            showCachedNative();
        }else body.addView(txt("Sin datos sincronizados. No se muestran resultados ni clasificaciones sin verificar.",14,Color.LTGRAY));
        String saved=getPreferences(0).getString("url_"+season+"_"+category+"_"+selectedCompetition,"");
        if(official.isEmpty()&&saved.startsWith("https://farugby.matchready.es/"))button("Consultar "+tab.toLowerCase()+" en MatchReady",()->openBrowser(saved));
        else if(official.isEmpty())button("Buscar "+tab.toLowerCase()+" en MatchReady",()->openBrowser("https://farugby.matchready.es/"));
        body.addView(txt("FUENTES OFICIALES",17,accent));
        button("Consultar categoría en FAR",()->openBrowser(farUrl()));
        button("Consultar MatchReady",()->openBrowser("https://farugby.matchready.es/"));
        if(category.equals("M14")&&season.equals("2025/26")){
            body.addView(txt("EJEMPLO VERIFICADO · TEMPORADA 2025/26",14,accent));
            button("Ver competición M14 histórica en MatchReady",()->openBrowser("https://farugby.matchready.es/es/public/competition/4282/"));
            body.addView(txt("Este enlace es histórico (2025/26), no corresponde a 2026/27.",12,Color.LTGRAY));
        }
        body.addView(txt("¿Tienes el enlace público de esta competición en MatchReady? Puedes guardarlo aquí para acceder directamente.",14,Color.LTGRAY));
        String key="url_"+season+"_"+category+"_"+selectedCompetition;
        EditText url=new EditText(this);url.setSingleLine(true);url.setText(getPreferences(0).getString(key,""));url.setHint("https://farugby.matchready.es/...");url.setTextColor(Color.WHITE);url.setHintTextColor(Color.LTGRAY);body.addView(url);
        button("Guardar y abrir enlace",()->{
            String link=url.getText().toString().trim();
            if(!link.startsWith("https://farugby.matchready.es/")){Toast.makeText(this,"Introduce un enlace HTTPS público de farugby.matchready.es",Toast.LENGTH_LONG).show();return;}
            getPreferences(0).edit().putString(key,link).apply();openBrowser(link);
        });
        button("Volver a categorías",()->home());
        body.addView(txt("Enlaces guardados por temporada, categoría y competición. La importación automática sigue en desarrollo.",13,Color.LTGRAY));
    }
    String cacheKey(){return "native_"+season+"_"+category+"_"+selectedCompetition;}
    void showCachedNative(){
        String raw=getPreferences(0).getString(cacheKey(),"");
        if(raw.isEmpty()){body.addView(txt("Pulsa Actualizar datos de MatchReady para cargar las tablas oficiales.",13,Color.LTGRAY));return;}
        try{renderNative(new org.json.JSONObject(raw));}catch(Exception ex){body.addView(txt("Los datos guardados no se pueden leer. Actualiza de nuevo.",13,Color.LTGRAY));}
    }
    void renderNative(org.json.JSONObject data)throws org.json.JSONException{
        org.json.JSONArray tables=data.optJSONArray("tables");
        if(tables==null)return;
        int count=0;
        for(int i=0;i<tables.length();i++){
            org.json.JSONObject table=tables.optJSONObject(i);if(table==null)continue;
            org.json.JSONArray rows=table.optJSONArray("rows");if(rows==null||rows.length()==0)continue;
            boolean standing=table.optBoolean("standing");
            if(selectedTab.equals("Clasificación")!=standing)continue;
            String title=table.optString("heading",standing?"Clasificación":"Partidos");
            TextView head=txt(title.isEmpty()?(standing?"Clasificación":"Jornada"):title,16,accent);
            head.setTypeface(null,Typeface.BOLD);body.addView(head);
            int max=standing?rows.length():rows.length();
            for(int j=0;j<max;j++){
                org.json.JSONArray cells=rows.optJSONArray(j);if(cells==null||cells.length()==0)continue;
                StringBuilder line=new StringBuilder();
                for(int k=0;k<cells.length();k++){
                    String value=cells.optString(k,"").trim();
                    if(value.isEmpty())continue;
                    if(line.length()>0)line.append(standing?"  ·  ":"  |  ");
                    line.append(value);
                }
                if(line.length()==0)continue;
                String rowText=line.toString();
                boolean score=rowText.matches("(?s).*\\d+\\s*[-–]\\s*\\d+.*");
                if(selectedTab.equals("Resultados")&&!score)continue;
                TextView item=txt(rowText,standing?12:14,Color.WHITE);
                item.setBackgroundColor(panel);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,2,0,2);body.addView(item,lp);
                count++;
            }
        }
        if(count==0)body.addView(txt("No se encontraron filas para esta pestaña. Consulta la fuente oficial.",13,Color.LTGRAY));
        else body.addView(txt("Datos de MatchReady guardados en este dispositivo. Actualiza para comprobar cambios.",12,Color.LTGRAY));
    }
    void refreshNative(String url){
        if(dataBrowser!=null){dataBrowser.destroy();dataBrowser=null;}
        final String key=cacheKey();
        Toast.makeText(this,"Leyendo calendario oficial…",Toast.LENGTH_SHORT).show();
        dataBrowser=new WebView(this);
        body.addView(dataBrowser,new LinearLayout.LayoutParams(1,1));
        dataBrowser.getSettings().setJavaScriptEnabled(true);
        dataBrowser.getSettings().setDomStorageEnabled(true);
        dataBrowser.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String loaded){
                if(!loaded.startsWith("https://farugby.matchready.es/"))return;
                view.postDelayed(()->{
                    if(dataBrowser!=view||!page.equals("detail")||!key.equals(cacheKey()))return;
                    String js="(function(){try{var tables=Array.from(document.querySelectorAll('table')).map(function(t){var headers=Array.from(t.querySelectorAll('th')).map(function(c){return (c.innerText||'').trim().toLowerCase()});var standing=headers.some(function(x){return x==='puntos'||x==='ptos'||x==='pts'||x==='punt os'})||(headers.indexOf('j')>=0&&headers.indexOf('g')>=0);var rows=Array.from(t.querySelectorAll('tr')).map(function(r){return Array.from(r.querySelectorAll('th,td')).map(function(c){return (c.innerText||'').trim().replace(/\\s+/g,' ')})}).filter(function(r){return r.length>1});return {heading:'',standing:standing,rows:rows}});return JSON.stringify({url:location.href,title:document.title,tables:tables})}catch(e){return JSON.stringify({error:String(e)})}})()";
                    view.evaluateJavascript(js,result->{
                        try{
                            String decoded=new org.json.JSONTokener(result).nextValue().toString();
                            org.json.JSONObject obj=new org.json.JSONObject(decoded);
                            org.json.JSONArray tables=obj.optJSONArray("tables");
                            if(obj.has("error")){Toast.makeText(MainActivity.this,"Error de lectura: "+obj.optString("error"),Toast.LENGTH_LONG).show();return;}
                            if(tables==null||tables.length()==0){Toast.makeText(MainActivity.this,"No se encontraron tablas. Abre la fuente oficial.",Toast.LENGTH_LONG).show();return;}
                            getPreferences(0).edit().putString(key,obj.toString()).apply();
                            Toast.makeText(MainActivity.this,"Tablas descargadas: "+tables.length(),Toast.LENGTH_LONG).show();
                            showCompetitionTab(selectedTab);
                        }catch(Exception ex){android.util.Log.e("FAR_IMPORT","Respuesta: "+result,ex);new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Error del lector").setMessage("Detalle: "+ex.getMessage()+"\nRespuesta: "+String.valueOf(result).substring(0,Math.min(250,String.valueOf(result).length()))).setPositiveButton("Cerrar",null).show();}
                    });
                },2500);
            }
        });
        dataBrowser.loadUrl(url);
    }
    void openExternal(String url){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    void openBrowser(String url){
        page="browser";layout("CONSULTA OFICIAL");
        button("Volver",()->home());
        button("Abrir en navegador externo",()->openExternal(browser!=null?browser.getUrl():url));
        button("Actualizar página",()->{if(browser!=null)browser.reload();});
        button("Inspeccionar estructura pública",()->inspectPage());
        button("Ver informe de estructura",()->new android.app.AlertDialog.Builder(this).setTitle("Diagnóstico de MatchReady").setMessage(lastInspectedStructure.isEmpty()?"Abre un calendario y pulsa Inspeccionar estructura pública.":lastInspectedStructure).setPositiveButton("Cerrar",null).show());
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);body.addView(progress);
        browser=new WebView(this);browser.getSettings().setJavaScriptEnabled(true);browser.getSettings().setDomStorageEnabled(true);
        browser.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String target,android.graphics.Bitmap icon){progress.setVisibility(View.VISIBLE);lastBrowserUrl=target;}
            @Override public void onPageFinished(WebView view,String target){progress.setVisibility(View.GONE);lastBrowserUrl=target;getPreferences(0).edit().putString("last_url",target).apply();}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String host=r.getUrl().getHost();return host==null||!(host.equals("farugby.com")||host.endsWith(".farugby.com")||host.equals("farugby.matchready.es"));
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView view,int newProgress){progress.setProgress(newProgress);}});
        body.addView(browser,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.72)));
        browser.loadUrl(url);
    }
    void inspectPage(){
        if(browser==null){Toast.makeText(this,"Abre primero un calendario",Toast.LENGTH_SHORT).show();return;}
        String script="(function(){try{var tables=[].slice.call(document.querySelectorAll('table'));var headings=[].slice.call(document.querySelectorAll('h1,h2,h3,h4')).map(function(x){return x.innerText.trim()}).filter(Boolean).slice(0,20);var rows=tables.map(function(t){return {filas:t.querySelectorAll('tr').length,cabeceras:[].slice.call(t.querySelectorAll('th')).map(function(x){return x.innerText.trim()}).slice(0,25)}});return JSON.stringify({titulo:document.title,url:location.href,tablas:rows,encabezados:headings,iframes:[].slice.call(document.querySelectorAll('iframe')).map(function(x){return x.src}).slice(0,12)});}catch(e){return JSON.stringify({error:String(e)})}})()";
        browser.evaluateJavascript(script,result->{
            try{
                String decoded=new org.json.JSONTokener(result).nextValue().toString();
                org.json.JSONObject info=new org.json.JSONObject(decoded);
                StringBuilder out=new StringBuilder();
                out.append("Página: ").append(info.optString("titulo")).append("\\n");
                out.append("URL: ").append(info.optString("url")).append("\\n");
                out.append("Tablas: ").append(info.optJSONArray("tablas")!=null?info.optJSONArray("tablas").length():0).append("\\n");
                out.append("Encabezados: ").append(info.optJSONArray("encabezados")).append("\\n");
                out.append("Columnas y filas: ").append(info.optJSONArray("tablas")).append("\\n");
                out.append("Marcos: ").append(info.optJSONArray("iframes"));
                lastInspectedStructure=out.toString();
                new android.app.AlertDialog.Builder(this).setTitle("Estructura detectada").setMessage(lastInspectedStructure).setPositiveButton("Cerrar",null).show();
            }catch(Exception ex){lastInspectedStructure="No se ha podido analizar la página: "+ex.getMessage();Toast.makeText(this,lastInspectedStructure,Toast.LENGTH_LONG).show();}
        });
    }
    @Override public void onBackPressed(){if(browser!=null&&browser.canGoBack())browser.goBack();else if(page.equals("browser")&&!selectedCompetition.isEmpty())showCompetitionTab(selectedTab);else home();}
    @Override protected void onDestroy(){if(browser!=null)browser.destroy();if(dataBrowser!=null)dataBrowser.destroy();super.onDestroy();}
}
