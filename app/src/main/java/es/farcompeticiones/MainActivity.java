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
    int selectedMatchGroup=-1;
    boolean showStandingDetails=false;
    boolean calendarUpcomingOnly=true;
    TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(15,12,15,12);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);home();}
    void layout(String heading){
        if(dataBrowser!=null){dataBrowser.stopLoading();dataBrowser.destroy();dataBrowser=null;}
        browser=null;
        ScrollView sc=new ScrollView(this);
        sc.setFitsSystemWindows(true);
        getWindow().getDecorView().setSystemUiVisibility(0);
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,8,12,16);root.setBackgroundColor(bg);sc.addView(root);setContentView(sc);
        TextView title=txt(heading,19,Color.WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
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
        selectedMatchGroup=-1;
        calendarUpcomingOnly=true;
        showCompetitionTab("Calendario");
    }
    void showCompetitionTab(String tab){
        selectedTab=tab;page="detail";
        layout(category+" · "+selectedCompetition);
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);body.addView(tabs);
        for(String item:new String[]{"Calendario","Resultados","Clasificación"}){
            Button b=new Button(this);b.setText(item);b.setTextSize(11);b.setAllCaps(false);
            b.setTextColor(item.equals(tab)?accent:Color.WHITE);
            tabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{if(!item.equals(selectedTab)){selectedMatchGroup=item.equals("Resultados")?0:-1;}showCompetitionTab(item);});
        }
        String official=officialCalendar();
        if(!official.isEmpty()){
            if(selectedCompetition.startsWith("Segunda División"))body.addView(txt("M14 · "+selectedCompetition+" · "+season,12,accent));
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);body.addView(actions);
            Button refresh=new Button(this);refresh.setText("↻ Actualizar");refresh.setTextSize(12);refresh.setAllCaps(false);
            actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));refresh.setOnClickListener(v->refreshNative(official));
            Button source=new Button(this);source.setText("Web oficial");source.setTextSize(12);source.setAllCaps(false);
            actions.addView(source,new LinearLayout.LayoutParams(0,-2,1));source.setOnClickListener(v->openBrowser(official));
            showCachedNative();
        }else{
            body.addView(txt("Todavía no hay un calendario oficial configurado para esta competición.",14,Color.LTGRAY));
            String key="url_"+season+"_"+category+"_"+selectedCompetition;
            EditText url=new EditText(this);url.setSingleLine(true);url.setHint("Enlace público MatchReady");url.setText(getPreferences(0).getString(key,""));body.addView(url);
            button("Guardar y abrir",()->{String link=url.getText().toString().trim();if(link.startsWith("https://farugby.matchready.es/")){getPreferences(0).edit().putString(key,link).apply();openBrowser(link);}else Toast.makeText(this,"Enlace MatchReady no válido",Toast.LENGTH_LONG).show();});
        }
        button("‹ Competiciones",()->home());
    }
    String cacheKey(){return "native_"+season+"_"+category+"_"+selectedCompetition;}
    void showCachedNative(){
        String raw=getPreferences(0).getString(cacheKey(),"");
        if(raw.isEmpty()){body.addView(txt("Esta competición aún no tiene datos descargados. Pulsa «Actualizar» para importar su calendario, resultados y clasificación desde MatchReady.",13,Color.LTGRAY));return;}
        try{org.json.JSONObject cached=new org.json.JSONObject(raw);if(cached.optInt("format",0)!=2){body.addView(txt("Actualiza para convertir los datos al nuevo formato.",13,Color.LTGRAY));return;}renderNative(cached);}catch(Exception ex){body.addView(txt("Los datos guardados no se pueden leer. Actualiza de nuevo.",13,Color.LTGRAY));}
    }
    String clean(String text,String marker){
        int index=text.toLowerCase(java.util.Locale.ROOT).indexOf(marker.toLowerCase(java.util.Locale.ROOT));
        return index<0?text.trim():text.substring(0,index).trim();
    }
    org.json.JSONArray normalizedCells(org.json.JSONArray original){
        org.json.JSONArray cells=new org.json.JSONArray();
        for(int i=0;i<original.length();i++){
            String v=original.optString(i,"").trim();
            if(v.isEmpty()&&(i==0||i==original.length()-1))continue;
            cells.put(v);
        }
        return cells;
    }
    void addStandingTeam(LinearLayout parent,String team,String crest,int width){
        LinearLayout fixed=new LinearLayout(this);
        fixed.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int px=(int)(getResources().getDisplayMetrics().density*30);
        if(crest.startsWith("https://")){
            ImageView logo=new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px,px);
            lp.setMargins(2,0,(int)(8*getResources().getDisplayMetrics().density),0);
            fixed.addView(logo,lp);
            new Thread(()->{
                try{
                    java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new java.net.URL(crest).openConnection();
                    connection.setConnectTimeout(4500);connection.setReadTimeout(4500);
                    try(java.io.InputStream input=connection.getInputStream()){
                        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(input);
                        if(bitmap!=null)runOnUiThread(()->logo.setImageBitmap(bitmap));
                    }finally{connection.disconnect();}
                }catch(Exception ignored){}
            }).start();
        }
        TextView name=txt(team,11,Color.WHITE);
        name.setPadding(2,4,3,4);
        name.setGravity(android.view.Gravity.CENTER_VERTICAL);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        fixed.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(fixed,new LinearLayout.LayoutParams(width,-2));
    }
    void addStandingPoints(LinearLayout parent,String value,int width,int color,int background){
        TextView cell=txt(value,11,color);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setPadding(5,8,2,8);
        cell.setBackgroundColor(background);
        cell.setElevation(4*getResources().getDisplayMetrics().density);
        parent.addView(cell,new LinearLayout.LayoutParams(width,-2));
    }
    void addStandingFixed(LinearLayout parent,String value,int width,int color){
        TextView cell=txt(value,11,color);
        cell.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.CENTER_HORIZONTAL);
        cell.setPadding(2,8,2,8);
        parent.addView(cell,new LinearLayout.LayoutParams(width,-2));
    }
    String standingStat(org.json.JSONObject stats,String... names){
        if(stats==null)return "–";
        java.util.Iterator<String> keys=stats.keys();
        while(keys.hasNext()){
            String key=keys.next();
            String normalized=java.text.Normalizer.normalize(key.toLowerCase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]","").trim();
            for(String name:names)if(normalized.equals(name))return stats.optString(key,"–");
        }
        return "–";
    }
    boolean isScore(String value){return value.matches("(?s).*\\d+\\s*[-–:]\\s*\\d+.*");}
    void addMatchCard(org.json.JSONArray raw){
        org.json.JSONArray cells=normalizedCells(raw);
        if(cells.length()<4)return;
        String when=cells.optString(0,""),home=cells.optString(1,""),score=cells.optString(2,""),away=cells.optString(3,"");
        if(home.equalsIgnoreCase("Equipo local")||when.equalsIgnoreCase("Fecha/Hora")||home.isEmpty())return;
        if(!isScore(score)&&isScore(away)&&cells.length()>4){
            score=away;away=cells.optString(4,"");
        }
        String referee=home.contains("Árbitro:")?home.substring(home.indexOf("Árbitro:")):"";
        String field=away.contains("Campo:")?away.substring(away.indexOf("Campo:")):"";
        home=clean(home,"Árbitro:");away=clean(away,"Campo:");
        if(away.isEmpty())return;
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(10,7,10,7);card.setBackgroundColor(panel);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,3,0,3);body.addView(card,cp);
        card.addView(txt(when,12,accent));
        LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(line);
        TextView left=txt(home,13,Color.WHITE);TextView mid=txt(isScore(score)?score:"–",19,accent);
        mid.setTypeface(null,Typeface.BOLD);mid.setGravity(android.view.Gravity.CENTER);
        TextView right=txt(away,13,Color.WHITE);right.setGravity(android.view.Gravity.RIGHT);
        line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(mid,new LinearLayout.LayoutParams(-2,-2));
        line.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        String extra=(referee+" "+field).trim();
        if(!extra.isEmpty()){TextView details=txt(extra,11,Color.LTGRAY);details.setVisibility(View.GONE);card.addView(details);card.setOnClickListener(v->details.setVisibility(details.getVisibility()==View.GONE?View.VISIBLE:View.GONE));}
    }
    long matchTimestamp(org.json.JSONObject row,boolean endOfDay){
        String when=row.optString("when","");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})(?:\\s+(\\d{1,2}):(\\d{2}))?").matcher(when);
        if(!m.find())return -1;
        try{
            java.util.Calendar c=java.util.Calendar.getInstance();
            c.setLenient(false);
            boolean hasTime=m.group(4)!=null;
            int hour=hasTime?Integer.parseInt(m.group(4)):(endOfDay?23:0);
            int minute=hasTime?Integer.parseInt(m.group(5)):(endOfDay?59:0);
            c.set(Integer.parseInt(m.group(3)),Integer.parseInt(m.group(2))-1,Integer.parseInt(m.group(1)),hour,minute,endOfDay&&!hasTime?59:0);
            c.set(java.util.Calendar.MILLISECOND,0);
            return c.getTimeInMillis();
        }catch(Exception ex){return -1;}
    }
    boolean isPlayedMatch(org.json.JSONObject row){
        if(!isScore(row.optString("score","")))return false;
        long date=matchTimestamp(row,false);
        return date<0||date<=System.currentTimeMillis();
    }
    boolean isUpcomingMatch(org.json.JSONObject row){
        if(isPlayedMatch(row))return false;
        long date=matchTimestamp(row,true);
        return date<0||date>=System.currentTimeMillis();
    }
    boolean matchVisible(org.json.JSONObject row){
        if(selectedTab.equals("Resultados"))return isPlayedMatch(row);
        if(selectedTab.equals("Calendario")&&calendarUpcomingOnly)return isUpcomingMatch(row);
        return true;
    }
    void renderNative(org.json.JSONObject data)throws org.json.JSONException{
        org.json.JSONArray tables=data.optJSONArray("tables");if(tables==null)return;
        int count=0;
        if(selectedTab.equals("Calendario")){
            Button mode=new Button(this);mode.setAllCaps(false);
            mode.setText(calendarUpcomingOnly?"Ver calendario completo":"Ver próximos partidos");mode.setTextSize(12);
            body.addView(mode);
            mode.setOnClickListener(v->{calendarUpcomingOnly=!calendarUpcomingOnly;selectedMatchGroup=-1;showCompetitionTab("Calendario");});
        }
        if(!selectedTab.equals("Clasificación")){
            java.util.ArrayList<Integer> groupIds=new java.util.ArrayList<>();
            java.util.ArrayList<String> groupNames=new java.util.ArrayList<>();
            for(int t=0;t<tables.length();t++){
                org.json.JSONObject group=tables.optJSONObject(t);
                if(group!=null&&group.optString("kind").equals("matches")&&group.optJSONArray("rows")!=null&&group.optJSONArray("rows").length()>0){
                    boolean any=false;
                    for(int n=0;n<group.optJSONArray("rows").length();n++){org.json.JSONObject row=group.optJSONArray("rows").optJSONObject(n);if(row!=null&&matchVisible(row)){any=true;break;}}
                    if(!any)continue;
                    if(selectedTab.equals("Resultados")){groupIds.add(0,t);groupNames.add(0,group.optString("heading","Jornada "+(t+1)));}else{groupIds.add(t);groupNames.add(group.optString("heading","Jornada "+(t+1)));}
                }
            }
            if(!groupIds.isEmpty()){
                if(selectedMatchGroup>=groupIds.size())selectedMatchGroup=-1;
                Spinner picker=new Spinner(this);
                java.util.ArrayList<String> options=new java.util.ArrayList<>();
                options.add(selectedTab.equals("Calendario")&&calendarUpcomingOnly?"Próximas jornadas":selectedTab.equals("Resultados")?"Todos los resultados":"Todas las jornadas");
                options.addAll(groupNames);
                ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
                picker.setAdapter(adapter);picker.setSelection(selectedMatchGroup+1);body.addView(picker);
                picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                    public void onNothingSelected(AdapterView<?> p){}
                    public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                        int next=position-1;
                        if(next!=selectedMatchGroup){selectedMatchGroup=next;showCompetitionTab(selectedTab);}
                    }
                });

            }
        }
        for(int iteration=0;iteration<tables.length();iteration++){
            int i=selectedTab.equals("Resultados")?tables.length()-1-iteration:iteration;
            org.json.JSONObject table=tables.optJSONObject(i);if(table==null)continue;
            String kind=table.optString("kind","");
            if(!selectedTab.equals("Clasificación")&&selectedMatchGroup>=0&&kind.equals("matches")){
                int groupIndex=0;for(int k=selectedTab.equals("Resultados")?tables.length()-1:0;selectedTab.equals("Resultados")?k>i:k<i;k+=selectedTab.equals("Resultados")?-1:1){org.json.JSONObject prev=tables.optJSONObject(k);if(prev!=null&&prev.optString("kind").equals("matches")&&prev.optJSONArray("rows")!=null&&prev.optJSONArray("rows").length()>0){
                    boolean any=false;
                    for(int n=0;n<prev.optJSONArray("rows").length();n++){org.json.JSONObject item=prev.optJSONArray("rows").optJSONObject(n);if(item!=null&&matchVisible(item)){any=true;break;}}
                    if(any)groupIndex++;
                }}
                if(groupIndex!=selectedMatchGroup)continue;
            }
            org.json.JSONArray rows=table.optJSONArray("rows");if(rows==null)continue;
            if(selectedTab.equals("Clasificación")){
                if(!kind.equals("standing"))continue;
                body.addView(txt("Desliza las estadísticas horizontalmente",11,Color.LTGRAY));
                String[] labels={"#","Equipo","J","G","E","P","TF","TC","DT","EF","EC","BO","BD"};
                int unit=(int)(getResources().getDisplayMetrics().density*34);
                java.util.ArrayList<android.widget.HorizontalScrollView> scrollRows=new java.util.ArrayList<>();
                LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);heading.setBackgroundColor(panel);body.addView(heading);
                addStandingFixed(heading,"#",unit*3/5,accent);
                addStandingFixed(heading,"Equipo",(int)(getResources().getDisplayMetrics().density*168),accent);
                android.widget.HorizontalScrollView headerScroll=new android.widget.HorizontalScrollView(this);
                headerScroll.setHorizontalScrollBarEnabled(false);
                headerScroll.setClipToPadding(true);
                LinearLayout headerStats=new LinearLayout(this);
                for(int k=2;k<labels.length;k++)addStandingFixed(headerStats,labels[k],unit,accent);
                headerScroll.addView(headerStats);heading.addView(headerScroll,new LinearLayout.LayoutParams(0,-2,1));
                addStandingPoints(heading,"Pts",unit,accent,panel);
                scrollRows.add(headerScroll);
                for(int j=0;j<rows.length();j++){
                    org.json.JSONObject row=rows.optJSONObject(j);if(row==null)continue;
                    LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);line.setMinimumHeight((int)(40*getResources().getDisplayMetrics().density));line.setBackgroundColor(j%2==0?panel:bg);body.addView(line);
                    org.json.JSONObject stats=row.optJSONObject("stats");
                    String[] vals={row.optString("position",""),row.optString("team",""),row.optString("played",""),
                        standingStat(stats,"g","ganados","victorias","pg","v"),
                        standingStat(stats,"e","empatados","empates","pe"),
                        standingStat(stats,"p","perdidos","derrotas","pp"),
                        standingStat(stats,"tf","tantos favor","tantos a favor","puntos a favor"),
                        standingStat(stats,"tc","tantos contra","tantos en contra","puntos en contra"),
                        standingStat(stats,"dt","diferencia tantos","diferencia","dif"),
                        standingStat(stats,"ef","ensayos favor","ensayos a favor"),
                        standingStat(stats,"ec","ensayos contra","ensayos en contra"),
                        standingStat(stats,"bo","bonus ofensivo"),
                        standingStat(stats,"bd","bonus defensivo"),
                        row.optString("points","—")};
                    addStandingFixed(line,vals[0],unit*3/5,Color.WHITE);
                    addStandingTeam(line,vals[1],row.optString("crest",""),(int)(getResources().getDisplayMetrics().density*168));
                    android.widget.HorizontalScrollView scroller=new android.widget.HorizontalScrollView(this);
                    scroller.setHorizontalScrollBarEnabled(false);
                    scroller.setClipToPadding(true);
                    LinearLayout statsLine=new LinearLayout(this);
                    for(int k=2;k<vals.length-1;k++)addStandingFixed(statsLine,vals[k],unit,Color.WHITE);
                    scroller.addView(statsLine);line.addView(scroller,new LinearLayout.LayoutParams(0,-2,1));
                    addStandingPoints(line,vals[vals.length-1],unit,accent,j%2==0?panel:bg);
                    scrollRows.add(scroller);
                    if(false){
                        if(stats!=null){
                            StringBuilder detail=new StringBuilder();
                            java.util.Iterator<String> keys=stats.keys();
                            while(keys.hasNext()){String key=keys.next();if(detail.length()>0)detail.append("   ·   ");detail.append(key).append(": ").append(stats.optString(key));}
                            TextView extra=txt(detail.toString(),11,Color.LTGRAY);extra.setBackgroundColor(j%2==0?panel:bg);body.addView(extra);
                        }
                    }
                    count++;
                }
                for(android.widget.HorizontalScrollView scroller:scrollRows){
                    scroller.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->{
                        for(android.widget.HorizontalScrollView other:scrollRows)if(other!=v&&other.getScrollX()!=x)other.scrollTo(x,0);
                    });
                }
            }else{
                if(!kind.equals("matches"))continue;
                if(rows.length()==0)continue;
                String full=table.optString("heading","Jornada");
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)Jornada\\s+([0-9]+(?:\\.[0-9]+)?)").matcher(full);
                String number=m.find()?m.group(1):"";
                String phase=full.toLowerCase(java.util.Locale.ROOT).contains("2ª fase")?"2.ª fase":full.toLowerCase(java.util.Locale.ROOT).contains("1ª fase")?"1.ª fase":"";
                int organizer=full.toLowerCase(java.util.Locale.ROOT).indexOf("organizador:");
                int visibleRows=0;
                for(int n=0;n<rows.length();n++){org.json.JSONObject candidate=rows.optJSONObject(n);if(candidate!=null&&matchVisible(candidate))visibleRows++;}
                if(visibleRows==0)continue;
                TextView head=txt(number.isEmpty()?full:"Jornada "+number+(phase.isEmpty()?"":" · "+phase),15,accent);head.setTypeface(null,Typeface.BOLD);body.addView(head);
                if(organizer>=0)body.addView(txt("Organiza: "+full.substring(organizer+"organizador:".length()).replaceFirst("^\\s*[:\\-]\\s*","").trim(),12,Color.LTGRAY));
                for(int j=0;j<rows.length();j++){
                    org.json.JSONObject row=rows.optJSONObject(j);if(row==null)continue;
                    String score=row.optString("score","");
                    if(!matchVisible(row))continue;
                    LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(10,8,10,8);card.setBackgroundColor(panel);
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,3,0,3);body.addView(card,cp);
                    card.addView(txt(row.optString("when",""),12,selectedTab.equals("Calendario")&&!isUpcomingMatch(row)?Color.LTGRAY:accent));
                    LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(line);
                    TextView left=txt(row.optString("home",""),13,Color.WHITE);
                    TextView mid=txt(selectedTab.equals("Calendario")&&isUpcomingMatch(row)?"VS":isScore(score)?score:"VS",19,selectedTab.equals("Calendario")&&!isUpcomingMatch(row)?Color.LTGRAY:accent);mid.setTypeface(null,Typeface.BOLD);mid.setGravity(android.view.Gravity.CENTER);
                    TextView right=txt(row.optString("away",""),13,Color.WHITE);right.setGravity(android.view.Gravity.RIGHT);
                    line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
                    line.addView(mid,new LinearLayout.LayoutParams(-2,-2));
                    line.addView(right,new LinearLayout.LayoutParams(0,-2,1));
                    String extra=row.optString("extra","");if(!extra.isEmpty()){TextView details=txt(extra,11,Color.LTGRAY);details.setVisibility(View.GONE);card.addView(details);card.setOnClickListener(v->details.setVisibility(details.getVisibility()==View.GONE?View.VISIBLE:View.GONE));}
                    count++;
                }
            }
        }
        if(count==0)body.addView(txt(selectedTab.equals("Resultados")?"No hay resultados finalizados disponibles en los datos importados.":selectedTab.equals("Calendario")&&calendarUpcomingOnly?"No hay próximos partidos en los datos guardados. Pulsa «Ver todas las jornadas» o «Actualizar» para consultar novedades.":"No hay datos estructurados para esta pestaña. Pulsa Actualizar para volver a importar desde MatchReady.",13,Color.LTGRAY));
        else body.addView(txt("MatchReady · Datos guardados en el móvil",11,Color.LTGRAY));
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
                    String js="(function(){try{\nvar norm=function(s){return (s||'').trim().replace(/\\s+/g,' ')};\nvar tables=Array.from(document.querySelectorAll('table')).map(function(t){\n var tr=Array.from(t.querySelectorAll('tr'));\n var first=tr.find(function(r){return r.querySelector('th')});\n if(!first)return null;\n var h=Array.from(first.children).map(function(c){return norm(c.innerText).toLowerCase()});\n var find=function(names){return h.findIndex(function(v){return names.some(function(n){return v===n||v.indexOf(n)>=0})})};\n var home=find(['equipo local']),away=find(['equipo visitante']),score=find(['resultado']),date=find(['fecha/hora']);\n var team=find(['equipo']),played=find(['j']),points=find(['puntos','ptos','pts','punt']),position=0;\n var kind=home>=0&&away>=0&&score>=0&&date>=0?'matches':team>=0&&played>=0&&points>=0?'standing':'other';\n if(kind==='other')return null;\n var node=t,heading='';\n for(var n=0;n<8&&node;n++){node=node.previousElementSibling||node.parentElement;if(!node||node===document.body)break;if(node.matches&&node.matches('h1,h2,h3,h4,h5')){heading=norm(node.innerText);break}var x=node.querySelector&&node.querySelector('h1,h2,h3,h4,h5');if(x){heading=norm(x.innerText);break}}\n var rows=tr.slice(tr.indexOf(first)+1).map(function(r){\n var cells=Array.from(r.children).filter(function(c){return c.matches('td,th')}).map(function(c){return norm(c.innerText)});\n if(kind==='matches'){if(cells.length<=Math.max(home,away,score,date))return null;var a=cells[home],b=cells[away];if(!a||!b||a.toLowerCase()==='equipo local'||b.toLowerCase()==='equipo visitante')return null;return {when:cells[date],home:a.replace(/Árbitro:.*$/i,'').trim(),score:cells[score],away:b.replace(/Campo:.*$/i,'').trim(),extra:((a.match(/Árbitro:.*$/i)||[])[0]||'')+' '+((b.match(/Campo:.*$/i)||[])[0]||'')}}\n if(cells.length<=Math.max(team,played,points))return null;\n var stats={};h.forEach(function(label,index){if(index!==position&&index!==team&&index!==played&&index!==points&&label)stats[label]=cells[index]||''});\n var teamCell=Array.from(r.children).filter(function(c){return c.matches('td,th')})[team];var crestImg=teamCell&&teamCell.querySelector('img');var crest=crestImg?(crestImg.currentSrc||crestImg.src||''):'';return {position:cells[position],team:cells[team],played:cells[played],points:cells[points],stats:stats,crest:crest};\n }).filter(Boolean);\n return {kind:kind,heading:heading|| (kind==='matches'?'Jornada':'Clasificación'),rows:rows};\n}).filter(Boolean);\nreturn JSON.stringify({url:location.href,title:document.title,format:2,tables:tables});\n}catch(e){return JSON.stringify({error:String(e)})}})()";
                    view.evaluateJavascript(js,result->{
                        try{
                            String decoded=new org.json.JSONTokener(result).nextValue().toString();
                            org.json.JSONObject obj=new org.json.JSONObject(decoded);
                            org.json.JSONArray tables=obj.optJSONArray("tables");
                            if(obj.has("error")){Toast.makeText(MainActivity.this,"Error de lectura: "+obj.optString("error"),Toast.LENGTH_LONG).show();return;}
                            if(tables==null||tables.length()==0){Toast.makeText(MainActivity.this,"No se encontraron tablas. Abre la fuente oficial.",Toast.LENGTH_LONG).show();return;}
                            int matchTables=0,standingTables=0;
                            for(int z=0;z<tables.length();z++){org.json.JSONObject t=tables.optJSONObject(z);if(t!=null){if("matches".equals(t.optString("kind")))matchTables++;if("standing".equals(t.optString("kind")))standingTables++;}}
                            if(matchTables==0&&standingTables==0){Toast.makeText(MainActivity.this,"No se han identificado tablas de partidos ni clasificación.",Toast.LENGTH_LONG).show();return;}
                            getPreferences(0).edit().putString(key,obj.toString()).apply();
                            Toast.makeText(MainActivity.this,"Importación: "+matchTables+" jornadas y "+standingTables+" clasificaciones",Toast.LENGTH_LONG).show();
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
