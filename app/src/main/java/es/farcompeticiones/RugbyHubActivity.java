package es.farcompeticiones;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class RugbyHubActivity extends Activity {
    private final String official="https://farugby.matchready.es/es/public/calendar/35/11/combined/";
    private LinearLayout list;
    private TextView screenTitle, screenSubtitle;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(Color.WHITE);if(bold)t.setTypeface(null,1);return t;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        String embeddedAct=getIntent().getStringExtra("far_act_url");
        if(embeddedAct!=null){
            android.widget.FrameLayout background=new android.widget.FrameLayout(this);
            background.setBackgroundColor(0xff101a24);
            setContentView(background);
            if(!embeddedAct.startsWith("https://farugby.matchready.es/")){finish();return;}
            background.post(()->showReadableAct(embeddedAct,
                getIntent().getStringExtra("far_home"),
                getIntent().getStringExtra("far_away")));
            return;
        }

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        list=new LinearLayout(this);list.setOrientation(1);list.setPadding(dp(16),dp(20),dp(16),dp(28));
        list.setBackgroundColor(0xff101a24);scroll.addView(list);setContentView(scroll);
        screenTitle=text("Rugby M14",25,true);list.addView(screenTitle);
        screenSubtitle=text("Todas las jornadas y organizadores sincronizados",13,false);
        screenSubtitle.setTextColor(0xffaab9c9);list.addView(screenSubtitle);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);list.addView(actions);
        Button matches=new Button(this);matches.setText("PARTIDOS");actions.addView(matches,new LinearLayout.LayoutParams(0,dp(55),1));
        Button table=new Button(this);table.setText("CLASIFICACIÓN");actions.addView(table,new LinearLayout.LayoutParams(0,dp(55),1));
        matches.setOnClickListener(v->showMatches());
        table.setOnClickListener(v->showClassification());
        showMatches();
    }
    private void clear(){while(list.getChildCount()>3)list.removeViewAt(3);}
    private void showClassification(){
        clear();
        screenTitle.setText("Clasificación M14");
        screenSubtitle.setText("2026/27 · Andaluz M14 XV · 1ª División");
        Button refresh=new Button(this);refresh.setText("↻ ACTUALIZAR");
        refresh.setTextSize(12);refresh.setAllCaps(false);
        LinearLayout.LayoutParams refreshParams=new LinearLayout.LayoutParams(-2,dp(40));
        list.addView(refresh,refreshParams);
        TextView status=text("Cargando clasificación oficial de MatchReady…",13,false);
        status.setTextColor(0xffaab9c9);status.setTextSize(11);status.setPadding(0,dp(2),0,dp(5));
        list.addView(status);
        LinearLayout results=new LinearLayout(this);results.setOrientation(1);list.addView(results);
        Button webButton=new Button(this);webButton.setText("VER CLASIFICACIÓN EN MATCHREADY");
        list.addView(webButton);webButton.setOnClickListener(v->openOfficial());
        android.webkit.WebView web=new android.webkit.WebView(this);
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.setVisibility(View.GONE);list.addView(web,new LinearLayout.LayoutParams(1,1));
        final Runnable[] load=new Runnable[1];
        load[0]=()->{
            status.setText("Actualizando desde MatchReady…");results.removeAllViews();
            web.loadUrl(official);
        };
        web.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public void onPageFinished(android.webkit.WebView view,String url){
                String script="(function(){try{"+
                    "var tables=[...document.querySelectorAll('table')];"+
                    "var table=tables.find(t=>{var head=(t.querySelector('thead')||t).innerText.toUpperCase();return /PUNTOS/.test(head)&&/BO/.test(head)&&/BD/.test(head);});"+
                    "if(!table)return JSON.stringify({error:'No se ha encontrado la tabla de clasificación'});"+
                    "var rows=[...table.querySelectorAll('tr')].map(tr=>{"+
                    "var cells=[...tr.querySelectorAll('td')];if(cells.length<10)return null;"+
                    "var img=tr.querySelector('img');"+
                    "return {values:cells.map(c=>c.innerText.trim()),logo:img?img.src:''};"+
                    "}).filter(Boolean);"+
                    "return JSON.stringify({rows:rows});"+
                    "}catch(e){return JSON.stringify({error:String(e)});}})()";
                view.evaluateJavascript(script,raw->{
                    try{
                        JSONObject obj=new JSONObject(new JSONTokener(raw).nextValue().toString());
                        if(obj.has("error"))throw new Exception(obj.optString("error"));
                        JSONArray rows=obj.optJSONArray("rows");
                        if(rows==null||rows.length()==0)throw new Exception("La tabla oficial no contiene equipos");
                        results.removeAllViews();
                        TextView hint=text("Desliza estadísticas · Pulsa un equipo para ampliar",11,false);
                        hint.setTextColor(0xffaab9c9);hint.setPadding(0,dp(2),0,dp(5));
                        results.addView(hint);
                        LinearLayout tableLayout=new LinearLayout(RugbyHubActivity.this);
                        tableLayout.setOrientation(LinearLayout.HORIZONTAL);
                        results.addView(tableLayout,new LinearLayout.LayoutParams(-1,-2));
                        LinearLayout fixedLeft=new LinearLayout(RugbyHubActivity.this);
                        fixedLeft.setOrientation(1);
                        tableLayout.addView(fixedLeft,new LinearLayout.LayoutParams(dp(158),-2));
                        HorizontalScrollView horizontal=new HorizontalScrollView(RugbyHubActivity.this);
                        horizontal.setHorizontalScrollBarEnabled(true);
                        tableLayout.addView(horizontal,new LinearLayout.LayoutParams(0,-2,1));
                        LinearLayout middle=new LinearLayout(RugbyHubActivity.this);
                        middle.setOrientation(1);horizontal.addView(middle);
                        LinearLayout fixedRight=new LinearLayout(RugbyHubActivity.this);
                        fixedRight.setOrientation(1);
                        tableLayout.addView(fixedRight,new LinearLayout.LayoutParams(dp(51),-2));
                        final int rowHeight=50, headerHeight=36, statWidth=37;
                        LinearLayout leftHeader=new LinearLayout(RugbyHubActivity.this);
                        leftHeader.setGravity(Gravity.CENTER_VERTICAL);leftHeader.setBackgroundColor(0xff30475d);
                        fixedLeft.addView(leftHeader,new LinearLayout.LayoutParams(-1,dp(headerHeight)));
                        TextView positionHeader=text("#",12,true);positionHeader.setGravity(Gravity.CENTER);
                        leftHeader.addView(positionHeader,new LinearLayout.LayoutParams(dp(30),-1));
                        TextView teamHeader=text("EQUIPO",12,true);
                        teamHeader.setGravity(Gravity.CENTER_VERTICAL);
                        leftHeader.addView(teamHeader,new LinearLayout.LayoutParams(0,-1,1));
                        LinearLayout midHeader=new LinearLayout(RugbyHubActivity.this);
                        midHeader.setGravity(Gravity.CENTER_VERTICAL);midHeader.setBackgroundColor(0xff30475d);
                        middle.addView(midHeader,new LinearLayout.LayoutParams(-2,dp(headerHeight)));
                        String[] columns={"J","G","E","P","TF","TC","DT","EF","EC","BO","BD"};
                        for(String label:columns){
                            TextView h=text(label,11,true);h.setGravity(Gravity.CENTER);
                            midHeader.addView(h,new LinearLayout.LayoutParams(dp(statWidth),-1));
                        }
                        TextView pointsHeader=text("PTS",12,true);
                        pointsHeader.setGravity(Gravity.CENTER);pointsHeader.setBackgroundColor(0xff30475d);
                        fixedRight.addView(pointsHeader,new LinearLayout.LayoutParams(-1,dp(headerHeight)));
                        LinearLayout detailsArea=new LinearLayout(RugbyHubActivity.this);
                        detailsArea.setOrientation(1);results.addView(detailsArea);
                        int shown=0;
                        for(int i=0;i<rows.length();i++){
                            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                            JSONArray cells=row.optJSONArray("values");
                            if(cells==null||cells.length()<14)continue;
                            String rank=cells.optString(0).trim();
                            if(!rank.matches("[0-9]+"))continue;
                            String team=cells.optString(1).trim();
                            final String[] values=new String[14];
                            for(int c=0;c<14;c++)values[c]=cells.optString(c);
                            int bg=shown%2==0?0xff203447:0xff192b3b;
                            LinearLayout leftRow=new LinearLayout(RugbyHubActivity.this);
                            leftRow.setGravity(Gravity.CENTER_VERTICAL);leftRow.setBackgroundColor(bg);
                            fixedLeft.addView(leftRow,new LinearLayout.LayoutParams(-1,dp(rowHeight)));
                            TextView rankView=text(rank+"º",15,true);rankView.setTextColor(0xff87d8b9);
                            rankView.setGravity(Gravity.CENTER);
                            leftRow.addView(rankView,new LinearLayout.LayoutParams(dp(34),-1));
                            ImageView crest=teamLogo(row.optString("logo"));
                            leftRow.addView(crest,new LinearLayout.LayoutParams(dp(27),dp(27)));
                            TextView name=text(team,11,true);
                            name.setPadding(dp(5),0,dp(2),0);name.setMaxLines(2);
                            leftRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                            LinearLayout midRow=new LinearLayout(RugbyHubActivity.this);
                            midRow.setGravity(Gravity.CENTER_VERTICAL);midRow.setBackgroundColor(bg);
                            middle.addView(midRow,new LinearLayout.LayoutParams(-2,dp(rowHeight)));
                            for(int c=2;c<=12;c++){
                                TextView cell=text(values[c],12,false);cell.setGravity(Gravity.CENTER);
                                midRow.addView(cell,new LinearLayout.LayoutParams(dp(statWidth),-1));
                            }
                            TextView points=text(values[13],16,true);
                            points.setGravity(Gravity.CENTER);points.setTextColor(0xff87d8b9);
                            points.setBackgroundColor(bg);
                            fixedRight.addView(points,new LinearLayout.LayoutParams(-1,dp(rowHeight)));
                            final String selectedTeam=team;
                            View.OnClickListener expand=v->{
                                detailsArea.removeAllViews();
                                LinearLayout detail=new LinearLayout(RugbyHubActivity.this);
                                detail.setOrientation(1);
                                detail.setPadding(dp(13),dp(12),dp(13),dp(13));
                                detail.setBackgroundColor(0xff293f52);
                                LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);
                                detailParams.topMargin=dp(10);detailsArea.addView(detail,detailParams);
                                TextView detailTitle=text(selectedTeam+" · "+values[13]+" puntos",16,true);
                                detail.addView(detailTitle);
                                String[] labels={"Partidos jugados","Victorias","Empates","Derrotas",
                                    "Tantos a favor","Tantos en contra","Diferencia",
                                    "Ensayos a favor","Ensayos en contra",
                                    "Bonus ofensivo","Bonus defensivo"};
                                for(int c=2;c<=12;c++){
                                    TextView detailLine=text(labels[c-2]+": "+values[c],14,false);
                                    detailLine.setPadding(0,dp(5),0,0);detail.addView(detailLine);
                                }
                                TextView close=text("Cerrar detalles  ×",13,true);
                                close.setTextColor(0xff87d8b9);close.setPadding(0,dp(12),0,0);
                                detail.addView(close);close.setOnClickListener(w->detailsArea.removeAllViews());
                            };
                            leftRow.setOnClickListener(expand);
                            midRow.setOnClickListener(expand);
                            points.setOnClickListener(expand);
                            shown++;
                        }
                        if(shown==0)throw new Exception("No se pudieron interpretar las posiciones");
                        TextView legendTitle=text("LEYENDA DE LA CLASIFICACIÓN",14,true);
                        legendTitle.setPadding(0,dp(16),0,dp(6));
                        results.addView(legendTitle);
                        LinearLayout legend=new LinearLayout(RugbyHubActivity.this);
                        legend.setOrientation(1);
                        legend.setPadding(dp(10),dp(9),dp(10),dp(9));
                        legend.setBackgroundColor(0xff203447);
                        results.addView(legend);
                        String[][] meanings={
                            {"J","Jugados"},{"G","Ganados"},{"E","Empatados"},{"P","Perdidos"},
                            {"TF","Tantos a favor"},{"TC","Tantos en contra"},{"DT","Diferencia"},
                            {"EF","Ensayos a favor"},{"EC","Ensayos en contra"},
                            {"BO","Bonus ofensivo"},{"BD","Bonus defensivo"},{"PTS","Puntos"}
                        };
                        for(int k=0;k<meanings.length;k+=2){
                            LinearLayout legendRow=new LinearLayout(RugbyHubActivity.this);
                            legendRow.setOrientation(LinearLayout.HORIZONTAL);
                            legend.addView(legendRow,new LinearLayout.LayoutParams(-1,-2));
                            for(int n=k;n<Math.min(k+2,meanings.length);n++){
                                TextView meaning=text(meanings[n][0]+" · "+meanings[n][1],12,false);
                                meaning.setTextColor(0xffb8c9d9);
                                meaning.setPadding(dp(3),dp(5),dp(3),dp(5));
                                legendRow.addView(meaning,new LinearLayout.LayoutParams(0,-2,1));
                            }
                        }
                        status.setText(shown+" equipos · Datos oficiales de MatchReady");
                    }catch(Exception e){
                        status.setText("No se ha podido leer la clasificación oficial. Puedes consultarla en MatchReady.");
                    }
                });
            }
        });
        refresh.setOnClickListener(v->load[0].run());
        load[0].run();
    }
    private void openOfficial(){startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(official)));}
    private void inspectOfficial(){
        clear();
        list.addView(text("Diagnóstico de jornadas oficiales",21,true));
        TextView status=text("Identificando cabeceras, filas, equipos, escudos y descargas…",14,false);
        list.addView(status);
        TextView report=text("",12,false);report.setTextIsSelectable(true);list.addView(report);
        Button copy=new Button(this);copy.setText("Copiar informe");list.addView(copy);
        copy.setOnClickListener(v->{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Rugby",report.getText()));
            Toast.makeText(this,"Informe copiado",Toast.LENGTH_SHORT).show();
        });
        Button back=new Button(this);back.setText("Volver a partidos");list.addView(back);
        back.setOnClickListener(v->showMatches());
        android.webkit.WebView web=new android.webkit.WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setVisibility(View.GONE);list.addView(web,new LinearLayout.LayoutParams(1,1));
        final boolean[] finished={false};
        web.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public void onPageFinished(android.webkit.WebView v,String url){
                v.postDelayed(()->v.evaluateJavascript(
                    "(function(){let o={url:location.href,title:document.title,sections:[],tables:[],links:[]};"+
                    "let heads=[...document.querySelectorAll('h1,h2,h3,h4,.card-header,.panel-heading,.accordion-header,[data-bs-toggle=collapse]')];"+
                    "o.sections=heads.slice(0,100).map(e=>({tag:e.tagName,text:(e.innerText||'').trim().slice(0,180)})).filter(x=>x.text);"+
                    "o.tables=[...document.querySelectorAll('table')].slice(0,60).map((t,i)=>({index:i,rows:[...t.querySelectorAll('tr')].slice(0,80).map(r=>({cells:[...r.querySelectorAll('th,td')].map(c=>({text:(c.innerText||'').trim().slice(0,160),images:[...c.querySelectorAll('img')].map(im=>({src:im.src,alt:im.alt})),links:[...c.querySelectorAll('a[href]')].map(a=>({text:(a.innerText||'').trim(),href:a.href}))}))}))}));"+
                    "o.links=[...document.querySelectorAll('a[href]')].filter(a=>/clasific|acta|descarga|pdf/i.test(a.innerText+' '+a.href)).slice(0,80).map(a=>({text:(a.innerText||'').trim(),href:a.href}));"+
                    "return JSON.stringify(o)})()",value->{
                        if(finished[0])return;
                        try{
                            String decoded=new JSONTokener(value).nextValue().toString();
                            JSONObject obj=new JSONObject(decoded);
                            JSONArray tables=obj.optJSONArray("tables");
                            JSONArray sections=obj.optJSONArray("sections");
                            report.setText(obj.toString(2));
                            status.setText("Lectura completada: "+(sections==null?0:sections.length())+
                                " cabeceras y "+(tables==null?0:tables.length())+
                                " tablas. Copia el informe para revisar cada campo.");
                            finished[0]=true;
                        }catch(Exception ex){status.setText("No se pudo interpretar la estructura: "+ex.getMessage());}
                        web.destroy();
                    }),1800);
            }
            @Override public void onReceivedError(android.webkit.WebView v,
                android.webkit.WebResourceRequest req,android.webkit.WebResourceError err){
                if(req.isForMainFrame())status.setText("No se pudo abrir la página oficial.");
            }
        });
        web.loadUrl(official);
    }
    private void updateOfficial(TextView status){
        status.setText("Leyendo jornadas y partidos oficiales…");
        android.webkit.WebView web=new android.webkit.WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setVisibility(View.GONE);
        list.addView(web,new LinearLayout.LayoutParams(1,1));
        final boolean[] received={false};
        web.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public void onPageFinished(android.webkit.WebView v,String url){
                v.postDelayed(()->v.evaluateJavascript(
                    "(function(){"+
                    "let hs=[...document.querySelectorAll('h4')].map(x=>(x.innerText||'').trim()).filter(x=>/^Jornada\\s+\\d|^JORNADA RECUPERACI/i.test(x));"+
                    "let ts=[...document.querySelectorAll('table')].filter(t=>{let r=t.querySelector('tr');return r&&/Fecha\\s*\\/\\s*Hora/i.test(r.innerText||'')&&/Equipo local/i.test(r.innerText||'')});"+
                    "let out=[];ts.forEach((t,i)=>{let round=hs[i]||('Jornada '+(i+1));"+
                    "[...t.querySelectorAll('tr')].slice(1).forEach(r=>{let c=[...r.querySelectorAll('td')];if(c.length<7)return;"+
                    "let first=x=>(x.innerText||'').trim().split('\\n')[0].trim();"+
                    "let home=first(c[1]),away=first(c[5]);if(!home||!away||/descanso/i.test(home+' '+away))return;"+
                    "let date=first(c[0]),time=((c[0].innerText||'').match(/\\b\\d{1,2}:\\d{2}\\b/)||[])[0]||'';"+
                    "let score=first(c[3]);if(!/^\\d+\\s*[-–]\\s*\\d+$/.test(score))score='';"+
                    "let hi=c[2].querySelector('img'),ai=c[4].querySelector('img');"+
                    "let stats=c[3].querySelector('a[href]'),act=c[6].querySelector('a[href]');"+
                    "out.push({jornada:round,date:date,time:time,home:home,away:away,score:score,"+
                    "homeLogo:hi?hi.src:'',awayLogo:ai?ai.src:'',stats:stats?stats.href:'',act:act?act.href:''});"+
                    "})});return JSON.stringify({rounds:hs,matches:out})})()",value->{
                    if(received[0])return;
                    try{
                        JSONObject payload=new JSONObject(new JSONTokener(value).nextValue().toString());
                        JSONArray rows=payload.getJSONArray("matches");
                        JSONArray rounds=payload.getJSONArray("rounds");
                        if(rows.length()<1||rounds.length()<1){
                            status.setText("No se reconocieron las jornadas. Se mantienen los datos anteriores.");
                        }else{
                            received[0]=true;
                            getSharedPreferences("rugby_official",MODE_PRIVATE).edit()
                                .putString("matches",rows.toString())
                                .putString("rounds",rounds.toString())
                                .putLong("updated",System.currentTimeMillis()).apply();
                            showMatches();
                        }
                    }catch(Exception ex){
                        status.setText("Error interpretando las tablas oficiales. No se han borrado datos.");
                    }
                    web.destroy();
                }),1800);
            }
            @Override public void onReceivedError(android.webkit.WebView v,
                android.webkit.WebResourceRequest request,android.webkit.WebResourceError error){
                if(request.isForMainFrame())status.setText("Sin conexión con Matchready. Se conservan los datos anteriores.");
            }
        });
        web.loadUrl(official);
    }
    private JSONArray parseOfficial(String raw)throws Exception{
        JSONArray out=new JSONArray();
        String date="",organizer="";
        java.util.regex.Pattern datePattern=java.util.regex.Pattern.compile(
            "(?<![0-9])([0-3]?[0-9])[/.-]([01]?[0-9])[/.-](20[0-9]{2})(?![0-9])");
        java.util.regex.Pattern hourPattern=java.util.regex.Pattern.compile(
            "(?<![0-9])(?:[01]?[0-9]|2[0-3]):[0-5][0-9](?![0-9])");
        java.util.regex.Pattern scorePattern=java.util.regex.Pattern.compile(
            "(?<![0-9])([0-9]{1,3})\\s*[-–]\\s*([0-9]{1,3})(?![0-9])");
        java.util.ArrayList<String> pending=new java.util.ArrayList<>();
        String[] lines=raw.split("\\n");
        for(int i=0;i<=lines.length;i++){
            String line=i<lines.length?lines[i].trim():"";
            java.util.regex.Matcher dm=datePattern.matcher(line);
            boolean isDate=dm.find();
            boolean isOrganizer=line.toLowerCase(Locale.ROOT).startsWith("organizador:");
            if((isDate||isOrganizer||i==lines.length)&&!pending.isEmpty()){
                String block=android.text.TextUtils.join("\n",pending);
                java.util.regex.Matcher tm=hourPattern.matcher(block);
                java.util.regex.Matcher sm=scorePattern.matcher(block);
                // Parse rows even without kickoff or final score. Matchready may
                // render pending fixtures as "Local / vs / Visitante".
                java.util.ArrayList<String> tokens=new java.util.ArrayList<>();
                for(String part:block.split("\\n")){
                    String t=part.trim();
                    if(!t.isEmpty()&&!t.equalsIgnoreCase("vs")&&!t.equals("-")
                        &&!t.equalsIgnoreCase("pendiente"))tokens.add(t);
                }
                String time="",home="",away="",score="";
                if(tm.find()){
                    time=tm.group();
                    String after=block.substring(tm.end()).trim();
                    int nl=after.indexOf('\n');
                    home=(nl<0?after:after.substring(0,nl)).trim();
                }
                if(sm.find()){
                    score=sm.group(1)+" - "+sm.group(2);
                    String after=block.substring(sm.end()).trim();
                    int nl=after.indexOf('\n');
                    away=(nl<0?after:after.substring(0,nl)).trim();
                }
                if(home.isEmpty()||away.isEmpty()){
                    java.util.ArrayList<String> teams=new java.util.ArrayList<>();
                    for(String token:tokens){
                        if(hourPattern.matcher(token).matches()||scorePattern.matcher(token).matches())continue;
                        if(token.matches("(?i)(jornada|grupo|campo|árbitro|arbitro|hora|resultado).*"))continue;
                        if(token.length()>90)continue;
                        teams.add(token);
                    }
                    if(teams.size()>=2){
                        if(home.isEmpty())home=teams.get(0);
                        if(away.isEmpty())away=teams.get(teams.size()-1);
                    }
                }
                if(!date.isEmpty()&&!home.isEmpty()&&!away.isEmpty()
                    &&!home.equalsIgnoreCase(away)){
                    JSONObject item=new JSONObject();
                    item.put("date",date);item.put("organizer",organizer);
                    item.put("home",home);item.put("away",away);
                    item.put("time",time);item.put("score",score);
                    out.put(item);
                }
                pending.clear();
            }
            if(isDate)date=String.format(Locale.ROOT,"%02d/%02d/%s",
                Integer.parseInt(dm.group(1)),Integer.parseInt(dm.group(2)),dm.group(3));
            if(isOrganizer)organizer=line.substring(line.indexOf(':')+1).trim();
            if(i<lines.length&&!isDate&&!isOrganizer&&!line.isEmpty()
                &&!line.toLowerCase(Locale.ROOT).startsWith("fecha/hora"))pending.add(line);
        }
        return out;
    }
    private void openMatchDetail(JSONObject match){
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(1);content.setPadding(dp(18),dp(12),dp(18),dp(8));
        content.setBackgroundColor(0xff223343);
        String round=match.optString("jornada");
        TextView title=text(round,17,true);content.addView(title);
        TextView fixture=text(match.optString("home")+"  "+
            (match.optString("score").isEmpty()?"VS":match.optString("score"))+
            "  "+match.optString("away"),18,true);
        fixture.setPadding(0,dp(14),0,dp(14));content.addView(fixture);
        detailLine(content,"Fecha",match.optString("date"));
        detailLine(content,"Hora",match.optString("time").isEmpty()?"Pendiente de confirmar":match.optString("time"));
        detailLine(content,"Árbitro",match.optString("referee"));
        detailLine(content,"Campo",match.optString("venue"));
        String stats=match.optString("stats"),act=match.optString("act");
        if(!stats.isEmpty()){
            Button button=new Button(this);button.setText("Ver estadísticas oficiales");
            content.addView(button);button.setOnClickListener(v->showStatistics(stats,match));
        }
        if(!act.isEmpty()){
            Button readable=new Button(this);readable.setText("Acta adaptada al móvil");
            content.addView(readable);readable.setOnClickListener(v->showReadableAct(act,match.optString("home"),match.optString("away")));
            Button button=new Button(this);button.setText("Abrir acta oficial");
            content.addView(button);button.setOnClickListener(v->showActPdf(act));
        }
        new AlertDialog.Builder(this).setView(content).setPositiveButton("Cerrar",null).show();
    }
    private void showReadableAct(String url,String homeName,String awayName){
        android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(10),dp(7),dp(10),dp(7));root.setBackgroundColor(0xff101a24);
        TextView heading=text("Acta oficial",17,true);root.addView(heading);
        TextView status=text("Cargando acta…",11,false);status.setTextColor(0xffaab9c9);root.addView(status);
        ScrollView scroll=new ScrollView(this);
        TextView body=text("",13,false);body.setTextIsSelectable(true);body.setLineSpacing(dp(3),1.0f);
        body.setPadding(dp(2),dp(12),dp(2),dp(12));scroll.addView(body);
        LinearLayout sections=new LinearLayout(this);sections.setOrientation(1);
        root.addView(sections);
        scroll.setFillViewport(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);root.addView(actions);
        final String[] diagnostics={""};
        Button debug=new Button(this);debug.setText("Diagnóstico");debug.setTextSize(10);debug.setAllCaps(false);debug.setSingleLine(true);debug.setEnabled(false);actions.addView(debug,new LinearLayout.LayoutParams(0,dp(46),1.1f));
        debug.setOnClickListener(v->{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
                getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico acta",diagnostics[0]));
            android.widget.Toast.makeText(this,"Diagnóstico copiado. Pégalo en el chat.",android.widget.Toast.LENGTH_LONG).show();
        });
        Button original=new Button(this);original.setText("PDF original");original.setTextSize(10);original.setAllCaps(false);original.setSingleLine(true);actions.addView(original,new LinearLayout.LayoutParams(0,dp(46),1.1f));
        original.setOnClickListener(v->showActPdf(url));
        Button close=new Button(this);close.setText("Cerrar");close.setTextSize(10);close.setAllCaps(false);close.setSingleLine(true);actions.addView(close,new LinearLayout.LayoutParams(0,dp(46),.75f));
        close.setOnClickListener(v->dialog.dismiss());
        // The reader was launched in a dedicated activity. Closing the dialog
        // must also close that activity; otherwise its empty background remains.
        if(getIntent().hasExtra("far_act_url")){
            dialog.setOnDismissListener(d->{
                if(!isFinishing())finish();
            });
        }
        dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);
        new Thread(()->{
            java.net.HttpURLConnection conn=null;
            try{
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
                conn=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
                conn.setConnectTimeout(10000);conn.setReadTimeout(20000);
                byte[] data;
                try(java.io.InputStream in=conn.getInputStream();
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                    byte[] buffer=new byte[16384];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    data=out.toByteArray();
                }
                StringBuilder extracted=new StringBuilder();
                String lineupColumns=null;
                String disciplinaryColumns=null;
                String scoreboardColumns=null;
                String coordinateDiagnosis="";
                String coordinateChangesResult=null;
                try(com.tom_roush.pdfbox.pdmodel.PDDocument pdf=
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(data)){
                    com.tom_roush.pdfbox.text.PDFTextStripper stripper=
                        new com.tom_roush.pdfbox.text.PDFTextStripper();
                    stripper.setSortByPosition(true);
                    if(pdf.getNumberOfPages()>0){
                        lineupColumns=coordinateLineups(pdf);
                        if(lineupColumns==null)lineupColumns=extractLineupColumns(pdf);
                    }
                    disciplinaryColumns=extractDisciplinaryColumns(pdf);
                    scoreboardColumns=extractScoreboardColumns(pdf);
                    coordinateDiagnosis=diagnoseScoreCoordinates(pdf);
                    String coordinateChanges=positionEventTable(pdf,"Cambios",true);
                    String coordinateTemporary=positionEventTable(pdf,"Expulsiones temporales",false);
                    String coordinateDefinitive=positionEventTable(pdf,"Expulsiones definitivas",false);
                    if(coordinateTemporary!=null||coordinateDefinitive!=null)
                        disciplinaryColumns="TEMPORALES\n"+(coordinateTemporary==null?"":coordinateTemporary)+
                            "\nDEFINITIVAS\n"+(coordinateDefinitive==null?"":coordinateDefinitive);
                    coordinateChangesResult=coordinateChanges;
                    String coordinateScore=scoreFromCoordinates(coordinateDiagnosis);
                    if(coordinateScore!=null)scoreboardColumns=coordinateScore;
                    for(int page=1;page<=pdf.getNumberOfPages();page++){
                        stripper.setStartPage(page);stripper.setEndPage(page);
                        extracted.append("PÁGINA ").append(page).append("\n\n")
                            .append(stripper.getText(pdf)).append("\n\n");
                    }
                }
                String content=extracted.toString().trim();
                final String safeLineups=lineupColumns;
                final String safeDiscipline=disciplinaryColumns;
                final String safeScoreboard=scoreboardColumns;
                final String safeCoordinates=coordinateDiagnosis;
                final String safeChanges=coordinateChangesResult;
                final String rawDiagnosis="ACTA: "+url+"\\n"+
                    "EXTRACCION DE ZONA MARCADOR: "+(safeScoreboard==null?"NO DISPONIBLE":safeScoreboard)+"\\n"+
                    "PRUEBA DE COORDENADAS:\\n"+safeCoordinates+"\\n"+
                    "TEXTO COMPLETO DEL PDF:\\n"+content;
                runOnUiThread(()->{
                    if(!dialog.isShowing())return;
                    diagnostics[0]=rawDiagnosis;
                    debug.setEnabled(true);
                    if(content.isEmpty()){
                        status.setText("El PDF no contiene texto extraíble.");
                        body.setText("Consulta el documento original. No se han inventado datos.");
                    }else{
                        status.setText("Lectura adaptada al móvil");
                        java.util.LinkedHashMap<String,String> parsed=structuredActSections(content);
                        if(safeLineups!=null)parsed.put("Alineaciones",safeLineups);
                        if(safeDiscipline!=null)parsed.put("Expulsiones",safeDiscipline);
                        if(safeScoreboard!=null)parsed.put("Marcador",safeScoreboard);
                        if(safeChanges!=null)parsed.put("Cambios",safeChanges);
                        parsed.put("Estadísticas","");
                        sections.removeAllViews();
                        
                        LinearLayout tabStack=new LinearLayout(this);
                        tabStack.setOrientation(LinearLayout.VERTICAL);
                        sections.addView(tabStack);
                        java.util.ArrayList<Button> actTabs=new java.util.ArrayList<>();
                        LinearLayout tabRow=null;
                        int tabIndex=0;
                        for(java.util.Map.Entry<String,String> entry:parsed.entrySet()){
                            if(tabIndex==0||tabIndex==3){
                                tabRow=new LinearLayout(this);
                                tabRow.setOrientation(LinearLayout.HORIZONTAL);
                                tabStack.addView(tabRow);
                            }
                            Button tab=new Button(this);
                            tab.setText(entry.getKey());
                            tab.setTextSize(12);
                            tab.setAllCaps(false);
                            tab.setSingleLine(true);
                            tab.setMinWidth(0);
                            tab.setMinimumWidth(0);
                            tab.setMinHeight(0);
                            tab.setMinimumHeight(0);
                            tab.setPadding(dp(2),0,dp(2),0);
                            tab.setTextColor(android.graphics.Color.WHITE);
                            tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                android.graphics.Color.parseColor(tabIndex==0?"#32638B":"#354554")));
                            if(tabIndex==0)tab.setTypeface(null,android.graphics.Typeface.BOLD);
                            LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(0,dp(44),1f);
                            tabParams.setMargins(dp(2),0,dp(2),dp(2));
                            tabRow.addView(tab,tabParams);
                            actTabs.add(tab);
                            tabIndex++;
                            tab.setOnClickListener(v->{
                                for(Button other:actTabs){
                                    boolean selected=other==tab;
                                    other.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                        android.graphics.Color.parseColor(selected?"#32638B":"#354554")));
                                    other.setTextColor(android.graphics.Color.WHITE);
                                    other.setTypeface(null,selected?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                                }
                                if("Resumen".equals(entry.getKey()))
                                    showActSummary(scroll,content,homeName,awayName);
                                else if("Marcador".equals(entry.getKey()))
                                    showActScoreboard(scroll,entry.getValue(),safeLineups,homeName,awayName);
                                else if("Cambios".equals(entry.getKey()))
                                    showActChanges(scroll,entry.getValue(),safeLineups,homeName,awayName);
                                else if("Estadísticas".equals(entry.getKey()))
                                    showActMatchStatistics(scroll,safeScoreboard,safeDiscipline,safeChanges,safeLineups,homeName,awayName);
                                else if("Expulsiones".equals(entry.getKey()))
                                    showActDiscipline(scroll,entry.getValue(),parsed.get("Cambios"),safeLineups,homeName,awayName);
                                else if("Alineaciones".equals(entry.getKey()) && safeLineups!=null)
                                    showLineupCards(scroll,safeLineups,homeName,awayName);
                                else {scroll.removeAllViews();scroll.addView(body);body.setText(entry.getValue());}
                                scroll.post(()->scroll.smoothScrollTo(0,0));
                            });
                        }
                        String first=parsed.containsKey("Resumen")?"Resumen":parsed.keySet().iterator().next();
                        if("Resumen".equals(first))showActSummary(scroll,content,homeName,awayName);
                        else body.setText(parsed.get(first));
                    }
                });
            }catch(Exception ex){
                runOnUiThread(()->{if(dialog.isShowing())status.setText(
                    "No se pudo leer el acta. Puedes abrir el PDF original.");});
            }finally{if(conn!=null)conn.disconnect();}
        }).start();
    }
    private static final class MatchNumbers {
        int tries, conversions, penalties, drops, points, changes, cards;
        final java.util.ArrayList<int[]> events=new java.util.ArrayList<>();
        final java.util.LinkedHashMap<String,Integer> scorers=new java.util.LinkedHashMap<>();
    }
    private MatchNumbers readMatchNumbers(String section,String team,boolean away){
        MatchNumbers n=new MatchNumbers();
        if(section==null)return n;
        String[] lines=section.split("\\r?\\n");
        boolean active=false;
        for(String raw:lines){
            String line=raw.trim();
            if(line.startsWith("Equipo local:")){active=!away;continue;}
            if(line.startsWith("Equipo visitante:")){active=away;continue;}
            if(!active)continue;
            java.util.regex.Matcher event=java.util.regex.Pattern.compile(
                "(?i)Min\\s*(\\d+)\\s*[-·]\\s*([^\\n]*?)\\s*\\((\\d+)\\s*puntos?\\)\\s*[-·]\\s*Dorsal\\s*(\\d+)"
            ).matcher(line);
            if(event.find()){
                int minute=Integer.parseInt(event.group(1));
                int points=Integer.parseInt(event.group(3));
                String kind=event.group(2).toLowerCase(java.util.Locale.ROOT);
                if(kind.contains("ensayo"))n.tries++;
                else if(kind.contains("transform"))n.conversions++;
                else if(kind.contains("castigo")||kind.contains("penal"))n.penalties++;
                else if(kind.contains("drop"))n.drops++;
                n.points+=points;
                n.events.add(new int[]{minute,points});
                String dorsal=event.group(4);
                n.scorers.put(dorsal,n.scorers.containsKey(dorsal)?n.scorers.get(dorsal)+points:points);
            }
        }
        return n;
    }
    private int countActItems(String section,boolean away){
        if(section==null)return 0;
        boolean active=false;int count=0;
        for(String raw:section.split("\\r?\\n")){
            String line=raw.trim();
            if(line.startsWith("Equipo local:")){active=!away;continue;}
            if(line.startsWith("Equipo visitante:")){active=away;continue;}
            if(active&&line.matches("(?i)^\\d+\\.\\s*Min\\s*\\d+.*"))count++;
        }
        return count;
    }
    private void addStatTitle(LinearLayout host,String title){
        TextView label=text(title,15,true);
        label.setTextColor(0xff61dda5);
        label.setPadding(0,dp(15),0,dp(7));
        host.addView(label);
    }
    private final class ThreeDChart extends android.view.View {
        private final int[] values;
        private final String[] labels;
        private final int[] colors={0xff46dca0,0xff52a9f7,0xfff7b84b,0xffd47aff,0xffff7c8c,0xff66d7e9,0xffe8dd78,0xff9cc783};
        private final boolean pie;
        private final android.graphics.Paint p=new android.graphics.Paint(3);
        ThreeDChart(int[] values,String[] labels,boolean pie){
            super(RugbyHubActivity.this);
            this.values=values;this.labels=labels;this.pie=pie;
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);
        }
        private int shade(int color,float factor){
            return android.graphics.Color.rgb(
                Math.min(255,(int)(android.graphics.Color.red(color)*factor)),
                Math.min(255,(int)(android.graphics.Color.green(color)*factor)),
                Math.min(255,(int)(android.graphics.Color.blue(color)*factor)));
        }
        private void polygon(android.graphics.Canvas c,int color,float... xy){
            android.graphics.Path path=new android.graphics.Path();
            path.moveTo(xy[0],xy[1]);
            for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
            path.close();p.setColor(color);p.setStyle(android.graphics.Paint.Style.FILL);
            c.drawPath(path,p);
        }
        private void label(android.graphics.Canvas c,String text,float x,float y,float size,int color){
            p.setColor(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);c.drawText(text,x,y,p);
        }
        @Override protected void onMeasure(int w,int h){
            setMeasuredDimension(android.view.View.MeasureSpec.getSize(w),dp(pie?170:170));
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float width=getWidth(),height=getHeight(),density=getResources().getDisplayMetrics().density;
            canvas.save();canvas.scale(width/360f,height/170f);
            if(pie){
                int total=0;for(int v:values)total+=Math.max(0,v);
                if(total<=0){label(canvas,"Sin anotaciones registradas",180,95,15,0xffb6c6d1);canvas.restore();return;}
                float start=-90;
                android.graphics.RectF oval=new android.graphics.RectF(65,15,295,135);
                for(int depth=18;depth>=1;depth-=2){
                    android.graphics.RectF base=new android.graphics.RectF(65,15+depth,295,135+depth);
                    float angle=start;
                    for(int i=0;i<values.length;i++){
                        float sweep=360f*Math.max(0,values[i])/total;
                        p.setColor(shade(colors[i%colors.length],0.58f));canvas.drawArc(base,angle,sweep,true,p);
                        angle+=sweep;
                    }
                }
                for(int i=0;i<values.length;i++){
                    float sweep=360f*Math.max(0,values[i])/total;
                    p.setColor(colors[i%colors.length]);canvas.drawArc(oval,start,sweep,true,p);
                    android.graphics.RectF shine=new android.graphics.RectF(78,20,282,115);
                    p.setColor(0x22ffffff);canvas.drawArc(shine,start,sweep,true,p);
                    start+=sweep;
                }
            }else{
                int max=1;for(int v:values)max=Math.max(max,v);
                p.setColor(0xff31495b);p.setStrokeWidth(1);
                canvas.drawLine(24,137,335,137,p);
                for(int i=0;i<values.length;i++){
                    float x=75+i*160,w=65,depth=14;
                    float h=values[i]*92f/max;
                    float top=132-h;
                    int color=colors[i%colors.length];
                    polygon(canvas,shade(color,0.63f),x+w,top,x+w+depth,top-10,x+w+depth,132-10,x+w,132);
                    polygon(canvas,shade(color,1.18f),x,top,x+depth,top-10,x+w+depth,top-10,x+w,top);
                    p.setShader(new android.graphics.LinearGradient(x,top,x+w,top,
                        shade(color,1.07f),shade(color,0.80f),android.graphics.Shader.TileMode.CLAMP));
                    canvas.drawRect(x,top,x+w,132,p);p.setShader(null);
                    label(canvas,String.valueOf(values[i]),x+w/2,Math.max(18,top-16),17,0xffffffff);
                    label(canvas,labels[i],x+w/2+5,158,12,0xffe7f2f8);
                }
            }
            canvas.restore();
        }
    }
    private void addStatCompare(LinearLayout host,String label,int local,int visitor,int max){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(8),dp(8),dp(8));
        box.setBackgroundColor(0xff1b2d3f);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(7);host.addView(box,params);
        TextView heading=text(label,14,true);
        heading.setGravity(Gravity.CENTER);box.addView(heading);
        box.addView(new ThreeDChart(new int[]{local,visitor},new String[]{"Local","Visitante"},false));
    }
    private void addStatPie(LinearLayout host,String title,int[] values,String[] labels){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(8),dp(8),dp(8));
        box.setBackgroundColor(0xff1b2d3f);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(7);host.addView(box,params);
        TextView heading=text(title,14,true);
        heading.setGravity(Gravity.CENTER);box.addView(heading);
        box.addView(new ThreeDChart(values,labels,true));
        addStatLegend(box,values,labels);
    }
    private void addStatLegend(LinearLayout host,int[] values,String[] labels){
        int total=0;for(int v:values)total+=Math.max(0,v);
        int[] palette={0xff46dca0,0xff52a9f7,0xfff7b84b,0xffd47aff,0xffff7c8c,0xff66d7e9,0xffe8dd78,0xff9cc783};
        for(int i=0;i<values.length;i++){
            if(values[i]<=0)continue;
            String label=i<labels.length?labels[i]:"Otro";
            int percent=total==0?0:Math.round(values[i]*100f/total);
            TextView item=text("●  "+label+"   ·   "+values[i]+" puntos   ("+percent+"%)",12,false);
            item.setTextColor(palette[i%palette.length]);item.setPadding(dp(8),dp(2),dp(5),dp(2));
            host.addView(item);
        }
    }
    private void addStatTypeSelector(LinearLayout host,MatchNumbers local,MatchNumbers visitor,
            String home,String away){
        LinearLayout card=statCard(host);
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(tabs);
        TextView heading=text("",13,true);heading.setGravity(Gravity.CENTER);
        heading.setTextColor(0xffe7f2f8);heading.setPadding(0,dp(9),0,dp(3));card.addView(heading);
        LinearLayout graph=new LinearLayout(this);graph.setOrientation(LinearLayout.VERTICAL);
        card.addView(graph);
        String[] choices={"Local","Visitante","Ambos"};
        TextView[] buttons=new TextView[3];
        final int[][] scores={
            {5*local.tries,2*local.conversions,3*local.penalties,3*local.drops},
            {5*visitor.tries,2*visitor.conversions,3*visitor.penalties,3*visitor.drops},
            {5*local.tries,2*local.conversions,3*local.penalties,3*local.drops,
             5*visitor.tries,2*visitor.conversions,3*visitor.penalties,3*visitor.drops}};
        final String[] kinds={"Ensayos","Transformaciones","Castigos","Drops"};
        final String[] combined={"Local · Ensayos","Local · Transform.","Local · Castigos","Local · Drops",
            "Visitante · Ensayos","Visitante · Transform.","Visitante · Castigos","Visitante · Drops"};
        for(int i=0;i<3;i++){
            final int index=i;
            TextView button=text(choices[i],12,true);button.setGravity(Gravity.CENTER);
            button.setPadding(dp(3),dp(10),dp(3),dp(10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
            lp.setMargins(dp(2),0,dp(2),0);tabs.addView(button,lp);buttons[i]=button;
            button.setOnClickListener(v->{
                for(int j=0;j<3;j++){
                    buttons[j].setBackgroundColor(j==index?0xff28618a:0xff26394b);
                    buttons[j].setTextColor(j==index?0xffffffff:0xffb9cad7);
                }
                graph.removeAllViews();
                heading.setText(index==0?(home==null?"Local":home):
                    index==1?(away==null?"Visitante":away):"Ambos equipos · separados por equipo");
                graph.addView(new ThreeDChart(scores[index],index==2?combined:kinds,true));
                addStatLegend(graph,scores[index],index==2?combined:kinds);
            });
        }
        buttons[0].performClick();
    }
    private final class ScoreTimeline3D extends android.view.View {
        private final java.util.ArrayList<int[]> events;
        private final android.graphics.Paint paint=new android.graphics.Paint(3);
        ScoreTimeline3D(java.util.ArrayList<int[]> events){
            super(RugbyHubActivity.this);this.events=events;
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);
        }
        @Override protected void onMeasure(int w,int h){
            setMeasuredDimension(android.view.View.MeasureSpec.getSize(w),dp(200));
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float sx=getWidth()/360f,sy=getHeight()/200f;
            canvas.save();canvas.scale(sx,sy);
            paint.setColor(0xff273f53);paint.setStrokeWidth(1);
            for(int i=0;i<4;i++)canvas.drawLine(30,24+i*38,344,24+i*38,paint);
            if(events.isEmpty()){
                paint.setColor(0xffb7c9d5);paint.setTextSize(13);
                canvas.drawText("Sin cronología disponible",72,90,paint);
                canvas.restore();return;
            }
            int maxMinute=40,maxPoints=1,local=0,away=0;
            for(int[] e:events){maxMinute=Math.max(maxMinute,e[0]);if(e[2]==0)local+=e[1];else away+=e[1];}
            maxPoints=Math.max(1,Math.max(local,away));
            for(int team=0;team<2;team++){
                int color=team==0?0xff55dba3:0xff63b9ff;
                int score=0;float prevX=30,prevY=145;
                for(int i=0;i<=events.size();i++){
                    int[] event=i<events.size()?events.get(i):null;
                    float x=event==null?335:30+305f*event[0]/maxMinute;
                    float y=145-112f*score/maxPoints;
                    paint.setColor(team==0?0xff1a6853:0xff245b88);
                    paint.setStrokeWidth(5);canvas.drawLine(prevX+4,prevY+5,x+4,prevY+5,paint);
                    canvas.drawLine(x+4,prevY+5,x+4,y+5,paint);
                    paint.setColor(color);paint.setStrokeWidth(3);
                    canvas.drawLine(prevX,prevY,x,prevY,paint);canvas.drawLine(x,prevY,x,y,paint);
                    if(event!=null&&event[2]==team)score+=event[1];
                    prevX=x;prevY=y;
                }
            }
            paint.setTextSize(11);paint.setColor(0xffaebfca);
            canvas.drawText("0'",27,164,paint);
            canvas.drawText((maxMinute/2)+"'",175,164,paint);
            canvas.drawText(maxMinute+"'",310,164,paint);
            paint.setTextSize(13);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setColor(0xff55dba3);canvas.drawText(""+local,304,Math.max(20,145-112f*local/maxPoints-9),paint);
            paint.setColor(0xff63b9ff);canvas.drawText(""+away,304,Math.min(150,145-112f*away/maxPoints+17),paint);
            paint.setColor(0xff55dba3);canvas.drawText("● Local",35,183,paint);
            paint.setColor(0xff63b9ff);canvas.drawText("● Visitante",190,183,paint);
            canvas.restore();
        }
    }
    private void addStatTimeline(LinearLayout host,java.util.ArrayList<int[]> events){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackgroundColor(0xff1b2d3f);
        host.addView(card,new LinearLayout.LayoutParams(-1,-2));
        card.addView(new ScoreTimeline3D(events));
    }
    private void addStatScorers(LinearLayout host,String title,MatchNumbers numbers){
        addStatTitle(host,title);
        if(numbers.scorers.isEmpty()){host.addView(text("No hay anotadores identificados.",12,false));return;}
        int count=Math.min(4,numbers.scorers.size());
        int[] values=new int[count];String[] names=new String[count];int i=0;
        java.util.ArrayList<java.util.Map.Entry<String,Integer>> ordered=
            new java.util.ArrayList<>(numbers.scorers.entrySet());
        java.util.Collections.sort(ordered,(a,b)->Integer.compare(b.getValue(),a.getValue()));
        for(java.util.Map.Entry<String,Integer> e:ordered){
            if(i>=count)break;values[i]=e.getValue();names[i]="Nº "+e.getKey();i++;
        }
        addStatPie(host,"Puntos por anotador",values,names);
        addActStatBlock(host,"Desglose completo",formatScorers(numbers));
    }
    private final class CompactCompare3D extends android.view.View {
        private final int[][] values;
        private final String[] labels={"Ens.","Trans.","Cast.","Drops","Camb."};
        private final android.graphics.Paint paint=new android.graphics.Paint(3);
        CompactCompare3D(MatchNumbers local,MatchNumbers visitor){
            super(RugbyHubActivity.this);
            values=new int[][]{{local.tries,visitor.tries},{local.conversions,visitor.conversions},
                {local.penalties,visitor.penalties},{local.drops,visitor.drops},{local.changes,visitor.changes}};
        }
        @Override protected void onMeasure(int w,int h){
            setMeasuredDimension(android.view.View.MeasureSpec.getSize(w),dp(190));
        }
        @Override protected void onDraw(android.graphics.Canvas c){
            super.onDraw(c);c.save();c.scale(getWidth()/360f,getHeight()/200f);
            int max=1;for(int[] pair:values)for(int v:pair)max=Math.max(max,v);
            paint.setColor(0xff375064);paint.setStrokeWidth(1);c.drawLine(12,145,348,145,paint);
            for(int i=0;i<values.length;i++){
                float x=20+i*68;
                for(int j=0;j<2;j++){
                    float h=values[i][j]*112f/max,top=141-h,bx=x+j*24;
                    int color=j==0?0xff48dca2:0xff56aaf2;
                    android.graphics.Path side=new android.graphics.Path();
                    side.moveTo(bx+17,top);side.lineTo(bx+24,top-6);
                    side.lineTo(bx+24,135);side.lineTo(bx+17,141);side.close();
                    paint.setColor(j==0?0xff238b68:0xff296ca3);c.drawPath(side,paint);
                    paint.setShader(new android.graphics.LinearGradient(bx,top,bx+17,top,
                        j==0?0xffa0ffe0:0xffa4dbff,color,android.graphics.Shader.TileMode.CLAMP));
                    c.drawRect(bx,top,bx+17,141,paint);paint.setShader(null);
                    android.graphics.Path cap=new android.graphics.Path();
                    cap.moveTo(bx,top);cap.lineTo(bx+7,top-6);cap.lineTo(bx+24,top-6);
                    cap.lineTo(bx+17,top);cap.close();paint.setColor(0x88ffffff);c.drawPath(cap,paint);
                    paint.setColor(0xfff1f7fc);paint.setTextSize(13);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    c.drawText(""+values[i][j],bx+9,Math.max(17,top-9),paint);
                }
                paint.setColor(0xffdcebf5);paint.setTextSize(12);
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                c.drawText(labels[i],x+21,160,paint);
            }
            paint.setTextSize(13);paint.setTextAlign(android.graphics.Paint.Align.LEFT);
            paint.setColor(0xff48dca2);c.drawText("● Local",32,190,paint);
            paint.setColor(0xff56aaf2);c.drawText("● Visitante",195,190,paint);
            c.restore();
        }
    }
    private LinearLayout statCard(LinearLayout host){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9),dp(9),dp(9),dp(9));card.setBackgroundColor(0xff1b2d3f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);
        host.addView(card,p);return card;
    }
    private void addStatExpandable(LinearLayout host,String title,String details){
        LinearLayout card=statCard(host);
        TextView header=text(title+"  ▾",13,true);header.setTextColor(0xff64dfad);
        header.setPadding(dp(4),dp(5),dp(4),dp(5));card.addView(header);
        TextView body=text(details==null||details.trim().isEmpty()?
            "No hay datos registrados.":details,12,false);
        body.setPadding(dp(4),dp(8),dp(4),dp(6));body.setVisibility(android.view.View.GONE);
        card.addView(body);
        header.setOnClickListener(v->{
            boolean show=body.getVisibility()!=android.view.View.VISIBLE;
            body.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);
            header.setText(title+(show?"  ▴":"  ▾"));
        });
    }
    private java.util.Map<String,String>[] statPlayerNames(String lineups){
        java.util.Map<String,String>[] names=new java.util.HashMap[]{
            new java.util.HashMap<String,String>(),new java.util.HashMap<String,String>()};
        if(lineups==null)return names;
        int team=-1;
        java.util.regex.Pattern player=java.util.regex.Pattern.compile(
            "^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*\\d{7}.*$");
        for(String raw:lineups.split("\\r?\\n")){
            String line=raw.trim();
            if(line.equals("EQUIPO LOCAL"))team=0;
            else if(line.equals("EQUIPO VISITANTE"))team=1;
            else if(line.startsWith("JUGADORES SIN EQUIPO"))team=-1;
            else if(team>=0){
                java.util.regex.Matcher m=player.matcher(line);
                if(m.matches())names[team].put(m.group(1),m.group(2).trim());
            }
        }
        return names;
    }
    private String statScorerDetails(MatchNumbers numbers,java.util.Map<String,String> names){
        if(numbers.scorers.isEmpty())return "No hay anotadores identificados.";
        StringBuilder sb=new StringBuilder();
        for(java.util.Map.Entry<String,Integer> e:numbers.scorers.entrySet()){
            if(sb.length()>0)sb.append("\n");
            String name=names.get(e.getKey());
            sb.append(name==null||name.isEmpty()?"Dorsal "+e.getKey():name+" (nº "+e.getKey()+")")
                .append(" · ").append(e.getValue()).append(" puntos");
        }
        return sb.toString();
    }
    private void showActMatchStatistics(android.widget.ScrollView scroll,String scoreboard,
            String discipline,String changes,String lineups,String home,String away){
        scroll.removeAllViews();
        LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(dp(10),dp(7),dp(10),dp(14));
        host.setBackgroundColor(0xff101a24);scroll.addView(host);
        MatchNumbers local=readMatchNumbers(scoreboard,home,false);
        MatchNumbers visitor=readMatchNumbers(scoreboard,away,true);
        local.changes=countActItems(changes,false);visitor.changes=countActItems(changes,true);
        java.util.Map<String,String>[] names=statPlayerNames(lineups);
        TextView teams=text((home==null?"Local":home)+"   ·   "+(away==null?"Visitante":away),14,true);
        teams.setGravity(Gravity.CENTER);teams.setTextColor(0xffd7e7f0);host.addView(teams);
        TextView result=text(local.points+"  —  "+visitor.points,34,true);
        result.setGravity(Gravity.CENTER);result.setTextColor(0xff61dda5);
        result.setPadding(0,dp(4),0,dp(3));host.addView(result);
        TextView caveat=text("Puntos identificados en anotaciones del acta; consulta el marcador oficial para el resultado definitivo.",11,false);
        caveat.setGravity(Gravity.CENTER);caveat.setTextColor(0xffa8bbc9);host.addView(caveat);
        addStatTitle(host,"Comparativa de equipos");
        statCard(host).addView(new CompactCompare3D(local,visitor));
        java.util.ArrayList<int[]> timeline=new java.util.ArrayList<>();
        for(int[] e:local.events)timeline.add(new int[]{e[0],e[1],0});
        for(int[] e:visitor.events)timeline.add(new int[]{e[0],e[1],1});
        java.util.Collections.sort(timeline,(x,y)->Integer.compare(x[0],y[0]));
        addStatTitle(host,"Evolución del marcador");
        addStatTimeline(host,timeline);
        addStatTitle(host,"Distribución de anotaciones");
        addStatTypeSelector(host,local,visitor,home,away);
        addStatTitle(host,"Anotadores");
        addStatScorersCompact(host,home==null?"Local":home,local,names[0]);
        addStatScorersCompact(host,away==null?"Visitante":away,visitor,names[1]);
        StringBuilder chronology=new StringBuilder();int lp=0,vp=0;
        for(int[] e:timeline){
            if(e[2]==0)lp+=e[1];else vp+=e[1];
            chronology.append("Min ").append(e[0]).append(" · ")
                .append(e[2]==0?home:away).append(" +").append(e[1])
                .append("  →  ").append(lp).append("–").append(vp).append("\n");
        }
        addStatExpandable(host,"Cronología completa",chronology.toString());
        addStatExpandable(host,"Sustituciones",changes);
        addStatExpandable(host,"Tarjetas y expulsiones",discipline);
        TextView note=text("Solo se muestran datos identificados en el acta. Las estadísticas no registradas no se consideran cero.",11,false);
        note.setTextColor(0xffa8bbc9);note.setPadding(0,dp(8),0,0);host.addView(note);
    }
    private void addStatScorerLegend(LinearLayout host,
            java.util.ArrayList<java.util.Map.Entry<String,Integer>> ordered,
            java.util.Map<String,String> names){
        int total=0;for(java.util.Map.Entry<String,Integer> e:ordered)total+=e.getValue();
        int[] palette={0xff46dca0,0xff52a9f7,0xfff7b84b,0xffd47aff,
            0xffff7c8c,0xff66d7e9,0xffe8dd78,0xff9cc783};
        for(int i=0;i<ordered.size();i++){
            java.util.Map.Entry<String,Integer> e=ordered.get(i);
            String name=names.get(e.getKey());
            String label=name==null||name.trim().isEmpty()?"Dorsal "+e.getKey():name;
            int percent=total<=0?0:Math.round(e.getValue()*100f/total);
            TextView item=text("●  "+label+" (nº "+e.getKey()+") · "+e.getValue()+
                " puntos ("+percent+"%)",12,false);
            item.setTextColor(palette[i%palette.length]);
            item.setPadding(dp(7),dp(3),dp(4),dp(3));host.addView(item);
        }
    }
    private void addStatScorersCompact(LinearLayout host,String team,MatchNumbers numbers,
            java.util.Map<String,String> names){
        LinearLayout card=statCard(host);
        TextView title=text(team,13,true);title.setTextColor(0xffe6f1f8);card.addView(title);
        if(numbers.scorers.isEmpty()){card.addView(text("Sin anotadores identificados",12,false));return;}
        java.util.ArrayList<java.util.Map.Entry<String,Integer>> ordered=
            new java.util.ArrayList<>(numbers.scorers.entrySet());
        java.util.Collections.sort(ordered,(a,b)->Integer.compare(b.getValue(),a.getValue()));
        int count=ordered.size();int[] values=new int[count];String[] labels=new String[count];
        for(int i=0;i<count;i++){
            java.util.Map.Entry<String,Integer> e=ordered.get(i);
            values[i]=e.getValue();labels[i]="Nº "+e.getKey();
        }
        card.addView(new ThreeDChart(values,labels,true));
        addStatScorerLegend(card,ordered,names);


    }
    private String formatScorers(MatchNumbers numbers){
        if(numbers.scorers.isEmpty())return "No se han identificado anotadores.";
        StringBuilder sb=new StringBuilder();
        for(java.util.Map.Entry<String,Integer> e:numbers.scorers.entrySet()){
            if(sb.length()>0)sb.append("\n");
            sb.append("Dorsal ").append(e.getKey()).append(" · ").append(e.getValue()).append(" puntos");
        }
        return sb.toString();
    }
    private void addActStatBlock(LinearLayout host,String heading,String value){
        TextView title=text(heading,13,true);
        title.setTextColor(0xff66e1a4);
        title.setPadding(dp(10),dp(8),dp(10),dp(5));
        host.addView(title);
        String detail=value==null||value.trim().isEmpty()?
            "No hay datos estructurados disponibles en esta acta.":value.trim();
        TextView content=text(detail,12,false);
        content.setTextIsSelectable(true);
        content.setPadding(dp(10),dp(8),dp(10),dp(10));
        content.setBackgroundColor(0xff1b2b3b);
        host.addView(content);
    }
    // Prototype: inspect PDF glyph coordinates without altering the production parser.
    // A text stream cannot represent empty cells; coordinates can reveal their gaps.
    private String diagnoseScoreCoordinates(com.tom_roush.pdfbox.pdmodel.PDDocument pdf){
        StringBuilder report=new StringBuilder();
        for(int pageIndex=0;pageIndex<pdf.getNumberOfPages();pageIndex++){
            try{
                final java.util.ArrayList<String> tokens=new java.util.ArrayList<>();
                com.tom_roush.pdfbox.text.PDFTextStripper positional=
                    new com.tom_roush.pdfbox.text.PDFTextStripper(){
                        @Override protected void writeString(String str,
                            java.util.List<com.tom_roush.pdfbox.text.TextPosition> positions)
                            throws java.io.IOException{
                            if(positions==null||positions.isEmpty()||str.trim().isEmpty())return;
                            com.tom_roush.pdfbox.text.TextPosition first=positions.get(0);
                            float x=first.getXDirAdj(),y=first.getYDirAdj();
                            float last=positions.get(positions.size()-1).getXDirAdj()+
                                positions.get(positions.size()-1).getWidthDirAdj();
                            tokens.add(String.format(java.util.Locale.ROOT,
                                "x=%.1f..%.1f y=%.1f %s",x,last,y,str.trim()));
                        }
                    };
                positional.setSortByPosition(true);
                positional.setStartPage(pageIndex+1);
                positional.setEndPage(pageIndex+1);
                positional.getText(pdf);
                int heading=-1;
                for(int i=0;i<tokens.size();i++)
                    if(tokens.get(i).matches("(?i).*\\bMarcador\\s*$")){heading=i;break;}
                if(heading<0)continue;
                report.append("PÁGINA ").append(pageIndex+1)
                    .append(" — POSICIONES DEL MARCADOR\n");
                int lines=0;
                for(int i=heading;i<tokens.size()&&lines<130;i++){
                    String t=tokens.get(i);
                    if(i>heading&&t.matches("(?i).*\\bCambios\\s*$"))break;
                    report.append(t).append('\n');lines++;
                }
            }catch(Exception ex){
                report.append("No se pudieron obtener coordenadas: ")
                    .append(ex.getClass().getSimpleName()).append('\n');
            }
        }
        return report.length()==0?"No se encontró la zona Marcador":report.toString();
    }

    // Coordinate-based score reader: associate each cell with its X column.
    // Missing dorsals remain blank instead of shifting subsequent entries.
    private String scoreFromCoordinates(String diagnosis){
        if(diagnosis==null||!diagnosis.contains("POSICIONES DEL MARCADOR"))return null;
        java.util.regex.Pattern glyph=java.util.regex.Pattern.compile(
            "^x=([0-9.]+)\\.\\.([0-9.]+) y=([0-9.]+) (.+)$");
        java.util.ArrayList<float[]> coords=new java.util.ArrayList<>();
        java.util.ArrayList<String> values=new java.util.ArrayList<>();
        for(String line:diagnosis.split("\\n")){
            java.util.regex.Matcher m=glyph.matcher(line.trim());
            if(!m.matches())continue;
            try{
                coords.add(new float[]{(Float.parseFloat(m.group(1))+Float.parseFloat(m.group(2)))/2f,
                    Float.parseFloat(m.group(3))});
                values.add(m.group(4).trim());
            }catch(NumberFormatException ignored){}
        }
        StringBuilder output=new StringBuilder();
        int teams=0;
        for(int i=0;i<values.size();i++){
            String name=values.get(i);
            if(!name.matches("(?i)^Equipo (local|visitante):.*"))continue;
            int end=i+1;
            while(end<values.size()&&!values.get(end).matches("(?i)^Equipo (local|visitante):.*"))end++;
            float typeY=-1,dorsalY=-1,minuteY=-1;
            for(int j=i+1;j<end;j++){
                String label=values.get(j);
                if(label.equalsIgnoreCase("Tipo *"))typeY=coords.get(j)[1];
                else if(label.equalsIgnoreCase("Dorsal"))dorsalY=coords.get(j)[1];
                else if(label.equalsIgnoreCase("Minuto"))minuteY=coords.get(j)[1];
            }
            if(typeY<0||minuteY<0||dorsalY<0)continue;
            java.util.ArrayList<Integer> types=new java.util.ArrayList<>();
            for(int j=i+1;j<end;j++){
                String v=values.get(j);
                if(Math.abs(coords.get(j)[1]-typeY)<2f&&coords.get(j)[0]>65f&&
                    v.matches("(?i)E|T|EC|D|PC"))types.add(j);
            }
            if(types.isEmpty())continue;
            StringBuilder block=new StringBuilder(name).append("\n");
            boolean complete=true;
            for(int n=0;n<types.size();n++){
                int ti=types.get(n);
                float x=coords.get(ti)[0];
                float tolerance=12.5f;
                String minute="",dorsal="";
                for(int j=i+1;j<end;j++){
                    float[] pos=coords.get(j);
                    if(Math.abs(pos[0]-x)>tolerance)continue;
                    if(Math.abs(pos[1]-minuteY)<2f&&values.get(j).matches("\\d+"))
                        minute=values.get(j);
                    if(Math.abs(pos[1]-dorsalY)<2f&&values.get(j).matches("\\d+"))
                        dorsal=values.get(j);
                }
                if(minute.isEmpty()){complete=false;break;}
                block.append(n+1).append(". Min ").append(minute).append(" · ")
                    .append(eventDescription(values.get(ti),"Marcador")).append(" · ")
                    .append(dorsal.isEmpty()?"Dorsal no indicado":"Dorsal "+dorsal)
                    .append("\n");
            }
            if(!complete)continue;
            output.append(block).append("\n");teams++;
            i=end-1;
        }
        return teams==2?output.toString().trim():null;
    }

    // Reusable position-aware event extraction for substitutions and sanctions.
    // The row label determines Y; event columns are anchored by the Tipo values.
    private String positionEventTable(com.tom_roush.pdfbox.pdmodel.PDDocument pdf,
        String heading,boolean changes){
        StringBuilder output=new StringBuilder();
        for(int pageIndex=0;pageIndex<pdf.getNumberOfPages();pageIndex++){
            try{
                java.util.ArrayList<float[]> positions=new java.util.ArrayList<>();
                java.util.ArrayList<String> strings=new java.util.ArrayList<>();
                com.tom_roush.pdfbox.text.PDFTextStripper stripper=
                    new com.tom_roush.pdfbox.text.PDFTextStripper(){
                        @Override protected void writeString(String text,
                            java.util.List<com.tom_roush.pdfbox.text.TextPosition> glyphs)
                            throws java.io.IOException{
                            if(glyphs==null||glyphs.isEmpty()||text.trim().isEmpty())return;
                            com.tom_roush.pdfbox.text.TextPosition first=glyphs.get(0);
                            float x=first.getXDirAdj(),y=first.getYDirAdj();
                            float end=glyphs.get(glyphs.size()-1).getXDirAdj()+
                                glyphs.get(glyphs.size()-1).getWidthDirAdj();
                            positions.add(new float[]{(x+end)/2f,y,x});
                            strings.add(text.trim());
                        }
                    };
                stripper.setSortByPosition(true);
                stripper.setStartPage(pageIndex+1);stripper.setEndPage(pageIndex+1);
                stripper.getText(pdf);
                float headingY=-1;
                float headingX=-1;
                for(int i=0;i<strings.size();i++)
                    if(strings.get(i).equalsIgnoreCase(heading)){
                        headingY=positions.get(i)[1];headingX=positions.get(i)[0];break;
                    }
                if(headingY<0)continue;
                final float middle=pdf.getPage(pageIndex).getCropBox().getWidth()/2f;
                final boolean leftColumn=headingX<middle;
                float nextY=Float.MAX_VALUE;
                for(int i=0;i<strings.size();i++){
                    float y=positions.get(i)[1];
                    if(y>headingY+3&&
                        (strings.get(i).equalsIgnoreCase("Cambios")||
                         strings.get(i).equalsIgnoreCase("Expulsiones temporales")||
                         strings.get(i).equalsIgnoreCase("Árbitro"))){
                        nextY=Math.min(nextY,y);
                    }
                }
                StringBuilder pageResult=new StringBuilder();
                int validTeams=0;
                for(int side=0;side<2;side++){
                    int teamIndex=-1;
                    for(int i=0;i<strings.size();i++){
                        float y=positions.get(i)[1];
                        if(y>headingY&&y<nextY&&
                            (changes||(leftColumn?positions.get(i)[0]<middle:positions.get(i)[0]>=middle))&&
                            strings.get(i).matches(
                            side==0?"(?i)Equipo local:.*":"(?i)Equipo visitante:.*")){
                            teamIndex=i;break;
                        }
                    }
                    if(teamIndex<0)continue;
                    float start=positions.get(teamIndex)[1];
                    float end=nextY;
                    for(int i=teamIndex+1;i<strings.size();i++){
                        float y=positions.get(i)[1];
                        if(y>start+3&&y<end&&
                            (changes||(leftColumn?positions.get(i)[0]<middle:positions.get(i)[0]>=middle))&&
                            strings.get(i).matches("(?i)Equipo (local|visitante):.*")){
                            end=y;break;
                        }
                    }
                    java.util.LinkedHashMap<String,Float> rowY=new java.util.LinkedHashMap<>();
                    for(int i=teamIndex+1;i<strings.size();i++){
                        float y=positions.get(i)[1];
                        if(y<=start||y>=end||(!changes&&
                            (leftColumn?positions.get(i)[0]>=middle:positions.get(i)[0]<middle)))continue;
                        String v=strings.get(i).toLowerCase(java.util.Locale.ROOT);
                        if(v.equals("tipo *"))rowY.put("tipo",y);
                        else if(v.equals("minuto"))rowY.put("minuto",y);
                        else if(v.equals("dorsal"))rowY.put("dorsal",y);
                        else if(v.equals("dorsal entra"))rowY.put("entra",y);
                        else if(v.equals("dorsal sale"))rowY.put("sale",y);
                    }
                    if(!rowY.containsKey("tipo")||!rowY.containsKey("minuto"))continue;
                    java.util.ArrayList<Integer> eventIndices=new java.util.ArrayList<>();
                    for(int i=teamIndex+1;i<strings.size();i++){
                        float[] pos=positions.get(i);
                        if(pos[1]<=start||pos[1]>=end||
                            (changes?pos[0]<65f:(leftColumn?pos[0]<65f||pos[0]>=middle:pos[0]<middle)))continue;
                        if(Math.abs(pos[1]-rowY.get("tipo"))<2f&&strings.get(i).matches(
                            changes?"(?i)CT|CL|CS|CC|C1|CJ|C20":"(?i)[A-E]"))
                            eventIndices.add(i);
                    }
                    StringBuilder team=new StringBuilder(strings.get(teamIndex)).append("\n");
                    for(int k=0;k<eventIndices.size();k++){
                        int event=eventIndices.get(k);
                        float x=positions.get(event)[0];
                        String minute=positionCell(strings,positions,rowY.get("minuto"),x,
                            changes?0f:(leftColumn?0f:middle),changes?Float.MAX_VALUE:(leftColumn?middle:Float.MAX_VALUE));
                        if(!minute.matches("\\d+"))continue;
                        team.append(k+1).append(". Min ").append(minute).append(" · ");
                        if(changes){
                            String enter=rowY.containsKey("entra")?
                                positionCell(strings,positions,rowY.get("entra"),x,0f,Float.MAX_VALUE):"";
                            String leave=rowY.containsKey("sale")?
                                positionCell(strings,positions,rowY.get("sale"),x,0f,Float.MAX_VALUE):"";
                            team.append("Cambio · ")
                                .append(enter.matches("\\d+")?"Entra nº "+enter:"Entra sin dorsal")
                                .append(" · ")
                                .append(leave.matches("\\d+")?"Sale nº "+leave:"Sale sin dorsal");
                        }else{
                            String dorsal=rowY.containsKey("dorsal")?
                                positionCell(strings,positions,rowY.get("dorsal"),x,leftColumn?0f:middle,leftColumn?middle:Float.MAX_VALUE):"";
                            team.append("Tipo ").append(strings.get(event)).append(" · ")
                                .append(dorsal.matches("\\d+")?"Dorsal "+dorsal:"Dorsal no indicado");
                        }
                        team.append("\n");
                    }
                    pageResult.append(team).append("\n");validTeams++;
                }
                if(validTeams==2){
                    output.append(pageResult);
                    break;
                }
            }catch(Exception ignored){}
        }
        return output.length()==0?null:output.toString().trim();
    }
    private String positionCell(java.util.List<String> strings,
        java.util.List<float[]> positions,float rowY,float x,float minX,float maxX){
        float best=12.5f;
        String value="";
        for(int i=0;i<strings.size();i++){
            float[] p=positions.get(i);
            float delta=Math.abs(p[0]-x);
            if(p[0]>=minX&&p[0]<maxX&&Math.abs(p[1]-rowY)<2f&&delta<best&&strings.get(i).matches("\\d+")){
                best=delta;value=strings.get(i);
            }
        }
        return value;
    }
    // Extract scoring rows from the score-table region, separately from page text.
    private String extractScoreboardColumns(com.tom_roush.pdfbox.pdmodel.PDDocument pdf){
        // MatchReady puts local and visitor score tables one below the other.
        // The previous narrow .16-.21 region excluded the visitor's table.
        for(int p=0;p<pdf.getNumberOfPages();p++){
            try{
                com.tom_roush.pdfbox.pdmodel.PDPage page=pdf.getPage(p);
                float w=page.getCropBox().getWidth(),h=page.getCropBox().getHeight();
                com.tom_roush.pdfbox.text.PDFTextStripperByArea area=
                    new com.tom_roush.pdfbox.text.PDFTextStripperByArea();
                area.setSortByPosition(true);
                area.addRegion("score",new android.graphics.RectF(0,h*.13f,w,h*.47f));
                area.extractRegions(page);
                String extracted=area.getTextForRegion("score");
                if(extracted==null)continue;
                java.util.regex.Matcher heading=java.util.regex.Pattern.compile(
                    "(?im)^\\s*Marcador\\s*$").matcher(extracted);
                if(!heading.find())continue;
                String score=extracted.substring(heading.end());
                java.util.regex.Matcher next=java.util.regex.Pattern.compile(
                    "(?im)^\\s*(?:Cambios|Expulsiones\\s+temporales|Expulsiones\\s+definitivas)\\s*$").matcher(score);
                if(next.find())score=score.substring(0,next.start());
                String parsed=parseActEventSection(score,"Marcador");
                // Never replace the normal extraction with an incomplete single-team result.
                if(parsed.contains("Equipo local:")&&parsed.contains("Equipo visitante:")&&
                   parsed.contains("puntos"))return parsed;
            }catch(Exception ignored){}
        }
        return null;
    }
    private String extractDisciplinaryColumns(com.tom_roush.pdfbox.pdmodel.PDDocument pdf){
        StringBuilder out=new StringBuilder();
        try{
            for(int pageIndex=0;pageIndex<pdf.getNumberOfPages();pageIndex++){
                com.tom_roush.pdfbox.pdmodel.PDPage page=pdf.getPage(pageIndex);
                float w=page.getCropBox().getWidth(),h=page.getCropBox().getHeight();
                com.tom_roush.pdfbox.text.PDFTextStripperByArea area=
                    new com.tom_roush.pdfbox.text.PDFTextStripperByArea();
                area.setSortByPosition(true);
                area.addRegion("temporal",new android.graphics.RectF(0,0,w*.495f,h));
                area.addRegion("definitiva",new android.graphics.RectF(w*.505f,0,w,h));
                area.extractRegions(page);
                String left=area.getTextForRegion("temporal");
                String right=area.getTextForRegion("definitiva");
                if(left==null||right==null)continue;
                if(!left.toLowerCase(java.util.Locale.ROOT).contains("expulsiones temporales")||
                   !right.toLowerCase(java.util.Locale.ROOT).contains("expulsiones definitivas"))continue;
                String temporary=parseDisciplineTableOnly(left,"Expulsiones temporales");
                String definitive=parseDisciplineTableOnly(right,"Expulsiones definitivas");
                if(temporary.isEmpty()&&definitive.isEmpty())continue;
                out.append("TEMPORALES\n").append(temporary).append("\nDEFINITIVAS\n")
                    .append(definitive).append("\n");
            }
        }catch(Exception ignored){return null;}
        return out.length()==0?null:out.toString();
    }
    private String parseDisciplineTableOnly(String column,String heading){
        if(column==null)return "";
        String lower=column.toLowerCase(java.util.Locale.ROOT);
        int start=lower.indexOf(heading.toLowerCase(java.util.Locale.ROOT));
        if(start<0)return "";
        String section=column.substring(start+heading.length());
        // Only the table immediately below this heading is eligible.
        // Stop before its code legend, or the next section on the page.
        java.util.regex.Matcher stop=java.util.regex.Pattern.compile(
            "(?im)^\\s*(?:\\(\\*\\)|\\*\\)|leyenda|marcador|cambios|alineaciones|expulsiones\\s+(?:temporales|definitivas))\\b")
            .matcher(section);
        if(stop.find())section=section.substring(0,stop.start());
        StringBuilder result=new StringBuilder();
        java.util.regex.Matcher teams=java.util.regex.Pattern.compile(
            "(?im)^\\s*Equipo\\s+(local|visitante):[^\\r\\n]*").matcher(section);
        java.util.ArrayList<Integer> offsets=new java.util.ArrayList<>();
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        while(teams.find()){offsets.add(teams.start());labels.add(teams.group().trim());}
        for(int k=0;k<offsets.size();k++){
            String block=section.substring(offsets.get(k),k+1<offsets.size()?offsets.get(k+1):section.length());
            java.util.regex.Matcher type=java.util.regex.Pattern.compile("(?im)^\\s*Tipo\\s*\\*?\\s*(.*)$").matcher(block);
            java.util.regex.Matcher dorsal=java.util.regex.Pattern.compile("(?im)^\\s*Dorsal\\s+(.*)$").matcher(block);
            java.util.regex.Matcher minute=java.util.regex.Pattern.compile("(?im)^\\s*Minuto\\s+(.*)$").matcher(block);
            if(!type.find()||!dorsal.find()||!minute.find())continue;
            String[] types=type.group(1).trim().split("\\s+");
            String[] dorsals=dorsal.group(1).trim().split("\\s+");
            String[] minutes=minute.group(1).trim().split("\\s+");
            result.append(labels.get(k)).append("\n");
            int count=Math.min(types.length,Math.min(dorsals.length,minutes.length));
            for(int i=0;i<count;i++){
                if(!types[i].matches("(?i)[A-E]")||!dorsals[i].matches("\\d{1,2}")||
                    !minutes[i].matches("\\d{1,3}"))continue;
                result.append(i+1).append(". Min ").append(minutes[i]).append(" · Tipo ")
                    .append(types[i]).append(" · Dorsal ").append(dorsals[i]).append("\n");
            }
            result.append("\n");
        }
        return result.toString();
    }
    private void showActDiscipline(ScrollView scroll,String expulsions,String changes,String lineups,String home,String away){
        LinearLayout host=new LinearLayout(this);host.setOrientation(1);
        host.setPadding(dp(4),dp(8),dp(4),dp(16));
        scroll.removeAllViews();scroll.addView(host);
        java.util.Map<String,String>[] names=new java.util.HashMap[]{
            new java.util.HashMap<String,String>(),new java.util.HashMap<String,String>()
        };
        if(lineups!=null){
            int side=-1;
            java.util.regex.Pattern player=java.util.regex.Pattern.compile(
                "^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*\\d{7}.*$");
            for(String line:lineups.split("\\r?\\n")){
                String t=line.trim();
                if(t.equals("EQUIPO LOCAL"))side=0;
                else if(t.equals("EQUIPO VISITANTE"))side=1;
                else if(side>=0){
                    java.util.regex.Matcher m=player.matcher(t);
                    if(m.matches())names[side].put(m.group(1),m.group(2).trim());
                }
            }
        }
        java.util.ArrayList<String[]> incidents=new java.util.ArrayList<>();
        java.util.regex.Pattern record=java.util.regex.Pattern.compile(
            "^\\d+\\.\\s*Min\\s+(\\d+)\\s*·\\s*Tipo\\s+([A-Za-z]{1,3})(?:\\s*·\\s*Dorsal\\s+(\\d+)|\\s*·\\s*Dorsal no indicado)?\\s*$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        String raw=expulsions==null?"":expulsions;
        int category=-1,side=-1;
        for(String line:raw.split("\\r?\\n")){
            String t=line.trim().toLowerCase(java.util.Locale.ROOT);
            if(t.equals("temporales")){category=0;side=-1;}
            else if(t.equals("definitivas")){category=1;side=-1;}
            else if(t.startsWith("equipo local:"))side=0;
            else if(t.startsWith("equipo visitante:"))side=1;
            else if(category>=0&&side>=0){
                java.util.regex.Matcher m=record.matcher(line.trim());
                if(m.matches())incidents.add(new String[]{String.valueOf(side),m.group(1),m.group(2),m.group(3)==null?"":m.group(3),String.valueOf(category)});
            }
        }
        // Never infer expulsions from substitutions: they are different sections.
        TextView heading=text("EXPULSIONES",16,true);
        host.addView(heading);
        TextView note=text("Temporales y definitivas con los motivos indicados en la leyenda oficial del acta.",12,false);
        note.setTextColor(0xffaab9c9);note.setPadding(0,dp(4),0,dp(12));host.addView(note);
        if(incidents.isEmpty()){
            TextView empty=text("No se han identificado registros disciplinarios verificables en el texto extraído. Consulta el PDF original.",13,false);
            host.addView(empty);
        }
        java.util.Collections.sort(incidents,(a,b)->Integer.compare(Integer.parseInt(a[1]),Integer.parseInt(b[1])));
        for(String[] item:incidents){
            int team=Integer.parseInt(item[0]);
            String teamName=team==0?home:away;
            if(teamName==null||teamName.trim().isEmpty())teamName=team==0?"Equipo local":"Equipo visitante";
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);
            card.setPadding(dp(12),dp(9),dp(12),dp(9));
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xff1b2a39);bg.setCornerRadius(dp(9));card.setBackground(bg);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,dp(4),0,dp(3));host.addView(card,lp);
            int sanctionCategory=Integer.parseInt(item[4]);
            String sanction=sanctionCategory==0?"EXPULSIÓN TEMPORAL":
                sanctionCategory==1?"EXPULSIÓN DEFINITIVA":"INCIDENCIA SIN CLASIFICAR";
            String[] temporalReasons={"Falta reiterada","Falta reiterada de equipo",
                "Actitud contra árbitros","Juego sucio","Otros motivos"};
            String[] definitiveReasons={"Doble expulsión temporal","Agresión al contrario",
                "Actitud contra árbitros","Otros motivos","Expulsión TR 20'"};
            String motive="Código "+item[2]+" · comprobar en el PDF";
            if(item[2].length()==1){
                int index=Character.toUpperCase(item[2].charAt(0))-'A';
                if(index>=0&&index<5){
                    if(sanctionCategory==0)motive=temporalReasons[index];
                    if(sanctionCategory==1)motive=definitiveReasons[index];
                }
            }
            card.addView(text(sanction+"  ·  MINUTO "+item[1]+"'",14,true));
            TextView motiveView=text(motive,13,true);
            motiveView.setTextColor(0xffffb1a1);
            card.addView(motiveView);
            String playerName=names[team].get(item[3]);
            TextView player=text(teamName+"  ·  "+(item[3].isEmpty()?"Dorsal no indicado":"Nº "+item[3])+
                (playerName==null?"":"  ·  "+playerName),12,false);
            player.setTextColor(0xffb8c9d9);card.addView(player);
        }
        TextView original=text("Las sanciones sin sección identificable se dejan sin clasificar para evitar motivos incorrectos.",12,false);
        original.setPadding(0,dp(14),0,0);host.addView(original);
    }
    private void showActChanges(ScrollView scroll,String events,String lineups,String home,String away){
        LinearLayout host=new LinearLayout(this);host.setOrientation(1);
        host.setPadding(dp(4),dp(6),dp(4),dp(14));
        scroll.removeAllViews();scroll.addView(host);
        String[] teams={
            home==null||home.trim().isEmpty()?"Equipo local":home.trim(),
            away==null||away.trim().isEmpty()?"Equipo visitante":away.trim()
        };
        java.util.Map<String,String>[] players=new java.util.HashMap[]{
            new java.util.HashMap<String,String>(),new java.util.HashMap<String,String>()
        };
        if(lineups!=null){
            int team=-1;
            java.util.regex.Pattern player=java.util.regex.Pattern.compile(
                "^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*\\d{7}.*$");
            for(String line:lineups.split("\\r?\\n")){
                String value=line.trim();
                if(value.equals("EQUIPO LOCAL"))team=0;
                else if(value.equals("EQUIPO VISITANTE"))team=1;
                else if(team>=0){
                    java.util.regex.Matcher m=player.matcher(value);
                    if(m.matches())players[team].put(m.group(1),m.group(2).trim());
                }
            }
        }
        java.util.ArrayList<String[]>[] changes=new java.util.ArrayList[]{
            new java.util.ArrayList<String[]>(),new java.util.ArrayList<String[]>()
        };
        int side=-1;
        java.util.regex.Pattern change=java.util.regex.Pattern.compile(
            "^\\d+\\.\\s*Min\\s+(\\d+)\\s*·\\s*Cambio\\s*·\\s*Entra\\s*(?:n[º°o]?\\s*(\\d+)|sin dorsal)\\s*·\\s*Sale\\s*(?:n[º°o]?\\s*(\\d+)|sin dorsal)\\s*$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        for(String line:(events==null?"":events).split("\\r?\\n")){
            String value=line.trim();
            if(value.toLowerCase(java.util.Locale.ROOT).startsWith("equipo local:"))side=0;
            else if(value.toLowerCase(java.util.Locale.ROOT).startsWith("equipo visitante:"))side=1;
            else if(side>=0){
                java.util.regex.Matcher m=change.matcher(value);
                if(m.matches())changes[side].add(new String[]{m.group(1),m.group(2)==null?"":m.group(2),m.group(3)==null?"":m.group(3)});
            }
        }
        for(java.util.ArrayList<String[]> list:changes)
            java.util.Collections.sort(list,(a,b)->Integer.compare(
                Integer.parseInt(a[0]),Integer.parseInt(b[0])));
        LinearLayout tabs=new LinearLayout(this);
        host.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(1);
        host.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        final int[] selected={0};
        Runnable render=new Runnable(){
            @Override public void run(){
                tabs.removeAllViews();cards.removeAllViews();
                for(int i=0;i<2;i++){
                    final int idx=i;
                    Button b=new Button(RugbyHubActivity.this);
                    b.setAllCaps(false);b.setTextSize(11);b.setMaxLines(2);
                    b.setText(teams[i]+" ("+changes[i].size()+")");
                    b.setAlpha(i==selected[0]?1f:.6f);
                    tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));
                    b.setOnClickListener(v->{selected[0]=idx;this.run();
                        scroll.post(()->scroll.scrollTo(0,0));});
                }
                int team=selected[0];
                if(changes[team].isEmpty()){
                    TextView empty=text("No hay sustituciones registradas para este equipo.",13,false);
                    empty.setPadding(dp(8),dp(18),dp(8),dp(10));cards.addView(empty);
                    return;
                }
                for(String[] changeRow:changes[team]){
                    LinearLayout card=new LinearLayout(RugbyHubActivity.this);
                    card.setOrientation(1);card.setPadding(dp(12),dp(9),dp(12),dp(9));
                    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
                    bg.setColor(0xff1b2a39);bg.setCornerRadius(dp(10));
                    card.setBackground(bg);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                    lp.setMargins(0,dp(5),0,dp(3));cards.addView(card,lp);
                    card.addView(text("MINUTO "+changeRow[0]+"'",12,true));
                    String outName=players[team].get(changeRow[2]);
                    TextView outgoing=text("↓  SALE · "+(changeRow[2].isEmpty()?"Dorsal no indicado":"Nº "+changeRow[2])+"  "+
                        (outName==null?"Nombre no identificado":outName),13,true);
                    outgoing.setTextColor(0xffffa0a0);
                    outgoing.setPadding(0,dp(6),0,dp(3));card.addView(outgoing);
                    String inName=players[team].get(changeRow[1]);
                    TextView incoming=text("↑  ENTRA · "+(changeRow[1].isEmpty()?"Dorsal no indicado":"Nº "+changeRow[1])+"  "+
                        (inName==null?"Nombre no identificado":inName),13,true);
                    incoming.setTextColor(0xff93e0b2);card.addView(incoming);
                }
            }
        };
        render.run();
    }
    private void showActScoreboard(ScrollView scroll,String events,String lineups,String home,String away){
        LinearLayout host=new LinearLayout(this);host.setOrientation(1);
        host.setPadding(dp(4),dp(7),dp(4),dp(18));
        scroll.removeAllViews();scroll.addView(host);
        String[] names={
            home==null||home.trim().isEmpty()?"Equipo local":home.trim(),
            away==null||away.trim().isEmpty()?"Equipo visitante":away.trim()
        };
        java.util.Map<String,String>[] players=new java.util.HashMap[]{
            new java.util.HashMap<String,String>(),new java.util.HashMap<String,String>()
        };
        if(lineups!=null){
            int side=-1;
            java.util.regex.Pattern player=java.util.regex.Pattern.compile(
                "^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*\\d{7}.*$");
            for(String line:lineups.split("\\r?\\n")){
                String t=line.trim();
                if(t.equals("EQUIPO LOCAL"))side=0;
                else if(t.equals("EQUIPO VISITANTE"))side=1;
                else if(side>=0){
                    java.util.regex.Matcher pm=player.matcher(t);
                    if(pm.matches())players[side].put(pm.group(1),pm.group(2).trim());
                }
            }
        }
        java.util.ArrayList<String[]> items=new java.util.ArrayList<>();
        int[] score={0,0};
        int side=-1;
        java.util.regex.Pattern row=java.util.regex.Pattern.compile(
            "^\\d+\\.\\s*Min\\s+(\\d+)\\s*·\\s*(.*?)\\s*·\\s*Dorsal\\s+(\\d+|no indicado)\\s*$");
        for(String line:(events==null?"":events).split("\\r?\\n")){
            String t=line.trim();
            if(t.toLowerCase(java.util.Locale.ROOT).startsWith("equipo local:"))side=0;
            else if(t.toLowerCase(java.util.Locale.ROOT).startsWith("equipo visitante:"))side=1;
            else if(side>=0){
                java.util.regex.Matcher m=row.matcher(t);
                if(!m.matches())continue;
                String type=m.group(2).trim();
                java.util.regex.Matcher points=java.util.regex.Pattern.compile(
                    "\\((\\d+)\\s+puntos?\\)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(type);
                if(!points.find())continue;
                int value=Integer.parseInt(points.group(1));
                score[side]+=value;
                String dorsal=m.group(3)==null||"no indicado".equalsIgnoreCase(m.group(3))?"":m.group(3);
                String playerName=dorsal.isEmpty()?"":players[side].get(dorsal);
                items.add(new String[]{m.group(1),type.replaceAll("\\s*\\(\\d+\\s+puntos?\\)",""),
                    dorsal,playerName==null?"":playerName,String.valueOf(value),String.valueOf(side)});
            }
        }
        LinearLayout header=new LinearLayout(this);header.setOrientation(1);
        header.setPadding(dp(10),dp(12),dp(10),dp(12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xff1b2a39);bg.setCornerRadius(dp(12));header.setBackground(bg);
        host.addView(header);
        TextView teams=text(names[0]+"  ·  "+names[1],13,true);
        teams.setGravity(Gravity.CENTER);header.addView(teams);
        String official=getIntent().getStringExtra("far_official_score");
        java.util.regex.Matcher officialMatch=java.util.regex.Pattern.compile(
            "(\\d+)\\s*[-–:]\\s*(\\d+)").matcher(official==null?"":official);
        boolean hasOfficial=officialMatch.find();
        String officialValue=hasOfficial?officialMatch.group(1)+"  –  "+officialMatch.group(2):"";
        boolean mismatch=hasOfficial && (
            score[0]!=Integer.parseInt(officialMatch.group(1)) ||
            score[1]!=Integer.parseInt(officialMatch.group(2)));
        TextView result=text(hasOfficial?officialValue:score[0]+"  –  "+score[1],30,true);
        result.setGravity(Gravity.CENTER);header.addView(result);
        TextView qualifier=text(hasOfficial?"Resultado oficial del partido":
            "Anotaciones identificadas en el acta (resultado no verificado)",10,false);
        qualifier.setGravity(Gravity.CENTER);qualifier.setTextColor(0xffaab9c9);
        header.addView(qualifier);
        if(mismatch){
            TextView warning=text("Anotaciones detectadas: "+score[0]+" – "+score[1]+
                ". El desglose es incompleto y no coincide con el resultado oficial.",11,false);
            warning.setTextColor(0xffffcf85);
            warning.setPadding(dp(4),dp(8),dp(4),0);
            header.addView(warning);
        }
        if(!hasOfficial){
            TextView warning=text("No se ha podido verificar el resultado oficial. El total extraído del PDF puede ser incompleto.",11,false);
            warning.setTextColor(0xffffcf85);
            warning.setPadding(dp(4),dp(8),dp(4),0);
            header.addView(warning);
        }
        if(items.isEmpty()){
            TextView empty=text("No se han identificado anotaciones suficientes. Consulta el PDF original.",13,false);
            empty.setPadding(dp(6),dp(16),dp(6),dp(8));host.addView(empty);
            return;
        }
        java.util.Collections.sort(items,(a,b)->Integer.compare(
            Integer.parseInt(a[0]),Integer.parseInt(b[0])));
        int[] running={0,0};
        for(String[] item:items){
            running[Integer.parseInt(item[5])]+=Integer.parseInt(item[4]);
            String accumulated=running[0]+" – "+running[1];
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);
            card.setPadding(dp(12),dp(7),dp(12),dp(7));
            android.graphics.drawable.GradientDrawable cardBg=new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(0xff1b2a39);cardBg.setCornerRadius(dp(9));
            card.setBackground(cardBg);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,dp(4),0,dp(2));host.addView(card,lp);
            card.addView(text(item[0]+"'  ·  "+item[1]+"  ·  +"+item[4],14,true));
            String who=item[2].isEmpty()?"Sin dorsal (ensayo de castigo)":item[3].isEmpty()?"Dorsal "+item[2]:
                item[3]+"  ·  Nº "+item[2];
            TextView detail=text(names[Integer.parseInt(item[5])]+"  ·  "+who,11,false);
            detail.setTextColor(0xffb8c9d9);card.addView(detail);
            TextView partial=text((mismatch||!hasOfficial?"Anotaciones identificadas hasta aquí: ":"Marcador acumulado: ")+accumulated,11,true);
            partial.setTextColor(0xff82c7ff);card.addView(partial);
        }
    }
    private void showActSummary(ScrollView scroll,String raw,String home,String away){
        LinearLayout host=new LinearLayout(this);host.setOrientation(1);
        host.setPadding(dp(4),dp(6),dp(4),dp(10));
        scroll.removeAllViews();scroll.addView(host);
        TextView matchup=text((home==null||home.isEmpty()?"Equipo local":home)+
            "   VS   "+(away==null||away.isEmpty()?"Equipo visitante":away),18,true);
        matchup.setGravity(Gravity.CENTER);
        matchup.setPadding(dp(8),dp(10),dp(8),dp(10));
        android.graphics.drawable.GradientDrawable header=new android.graphics.drawable.GradientDrawable();
        header.setColor(0xff1b2a39);header.setCornerRadius(dp(12));
        matchup.setBackground(header);host.addView(matchup);
        String clean=raw==null?"":raw.replaceAll("(?im)^\\s*PÁGINA\\s+\\d+\\s*$","").trim();
        String headerOnly=clean.split("(?im)^\\s*Alineaciones\\s*$",2)[0];
        java.util.regex.Pattern fields=java.util.regex.Pattern.compile(
            "(?i)(Celebrado en:|Categoría:|Jornada:|Organizador:|El día:|A la hora:|En el campo:|Equipo local:|Equipo visitante:)");
        java.util.regex.Matcher matcher=fields.matcher(headerOnly);
        java.util.ArrayList<String> keys=new java.util.ArrayList<>();
        java.util.ArrayList<Integer> starts=new java.util.ArrayList<>();
        java.util.ArrayList<Integer> ends=new java.util.ArrayList<>();
        while(matcher.find()){keys.add(matcher.group(1));starts.add(matcher.start());ends.add(matcher.end());}
        java.util.LinkedHashMap<String,String> found=new java.util.LinkedHashMap<>();
        for(int i=0;i<keys.size();i++){
            String key=keys.get(i).toLowerCase(java.util.Locale.ROOT);
            String value=headerOnly.substring(ends.get(i),i+1<keys.size()?starts.get(i+1):headerOnly.length()).trim();
            if(value.isEmpty())continue;
            if(key.startsWith("celebrado"))found.put("Localidad",value);
            else if(key.startsWith("categor"))found.put("Competición",value);
            else if(key.startsWith("jornada"))found.put("Jornada",value);
            else if(key.startsWith("organizador"))found.put("Organizador",value);
            else if(key.startsWith("el d"))found.put("Fecha",value);
            else if(key.startsWith("a la"))found.put("Hora",value);
            else if(key.startsWith("en el"))found.put("Campo",value);
        }
        // In federation PDFs "Jornada 1.1.- 1ª" is embedded inside Categoría,
        // while the later "Jornada: Fase." is a separate label.
        String competition=found.get("Competición");
        if(competition!=null){
            java.util.regex.Matcher embedded=java.util.regex.Pattern.compile(
                "(?i)\\bJornada\\s+([0-9][^\\r\\n]*)").matcher(competition);
            if(embedded.find()){
                String number=embedded.group(1).trim();
                String category=competition.substring(0,embedded.start()).trim();
                if(!category.isEmpty())found.put("Competición",category);
                if(!number.isEmpty()){
                    String phase=found.get("Jornada");
                    if(phase!=null && phase.matches("(?i)fase\\.?"))phase="";
                    found.put("Jornada",number+(phase==null||phase.isEmpty()?"":" · "+phase));
                }
            }else{
                String phase=found.get("Jornada");
                if(phase!=null && phase.matches("(?i)fase\\.?"))
                    found.remove("Jornada");
            }
        }
        // Staff is printed in two side-by-side cells, not among the match
        // header fields. Parse the first page only to avoid annex duplicates.
        String pageOne=(raw==null?"":raw).split("(?m)^\\s*PÁGINA\\s+2\\s*$",2)[0];
        java.util.regex.Pattern staff=java.util.regex.Pattern.compile(
            "(?im)^\\s*(Entrenador|Delegado|Juez línea):\\s*(.+?)\\s+"+
            "\\1:\\s*(.+?)\\s*$");
        java.util.regex.Matcher staffRows=staff.matcher(pageOne);
        while(staffRows.find()){
            String role=staffRows.group(1);
            String local=staffRows.group(2).trim();
            String visitor=staffRows.group(3).trim();
            if(!local.isEmpty())found.put(role+" local",local);
            if(!visitor.isEmpty())found.put(role+" visitante",visitor);
            // The next printed line contains each team's licence in the
            // same left-to-right order as the names above.
            String tail=pageOne.substring(staffRows.end());
            java.util.regex.Matcher licences=java.util.regex.Pattern.compile(
                "^\\s*\\r?\\n\\s*N[ºo]\\s*licencia:\\s*(\\d{7})"+
                "\\s+N[ºo]\\s*licencia:\\s*(\\d{7})",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tail);
            if(licences.find()){
                found.put("Licencia "+role+" local",licences.group(1));
                found.put("Licencia "+role+" visitante",licences.group(2));
            }
        }
        // Some PDFs wrap each team's staff onto separate lines.
        for(String role:new String[]{"Entrenador","Delegado","Juez línea"}){
            if(found.containsKey(role+" local"))continue;
            java.util.regex.Matcher row=java.util.regex.Pattern.compile(
                "(?im)^\\s*"+role+":\\s*([^\\r\\n]+)").matcher(pageOne);
            if(row.find()){
                String[] both=row.group(1).split("(?i)\\s+"+role+":\\s*",2);
                if(both.length==2){
                    found.put(role+" local",both[0].trim());
                    found.put(role+" visitante",both[1].trim());
                }
            }
        }
        // The two line judges and their licence numbers share the same rows.
        java.util.regex.Matcher judges=java.util.regex.Pattern.compile(
            "(?im)^\\s*Juez línea:\\s*(.*?)\\s+Juez línea:\\s*(.*?)\\s*$"+
            "(?:\\r?\\n\\s*N[ºo]\\s*licencia:\\s*(\\d{7})\\s+"+
            "N[ºo]\\s*licencia:\\s*(\\d{7}))?")
            .matcher(pageOne);
        if(judges.find()){
            found.remove("Juez línea local");found.remove("Juez línea visitante");
            String local=judges.group(1).trim(),visitor=judges.group(2).trim();
            if(!local.isEmpty())found.put("Juez de línea local",local);
            if(!visitor.isEmpty())found.put("Juez de línea visitante",visitor);
            if(judges.group(3)!=null)found.put("Licencia juez local",judges.group(3));
            if(judges.group(4)!=null)found.put("Licencia juez visitante",judges.group(4));
        }else{
            for(String role:new String[]{"Juez línea"}){
                if(found.containsKey(role+" local"))
                    found.put("Juez de línea local",found.remove(role+" local"));
                if(found.containsKey(role+" visitante"))
                    found.put("Juez de línea visitante",found.remove(role+" visitante"));
            }
        }
        // The referee appears in a three-column row below the final score.
        // Only display a name when an adjacent seven-digit licence confirms it.
        java.util.regex.Matcher referee=java.util.regex.Pattern.compile(
            "(?is)Árbitro\\s+N[ºo]\\s*Licencia del árbitro.*?\\n"+
            "\\s*([\\p{L}ÁÉÍÓÚÜÑáéíóúüñ, .\\r\\n'-]+?)\\s+(\\d{7})\\b")
            .matcher(pageOne);
        if(referee.find()){
            String name=referee.group(1).replaceAll("\\s+"," ").trim();
            if(name.length()>5){
                found.put("Árbitro",name);
                found.put("Licencia árbitro",referee.group(2));
            }
        }
        // Normalize alternative licence labels before merging cards.
        for(String side:new String[]{"local","visitante"}){
            String licence=found.remove("Licencia Juez línea "+side);
            if(licence==null)licence=found.remove("Licencia juez línea "+side);
            if(licence==null)licence=found.remove("Licencia juez "+side);
            if(licence!=null)found.put("Licencia juez de línea "+side,licence);
        }
        for(String role:new String[]{"Entrenador","Delegado","Juez de línea"}){
            for(String side:new String[]{"local","visitante"}){
                String key=role+" "+side;
                String licenceKey="Licencia "+(role.equals("Juez de línea")?"juez de línea":role)+" "+side;
                String licence=found.remove(licenceKey);
                if(licence!=null&&found.containsKey(key))
                    found.put(key,found.get(key)+"\nLicencia: "+licence);
            }
        }
        String refereeLicence=found.remove("Licencia árbitro");
        if(refereeLicence!=null&&found.containsKey("Árbitro"))
            found.put("Árbitro",found.get("Árbitro")+"\nLicencia: "+refereeLicence);
        // All licence cards have already been merged into the person's card.
        // Discard any remaining aliases produced by the PDF column extractor.
        java.util.ArrayList<String> obsoleteLicences=new java.util.ArrayList<>();
        for(String key:found.keySet()){
            if(key.toLowerCase(java.util.Locale.ROOT).startsWith("licencia "))
                obsoleteLicences.add(key);
        }
        for(String key:obsoleteLicences)found.remove(key);
        for(java.util.Map.Entry<String,String> field:found.entrySet()){
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);
            card.setPadding(dp(12),dp(6),dp(12),dp(6));
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xff1b2a39);bg.setCornerRadius(dp(9));card.setBackground(bg);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,dp(3),0,dp(2));host.addView(card,lp);
            String labelText=field.getKey();
            for(String side:new String[]{"local","visitante"}){
                if(labelText.endsWith(" "+side)){
                    String team="local".equals(side)?home:away;
                    if(team!=null&&!team.trim().isEmpty())
                        labelText=labelText.substring(0,labelText.length()-side.length()-1)
                            +" · "+team.trim();
                    break;
                }
            }
            TextView label=text(labelText.toUpperCase(java.util.Locale.ROOT),11,true);
            label.setTextColor(0xffaab9c9);card.addView(label);
            String display=field.getValue().replace("\\n","\n");
            int licenceBreak=display.indexOf("\nLicencia:");
            if(licenceBreak>=0){
                card.addView(text(display.substring(0,licenceBreak),14,false));
                TextView licenceText=text(display.substring(licenceBreak+1),12,false);
                licenceText.setTextColor(0xffaab9c9);
                card.addView(licenceText);
            }else card.addView(text(display,14,false));
            if("Campo".equals(field.getKey())){
                TextView mapHint=text("📍  Abrir en Google Maps",11,true);
                mapHint.setTextColor(0xff82c7ff);mapHint.setPadding(0,dp(2),0,0);
                card.addView(mapHint);
                card.setClickable(true);card.setFocusable(true);
                card.setOnClickListener(v->{
                    String place=field.getValue();
                    String city=found.get("Localidad");
                    String query=place+(city==null||city.trim().isEmpty()?"":", "+city.trim());
                    android.net.Uri uri=android.net.Uri.parse(
                        "https://www.google.com/maps/search/?api=1&query="+
                        android.net.Uri.encode(query));
                    Intent intent=new Intent(Intent.ACTION_VIEW,uri);
                    intent.setPackage("com.google.android.apps.maps");
                    try{startActivity(intent);}
                    catch(android.content.ActivityNotFoundException ex){
                        Intent browser=new Intent(Intent.ACTION_VIEW,uri);
                        try{startActivity(browser);}
                        catch(Exception ignored){
                            Toast.makeText(this,"No se pudo abrir Google Maps",Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }
        if(found.isEmpty()){
            TextView fallback=text(clean,13,false);fallback.setPadding(dp(4),dp(12),dp(4),0);
            host.addView(fallback);
        }
    }
    private void showLineupCards(ScrollView scroll,String lineups,String homeName,String awayName){
        LinearLayout host=new LinearLayout(this);host.setOrientation(1);
        host.setPadding(dp(2),dp(4),dp(2),dp(64));
        scroll.removeAllViews();scroll.addView(host);
        String[] teams=lineups.split("(?=EQUIPO VISITANTE)");
        LinearLayout teamTabs=new LinearLayout(this);teamTabs.setOrientation(0);
        host.addView(teamTabs);
        LinearLayout groupTabs=new LinearLayout(this);groupTabs.setOrientation(0);
        host.addView(groupTabs);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(1);
        host.addView(rows);
        final int[] selectedTeam={0},selectedGroup={0};
        Runnable render=()->{
            teamTabs.removeAllViews();groupTabs.removeAllViews();rows.removeAllViews();
            for(int i=0;i<teams.length;i++){
                final int index=i;
                Button b=new Button(this);
                String label=i==0?homeName:awayName;
                b.setText(label==null||label.trim().isEmpty()?(i==0?"LOCAL":"VISITANTE"):label.trim());
                b.setTextSize(11);b.setAllCaps(false);b.setMaxLines(2);
                b.setAlpha(i==selectedTeam[0]?1f:.6f);
                teamTabs.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
                b.setOnClickListener(v->{selectedTeam[0]=index;selectedGroup[0]=0;
                    showLineupCardsSelection(host,teams,selectedTeam,selectedGroup,scroll);});
            }
            showLineupGroupTabs(groupTabs,rows,teams,selectedTeam,selectedGroup,scroll);
        };
        render.run();
    }
    private void showLineupCardsSelection(LinearLayout host,String[] teams,int[] selectedTeam,
            int[] selectedGroup,ScrollView scroll){
        LinearLayout teamTabs=(LinearLayout)host.getChildAt(0);
        LinearLayout groupTabs=(LinearLayout)host.getChildAt(1);
        LinearLayout rows=(LinearLayout)host.getChildAt(2);
        for(int i=0;i<teamTabs.getChildCount();i++)
            teamTabs.getChildAt(i).setAlpha(i==selectedTeam[0]?1f:.6f);
        showLineupGroupTabs(groupTabs,rows,teams,selectedTeam,selectedGroup,scroll);
        scroll.post(()->scroll.scrollTo(0,0));
    }
    private void showLineupGroupTabs(LinearLayout tabs,LinearLayout rows,String[] teams,
            int[] selectedTeam,int[] selectedGroup,ScrollView scroll){
        tabs.removeAllViews();rows.removeAllViews();
        String team=teams[Math.min(selectedTeam[0],teams.length-1)];
        String[] parts=team.split("(?=TITULARES \\(|SUPLENTES \\()");
        String[] labels={"TITULARES","SUPLENTES"};
        for(int i=0;i<2;i++){
            final int idx=i;
            int count=0;
            for(String part:parts)if(part.startsWith(labels[i]+" (")){
                java.util.regex.Matcher cm=java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(part);
                if(cm.find())count=Integer.parseInt(cm.group(1));
            }
            Button b=new Button(this);b.setText(labels[i]+" ("+count+")");
            b.setTextSize(11);b.setAllCaps(false);
            b.setAlpha(i==selectedGroup[0]?1f:.6f);
            tabs.addView(b,new LinearLayout.LayoutParams(0,dp(40),1));
            b.setOnClickListener(v->{selectedGroup[0]=idx;
                showLineupGroupTabs(tabs,rows,teams,selectedTeam,selectedGroup,scroll);
                scroll.post(()->scroll.scrollTo(0,0));});
        }
        String wanted=labels[selectedGroup[0]]+" (";
        java.util.regex.Pattern player=java.util.regex.Pattern.compile(
            "^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*(\\d{7})(.*)$");
        int shown=0;
        for(String part:parts){
            if(!part.startsWith(wanted))continue;
            for(String line:part.split("\\r?\\n")){
                java.util.regex.Matcher m=player.matcher(line.trim());
                if(!m.matches())continue;
                LinearLayout card=new LinearLayout(this);card.setOrientation(1);
                card.setPadding(dp(10),dp(6),dp(10),dp(6));
                android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xff1b2a39);bg.setCornerRadius(dp(9));
                card.setBackground(bg);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                cp.setMargins(0,dp(2),0,dp(2));rows.addView(card,cp);
                TextView name=text(m.group(1)+"  "+m.group(2).trim(),13,true);
                card.addView(name);
                String note=m.group(4).trim().replaceFirst("^·\\s*","");
                TextView details=text("Licencia: "+m.group(3)+
                    (note.isEmpty()?"":"   ·   "+note),10,false);
                details.setTextColor(0xffb8c9d9);card.addView(details);
                shown++;
            }
        }
        if(shown==0)rows.addView(text("No hay jugadores identificados en este grupo.",13,false));
    }

    // Read the roster as two independent physical columns. Each seven-digit
    // licence anchors one row, so empty X/C/F cells cannot shift player data.
    private String coordinateLineups(com.tom_roush.pdfbox.pdmodel.PDDocument pdf){
        if(pdf.getNumberOfPages()==0)return null;
        try{
            java.util.ArrayList<float[]> positions=new java.util.ArrayList<>();
            java.util.ArrayList<String> words=new java.util.ArrayList<>();
            com.tom_roush.pdfbox.text.PDFTextStripper reader=
                new com.tom_roush.pdfbox.text.PDFTextStripper(){
                    @Override protected void writeString(String text,
                        java.util.List<com.tom_roush.pdfbox.text.TextPosition> glyphs)
                        throws java.io.IOException{
                        if(glyphs==null||glyphs.isEmpty()||text.trim().isEmpty())return;
                        // Split the PDFBox line into glyph groups by physical gaps.
                        StringBuilder group=new StringBuilder();
                        float start=-1,last=-1,y=-1;
                        for(com.tom_roush.pdfbox.text.TextPosition g:glyphs){
                            String ch=g.getUnicode();
                            if(ch==null||ch.isEmpty())continue;
                            float x=g.getXDirAdj();
                            if(group.length()>0&&(x-last>5.5f||Math.abs(g.getYDirAdj()-y)>2f)){
                                words.add(group.toString().trim());
                                positions.add(new float[]{start,last,y});
                                group.setLength(0);
                            }
                            if(group.length()==0){start=x;y=g.getYDirAdj();}
                            group.append(ch);last=x+g.getWidthDirAdj();
                        }
                        if(group.length()>0){
                            words.add(group.toString().trim());
                            positions.add(new float[]{start,last,y});
                        }
                    }
                };
            reader.setSortByPosition(true);reader.setStartPage(1);reader.setEndPage(1);
            reader.getText(pdf);
            float middle=pdf.getPage(0).getCropBox().getWidth()/2f;
            StringBuilder[] starters={new StringBuilder(),new StringBuilder()};
            StringBuilder[] reserves={new StringBuilder(),new StringBuilder()};
            int[] starterCount={0,0},reserveCount={0,0};
            for(int side=0;side<2;side++){
                float minX=side==0?0:middle,maxX=side==0?middle:Float.MAX_VALUE;
                java.util.ArrayList<Float> rowYs=new java.util.ArrayList<>();
                java.util.ArrayList<Float> headerYs=new java.util.ArrayList<>();
                for(int i=0;i<words.size();i++){
                    float[] pos=positions.get(i);
                    if(pos[0]<minX||pos[0]>=maxX)continue;
                    if(words.get(i).matches("\\d{7}")){
                        boolean seen=false;
                        for(float prior:rowYs)if(Math.abs(prior-pos[2])<2f){seen=true;break;}
                        if(!seen)rowYs.add(pos[2]);
                    }
                    if(words.get(i).matches("(?i).*Dorsal.*")){
                        boolean seen=false;
                        for(float prior:headerYs)if(Math.abs(prior-pos[2])<3f){seen=true;break;}
                        if(!seen)headerYs.add(pos[2]);
                    }
                }
                java.util.Collections.sort(rowYs);
                java.util.Collections.sort(headerYs);
                // The form repeats the column headers before the substitute block.
                float splitY=Float.MAX_VALUE;
                if(headerYs.size()>=2){
                    for(float y:headerYs){
                        if(!rowYs.isEmpty()&&y>rowYs.get(0)+2f){
                            splitY=y;break;
                        }
                    }
                }
                int ordinal=0;
                for(float y:rowYs){
                    java.util.ArrayList<Integer> row=new java.util.ArrayList<>();
                    for(int i=0;i<words.size();i++){
                        float[] pos=positions.get(i);
                        if(pos[0]>=minX&&pos[0]<maxX&&Math.abs(pos[2]-y)<2f)
                            row.add(i);
                    }
                    java.util.Collections.sort(row,(a,b)->Float.compare(
                        positions.get(a)[0],positions.get(b)[0]));
                    StringBuilder line=new StringBuilder();
                    for(int index:row){
                        String part=words.get(index);
                        if(!part.isEmpty())line.append(part).append(' ');
                    }
                    java.util.regex.Matcher player=java.util.regex.Pattern.compile(
                        "^\\s*(\\d{1,3})\\s+([XC]\\s+)?(.+?)\\s+(\\d{7})(?:\\s+(F))?\\s*$")
                        .matcher(line.toString().trim());
                    if(!player.matches())continue;
                    String name=player.group(3).trim();
                    if(name.length()<4||name.toLowerCase(java.util.Locale.ROOT).contains("licencia"))continue;
                    // If headers cannot be located, the first 15 occupied rows
                    // belong to the starting lineup in this federation form.
                    boolean reserve=splitY!=Float.MAX_VALUE?y>splitY:ordinal>=15;
                    StringBuilder target=reserve?reserves[side]:starters[side];
                    target.append(player.group(1)).append("  ·  ")
                        .append(name).append("  |  ").append(player.group(4));
                    // Only accept role marks located in the narrow flag cell
                    // immediately after the dorsal, never a letter in the name.
                    float dorsalRight=-1f;
                    for(int index:row){
                        if(words.get(index).equals(player.group(1))){
                            dorsalRight=positions.get(index)[1];break;
                        }
                    }
                    boolean firstLine=false,captain=false,formation=false;
                    for(int index:row){
                        String mark=words.get(index).trim();
                        float x=positions.get(index)[0];
                        if(dorsalRight>=0&&x>=dorsalRight&&x-dorsalRight<19f){
                            if("X".equals(mark))firstLine=true;
                            if("C".equals(mark))captain=true;
                        }
                        if("F".equals(mark)&&x>minX+(maxX==Float.MAX_VALUE?150f:(maxX-minX)*.70f))
                            formation=true;
                    }
                    if(firstLine)target.append("  ·  Primera línea");
                    if(captain)target.append("  ·  Capitán");
                    if(formation)target.append("  ·  Formación");
                    target.append('\n');
                    if(reserve)reserveCount[side]++;else starterCount[side]++;
                    ordinal++;
                }
            }
            if(starterCount[0]+reserveCount[0]<8||starterCount[1]+reserveCount[1]<8)return null;
            // A failed header split must not mislabel the entire roster.
            if(starterCount[0]==0||starterCount[1]==0)return null;
            return "EQUIPO LOCAL\nTITULARES ("+starterCount[0]+")\n"+
                starters[0]+"SUPLENTES ("+reserveCount[0]+")\n"+reserves[0]+
                "\nEQUIPO VISITANTE\nTITULARES ("+starterCount[1]+")\n"+
                starters[1]+"SUPLENTES ("+reserveCount[1]+")\n"+reserves[1];
        }catch(Exception ignored){return null;}
    }
    private String extractLineupColumns(com.tom_roush.pdfbox.pdmodel.PDDocument pdf){
        try{
            com.tom_roush.pdfbox.pdmodel.PDPage page=pdf.getPage(0);
            float w=page.getCropBox().getWidth(),h=page.getCropBox().getHeight();
            com.tom_roush.pdfbox.text.PDFTextStripperByArea area=
                new com.tom_roush.pdfbox.text.PDFTextStripperByArea();
            area.setSortByPosition(true);
            area.addRegion("local",new android.graphics.RectF(0,0,w*.495f,h));
            area.addRegion("visitor",new android.graphics.RectF(w*.505f,0,w,h));
            area.extractRegions(page);
            String left=parseSingleLineup(area.getTextForRegion("local"));
            String right=parseSingleLineup(area.getTextForRegion("visitor"));
            if(left.isEmpty()||right.isEmpty())return null;
            return "EQUIPO LOCAL\n"+left+"\nEQUIPO VISITANTE\n"+right;
        }catch(Exception ignored){return null;}
    }
    private String parseSingleLineup(String text){
        StringBuilder starters=new StringBuilder(),subs=new StringBuilder();
        java.util.regex.Pattern player=java.util.regex.Pattern.compile(
            "^\\s*(\\d{1,2})\\s+(?:([XC])\\s+)?(.+?)\\s+(\\d{7})(?:\\s+(F))?\\s*$");
        boolean reserve=false,explicitSplit=false;
        int starterCount=0,subCount=0;
        for(String line:text.split("\\r?\n")){
            String t=line.trim();
            String normalized=java.text.Normalizer.normalize(t,java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").toLowerCase(java.util.Locale.ROOT);
            if(normalized.matches(".*\\b(suplentes|reservas|sustitutos)\\b.*")){
                reserve=true;explicitSplit=true;continue;
            }
            if(normalized.matches(".*\\b(titulares|equipo inicial)\\b.*")){
                reserve=false;explicitSplit=true;continue;
            }
            java.util.regex.Matcher m=player.matcher(t);
            if(!m.matches())continue;
            String name=m.group(3).trim();
            if(name.matches("(?i).*licencia.*")||name.length()<5)continue;
            boolean isSub=explicitSplit?reserve:(starterCount>=15);
            StringBuilder target=isSub?subs:starters;
            target.append(m.group(1)).append("  ·  ").append(name)
                .append("  |  ").append(m.group(4));
            if("C".equals(m.group(2)))target.append("  ·  Capitán");
            if("X".equals(m.group(2)))target.append("  ·  Primera línea");
            if(m.group(5)!=null)target.append("  ·  Formación");
            target.append("\n");
            if(isSub)subCount++;else starterCount++;
        }
        if(starterCount+subCount==0)return "";
return "TITULARES ("+starterCount+")\n"+starters+
            "SUPLENTES ("+subCount+")\n"+subs;
    }
    private java.util.LinkedHashMap<String,String> structuredActSections(String raw){
        java.util.LinkedHashMap<String,String> source=splitActSections(raw);
        java.util.LinkedHashMap<String,String> result=new java.util.LinkedHashMap<>();
        result.put("Resumen",source.containsKey("Resumen")?source.get("Resumen"):"Consulta el acta original.");
        String roster=source.get("Alineaciones");
        if(roster!=null){
            StringBuilder local=new StringBuilder(),away=new StringBuilder();
            int count=0; int unmatched=0; StringBuilder uncertain=new StringBuilder();
            // The federation PDF prints two players on each physical line.
            // Split at the seven-digit licence, not at spaces in player names.
            java.util.regex.Pattern player=java.util.regex.Pattern.compile(
                "(\\d{1,3})\\s+([XC]?)[\\s]*([^\\n]*?)\\s+(\\d{7})(?:\\s+(F))?(?=\\s+\\d{1,3}\\s|$)");
            for(String line:roster.split("\\r?\\n")){
                java.util.regex.Matcher m=player.matcher(line.trim());
                java.util.ArrayList<String> row=new java.util.ArrayList<>();
                while(m.find()){
                    String item="Nº "+m.group(1)+" · "+m.group(3).trim()+
                        "\nLicencia: "+m.group(4)+
                        ("C".equals(m.group(2))?" · Capitán":"")+
                        ("X".equals(m.group(2))?" · Primera línea":"")+
                        (m.group(5)!=null?" · Formación":"")+"\n\n";
                    row.add(item);count++;
                }
                if(row.size()==2){local.append(row.get(0));away.append(row.get(1));}
                else if(!row.isEmpty()){unmatched+=row.size();for(String item:row)uncertain.append(item);}
            }
            if(count>=4 && local.length()>0 && away.length()>0)
                result.put("Alineaciones","EQUIPO LOCAL\n\n"+local+"EQUIPO VISITANTE\n\n"+away+
                    (unmatched>0?"JUGADORES SIN EQUIPO VERIFICADO\n\n"+uncertain:""));
            else result.put("Alineaciones","No se pudieron separar todas las licencias con seguridad. Consulta el PDF original.\n\n"+roster);
        }
        result.put("Marcador",parseActEventSection(source.get("Marcador"),"Marcador"));
        result.put("Cambios",parseActEventSection(source.get("Cambios"),"Cambios"));
        String temp=parseActEventSection(source.get("Expulsiones temporales"),"Expulsiones");
        String permanent=parseActEventSection(source.get("Expulsiones definitivas"),"Expulsiones");
        result.put("Expulsiones","TEMPORALES\n"+temp+"\n\nDEFINITIVAS\n"+permanent);
        return result;
    }
    private String parseActEventSection(String raw,String kind){
        if(raw==null||raw.trim().isEmpty())return "Sin información en el acta.";
        StringBuilder out=new StringBuilder();
        String[] blocks=raw.split("(?im)(?=^[ \\t]*Equipo[ \\t]+(?:local|visitante):)");
        boolean any=false;
        for(String block:blocks){
            String normalized=block.trim();
            if(!normalized.matches("(?s)(?i)^Equipo\\s+(local|visitante):.*"))continue;
            String[] lines=normalized.split("\\r?\n");
            String team=lines[0].trim();
            java.util.LinkedHashMap<String,java.util.List<String>> columns=new java.util.LinkedHashMap<>();
            for(String line:lines){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                    "(?i)^\\s*(Dorsal entra|Dorsal sale|Dorsal|Minuto|Tipo)\\s*(?:\\*)?\\s*(.*)$").matcher(line.trim());
                if(m.find()){
                    String key=m.group(1).toLowerCase(Locale.ROOT);
                    String value=m.group(2).trim();
                    java.util.ArrayList<String> tokens=new java.util.ArrayList<>();
                    for(String token:value.split("\\s+"))if(!token.isEmpty()&&!token.equals("*"))tokens.add(token);
                    columns.put(key,tokens);
                }
            }
            out.append(team).append("\n");
            java.util.List<String> types=columns.get("tipo");
            java.util.List<String> minutes=columns.get("minuto");
            int n=minutes==null?0:minutes.size();
            if(types!=null)n=Math.max(n,types.size());
            if(n==0){out.append("Sin incidencias registradas.\n\n");any=true;continue;}
            // A missing dorsal must never discard otherwise valid events.
            // Types and minutes identify the event; missing numbers are optional.
            boolean valid=types!=null&&minutes!=null&&types.size()==n&&minutes.size()==n;
            java.util.List<String> dorsals=columns.get("dorsal");
            if(valid&&dorsals!=null&&dorsals.size()!=n){
                java.util.ArrayList<String> aligned=new java.util.ArrayList<>();
                int nonPenalty=0;
                for(String type:types)if(!"EC".equalsIgnoreCase(type))nonPenalty++;
                if(dorsals.size()==nonPenalty){
                    int index=0;
                    for(String type:types)
                        aligned.add("EC".equalsIgnoreCase(type)?"":dorsals.get(index++));
                }else{
                    // Ambiguous alignment: never attach a number to the wrong player.
                    for(int event=0;event<n;event++)aligned.add("");
                }
                columns.put("dorsal",aligned);
            }
            // In changes and sanctions, do not shift numbers onto the wrong event.
            // Keep the event and mark an incomplete column as unknown.
            for(String optional:("Cambios".equals(kind)?new String[]{"dorsal entra","dorsal sale"}:new String[]{"dorsal"})){
                java.util.List<String> values=columns.get(optional);
                if(values==null||values.size()!=n){
                    java.util.ArrayList<String> blanks=new java.util.ArrayList<>();
                    for(int event=0;event<n;event++)blanks.add("");
                    columns.put(optional,blanks);
                }
            }
            // Other optional columns can also be absent; do not reject the whole team.
            if(!valid){
                out.append("Datos de tipo o minuto incompletos; comprueba el PDF.\n");
                out.append(normalized).append("\n\n");any=true;continue;
            }
            for(int i=0;i<n;i++){
                out.append(i+1).append(". ");
                if(minutes!=null)out.append("Min ").append(minutes.get(i)).append(" · ");
                if(types!=null)out.append(eventDescription(types.get(i),kind)).append(" · ");
                if(!"Cambios".equals(kind)&&columns.containsKey("dorsal"))out.append(columns.get("dorsal").get(i).isEmpty()?"Dorsal no indicado":"Dorsal "+columns.get("dorsal").get(i));
                if("Cambios".equals(kind)&&columns.containsKey("dorsal entra"))out.append(columns.get("dorsal entra").get(i).isEmpty()?"Entra sin dorsal":"Entra nº "+columns.get("dorsal entra").get(i));
                if("Cambios".equals(kind)&&columns.containsKey("dorsal sale"))out.append(columns.get("dorsal sale").get(i).isEmpty()?" · Sale sin dorsal":" · Sale nº "+columns.get("dorsal sale").get(i));
                out.append("\n");
            }
            out.append("\n");any=true;
        }
        return any?out.toString().trim():"No se ha podido interpretar la tabla.\n\n"+raw;
    }
    private String eventDescription(String type,String kind){
        if("Marcador".equals(kind)){
            if("E".equalsIgnoreCase(type))return "Ensayo (5 puntos)";
            if("T".equalsIgnoreCase(type))return "Transformación (2 puntos)";
            if("D".equalsIgnoreCase(type))return "Drop (3 puntos)";
            if("PC".equalsIgnoreCase(type))return "Puntapié de castigo (3 puntos)";
            if("EC".equalsIgnoreCase(type))return "Ensayo de castigo (7 puntos)";
        }
        if("Cambios".equals(kind)&&"CT".equalsIgnoreCase(type))return "Cambio";
        return "Tipo "+type;
    }
    private java.util.LinkedHashMap<String,String> splitActSections(String raw){
        java.util.LinkedHashMap<String,StringBuilder> grouped=new java.util.LinkedHashMap<>();
        String current="Resumen";
        grouped.put(current,new StringBuilder());
        for(String line:raw.split("\\r?\\n")){
            String trimmed=line.trim();
            if(trimmed.isEmpty())continue;
            String normalized=java.text.Normalizer.normalize(trimmed,
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toLowerCase(Locale.ROOT);
            String next=null;
            if(normalized.equals("alineaciones"))next="Alineaciones";
            else if(normalized.equals("marcador"))next="Marcador";
            else if(normalized.equals("cambios"))next="Cambios";
            else if(normalized.equals("expulsiones temporales"))next="Expulsiones temporales";
            else if(normalized.equals("expulsiones definitivas"))next="Expulsiones definitivas";
            else if(normalized.startsWith("pagina "))next="Página original "+trimmed.replaceAll("[^0-9]","");
            if(next!=null){
                if(next.startsWith("Página original")){
                    // Page markers are retained within the current section.
                    grouped.get(current).append("\n").append(trimmed).append("\n");
                    continue;
                }
                current=next;
                if(!grouped.containsKey(current))grouped.put(current,new StringBuilder());
                continue;
            }
            grouped.get(current).append(trimmed).append("\n");
        }
        java.util.LinkedHashMap<String,String> result=new java.util.LinkedHashMap<>();
        for(java.util.Map.Entry<String,StringBuilder> e:grouped.entrySet()){
            String value=e.getValue().toString().trim();
            if(!value.isEmpty())result.put(e.getKey(),value);
        }
        result.put("Texto completo",raw);
        return result;
    }
    private void showStatistics(String url,JSONObject match){
        Dialog dialog=new Dialog(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(10),dp(8),dp(10),dp(8));root.setBackgroundColor(0xff101a24);
        TextView title=text("Estadísticas del partido",21,true);root.addView(title);
        LinearLayout toolbar=new LinearLayout(this);
        Button close=new Button(this);close.setText("Cerrar");close.setTextSize(12);
        toolbar.addView(close,new LinearLayout.LayoutParams(0,dp(45),1));
        close.setOnClickListener(v->dialog.dismiss());
        Button original=new Button(this);original.setText("Web original");original.setTextSize(12);
        toolbar.addView(original,new LinearLayout.LayoutParams(0,dp(45),1));
        original.setOnClickListener(v->openExternal(url));root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);
        body.setPadding(dp(2),dp(8),dp(2),dp(20));
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView loading=text("Cargando datos oficiales…",15,false);body.addView(loading);
        android.webkit.WebView extractor=new android.webkit.WebView(this);
        extractor.setVisibility(View.GONE);extractor.getSettings().setJavaScriptEnabled(true);
        extractor.getSettings().setDomStorageEnabled(true);
        root.addView(extractor,new LinearLayout.LayoutParams(1,1));
        extractor.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public void onPageFinished(android.webkit.WebView view,String loaded){
                String js="(function(){try{"+
                    "var txt=document.body.innerText||'';var sections=[];var current=null;"+
                    "document.querySelectorAll('h3,h4,table').forEach(function(el){"+
                    "if(el.tagName==='H3'||el.tagName==='H4'){var h=el.innerText.trim();"+
                    "if(/^(Anotadores|Cambios equipo|Expulsiones temporales equipo|Expulsiones definitivas equipo)/i.test(h)){current={title:h,rows:[]};sections.push(current);}return;}"+
                    "if(current){el.querySelectorAll('tr').forEach(function(tr){"+
                    "var cells=[...tr.querySelectorAll('td')].map(function(td){return td.innerText.trim();});"+
                    "if(cells.length)current.rows.push(cells);});}});"+
                    "var match=txt.match(/(\\d+)\\s*Puntos[\\s\\S]{0,130}?(\\d+)\\s*Puntos/i);"+
                    "var teams=[...document.querySelectorAll('h4')].map(e=>e.innerText.trim()).filter(x=>x&&!/^(Anotadores|Cambios|Expulsiones)/i.test(x));"+
                    "return JSON.stringify({score:match?[+match[1],+match[2]]:null,teams:teams,sections:sections});"+
                    "}catch(e){return JSON.stringify({error:String(e)});}})()";
                view.evaluateJavascript(js,raw->{
                    try{
                        String decoded=new JSONTokener(raw).nextValue().toString();
                        JSONObject data=new JSONObject(decoded);
                        if(data.has("error"))throw new Exception(data.getString("error"));
                        JSONArray score=data.optJSONArray("score");
                        JSONArray sections=data.optJSONArray("sections");
                        if(score==null||sections==null||sections.length()==0)throw new Exception("Sin datos");
                        body.removeAllViews();
                        JSONArray teams=data.optJSONArray("teams");
                        String homeName=match.optString("home","Equipo local");
                        String awayName=match.optString("away","Equipo visitante");
                        LinearLayout scoreboard=new LinearLayout(RugbyHubActivity.this);
                        scoreboard.setGravity(Gravity.CENTER_VERTICAL);
                        scoreboard.setPadding(dp(2),dp(12),dp(2),dp(12));
                        scoreboard.setBackgroundColor(0xff1b3042);
                        LinearLayout homeCol=new LinearLayout(RugbyHubActivity.this);
                        homeCol.setGravity(Gravity.CENTER);homeCol.setOrientation(1);
                        homeCol.addView(teamLogo(match.optString("homeLogo")),
                            new LinearLayout.LayoutParams(dp(56),dp(56)));
                        TextView homeLabel=text(homeName,12,true);homeLabel.setGravity(Gravity.CENTER);homeLabel.setMaxLines(2);
                        homeCol.addView(homeLabel);scoreboard.addView(homeCol,new LinearLayout.LayoutParams(0,-2,1));
                        TextView result=text(score.optInt(0)+"–"+score.optInt(1),28,true);
                        result.setGravity(Gravity.CENTER);
                        result.setSingleLine(true);
                        result.setEllipsize(null);
                        result.setAutoSizeTextTypeUniformWithConfiguration(18,28,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                        scoreboard.addView(result,new LinearLayout.LayoutParams(dp(118),dp(60)));
                        LinearLayout awayCol=new LinearLayout(RugbyHubActivity.this);
                        awayCol.setGravity(Gravity.CENTER);awayCol.setOrientation(1);
                        awayCol.addView(teamLogo(match.optString("awayLogo")),
                            new LinearLayout.LayoutParams(dp(68),dp(68)));
                        TextView awayLabel=text(awayName,12,true);awayLabel.setGravity(Gravity.CENTER);awayLabel.setMaxLines(2);
                        awayCol.addView(awayLabel);scoreboard.addView(awayCol,new LinearLayout.LayoutParams(0,-2,1));
                        body.addView(scoreboard);
                        JSONArray homeEvents=null,awayEvents=null;
                        for(int i=0;i<sections.length();i++){
                            JSONObject sec=sections.optJSONObject(i);if(sec==null)continue;
                            if("Anotadores".equalsIgnoreCase(sec.optString("title"))){
                                JSONArray candidate=sec.optJSONArray("rows");
                                boolean isEvent=false;
                                if(candidate!=null)for(int k=0;k<candidate.length();k++){
                                    JSONArray r=candidate.optJSONArray(k);
                                    if(r!=null&&r.length()>=3&&r.optString(0).matches("[0-9]{1,3}")&&r.optString(1).matches("(?i)E|T|GC|P|F|D")){isEvent=true;break;}
                                }
                                if(!isEvent)continue;
                                if(homeEvents==null)homeEvents=sec.optJSONArray("rows");
                                else if(awayEvents==null)awayEvents=sec.optJSONArray("rows");
                            }
                        }
                        if(homeEvents!=null||awayEvents!=null){
                            TextView graphTitle=text("EVOLUCIÓN DEL MARCADOR",17,true);
                            graphTitle.setPadding(0,dp(22),0,dp(8));body.addView(graphTitle);
                            ArrayList<int[]> events=new ArrayList<>();
                            JSONArray[] arrays={homeEvents,awayEvents};
                            for(int team=0;team<2;team++){
                                JSONArray arr=arrays[team];if(arr==null)continue;
                                for(int j=0;j<arr.length();j++){
                                    JSONArray row=arr.optJSONArray(j);if(row==null||row.length()<2)continue;
                                    try{
                                        int minute=Integer.parseInt(row.optString(0));
                                        String kind=row.optString(1).trim();
                                        int pts="E".equalsIgnoreCase(kind)?5:"T".equalsIgnoreCase(kind)?2:
                                            ("F".equalsIgnoreCase(kind)||"GC".equalsIgnoreCase(kind)||
                                             "P".equalsIgnoreCase(kind)||"D".equalsIgnoreCase(kind))?3:0;
                                        if(pts>0)events.add(new int[]{minute,team,pts});
                                    }catch(Exception ignored){}
                                }
                            }
                            java.util.Collections.sort(events,(x,y)->Integer.compare(x[0],y[0]));
                            final ArrayList<int[]> graphEvents=events;
                            int[] calculated={0,0};
                            for(int[] event:events)calculated[event[1]]+=event[2];
                            boolean graphMatches=calculated[0]==score.optInt(0)&&calculated[1]==score.optInt(1);
                            View graph=new View(RugbyHubActivity.this){
                                android.graphics.Paint p=new android.graphics.Paint(3);
                                @Override protected void onDraw(android.graphics.Canvas c){
                                    super.onDraw(c);
                                    float left=dp(36),top=dp(20),right=getWidth()-dp(15),bottom=getHeight()-dp(35);
                                    int maxMinute=40,maxPoints=Math.max(5,Math.max(score.optInt(0),score.optInt(1)));
                                    for(int[] e:graphEvents)maxMinute=Math.max(maxMinute,e[0]);
                                    p.setStrokeWidth(dp(1));p.setColor(0xff435567);
                                    for(int i=0;i<=4;i++){
                                        float y=bottom-(bottom-top)*i/4f;c.drawLine(left,y,right,y,p);
                                        p.setColor(Color.WHITE);p.setTextSize(dp(10));
                                        c.drawText(String.valueOf(Math.round(maxPoints*i/4f)),dp(3),y+dp(3),p);
                                        p.setColor(0xff435567);
                                    }
                                    for(int i=0;i<=4;i++){
                                        float x=left+(right-left)*i/4f;
                                        p.setColor(0xffaab9c9);p.setTextSize(dp(10));
                                        c.drawText(String.valueOf(Math.round(maxMinute*i/4f))+"'",x-dp(9),bottom+dp(18),p);
                                    }
                                    int[] totals={0,0};float[] lastX={left,left};float[] lastY={bottom,bottom};
                                    int[] colors={0xff54b6ef,0xffe99a64};
                                    for(int[] e:graphEvents){
                                        int team=e[1];float x=left+(right-left)*e[0]/maxMinute;
                                        float newY=bottom-(bottom-top)*(totals[team]+e[2])/maxPoints;
                                        p.setColor(colors[team]);p.setStrokeWidth(dp(3));
                                        c.drawLine(lastX[team],lastY[team],x,lastY[team],p);
                                        c.drawLine(x,lastY[team],x,newY,p);
                                        totals[team]+=e[2];lastX[team]=x;lastY[team]=newY;
                                    }
                                    for(int team=0;team<2;team++){
                                        p.setColor(colors[team]);p.setStrokeWidth(dp(3));
                                        c.drawLine(lastX[team],lastY[team],right,lastY[team],p);
                                    }
                                }
                            };
                            graph.setBackgroundColor(0xff1b3042);
                            if(!graphMatches){
                                TextView warning=text("Aviso: la evolución extraída ("+calculated[0]+"–"+calculated[1]+
                                    ") no coincide con el marcador oficial ("+score.optInt(0)+"–"+score.optInt(1)+").",14,true);
                                warning.setTextColor(0xffffc777);warning.setPadding(dp(8),dp(10),dp(8),dp(10));
                                body.addView(warning);
                            }
                            body.addView(graph,new LinearLayout.LayoutParams(-1,dp(260)));
                            TextView legend=text("● Azul: "+homeName+"\n● Naranja: "+awayName,15,false);
                            legend.setPadding(dp(8),dp(12),dp(8),dp(12));body.addView(legend);
                            java.util.LinkedHashMap<String,Integer> players=new java.util.LinkedHashMap<>();
                            java.util.LinkedHashMap<String,String> playerTeams=new java.util.LinkedHashMap<>();
                            for(int team=0;team<arrays.length;team++){
                                JSONArray arr=arrays[team];
                                if(arr==null)continue;
                                for(int j=0;j<arr.length();j++){
                                    JSONArray r=arr.optJSONArray(j);if(r==null||r.length()<3)continue;
                                    String code=r.optString(1).trim().toUpperCase(java.util.Locale.ROOT);
                                    int pts="E".equals(code)?5:"T".equals(code)?2:
                                        ("F".equals(code)||"GC".equals(code)||"P".equals(code)||"D".equals(code))?3:0;
                                    if(pts>0&&r.optString(0).matches("[0-9]{1,3}")){
                                        String name=r.optString(2).trim();
                                        String key=team+"|"+name;
                                        players.put(key,players.containsKey(key)?players.get(key)+pts:pts);
                                        playerTeams.put(key,team==0?homeName:awayName);
                                    }
                                }
                            }
                            TextView pieTitle=text("PUNTOS POR ANOTADOR",17,true);
                            pieTitle.setPadding(0,dp(22),0,dp(8));body.addView(pieTitle);
                            final ArrayList<Integer> amounts=new ArrayList<>(players.values());
                            final int[] palette={0xff58b7e7,0xffe99a64,0xff7ecf9e,0xffbd95ed,0xffe3c16c,0xffed8aa6,0xff86c7c9,0xffc9a47d};
                            View pie=new View(RugbyHubActivity.this){
                                android.graphics.Paint p=new android.graphics.Paint(3);
                                @Override protected void onDraw(android.graphics.Canvas c){
                                    super.onDraw(c);
                                    int total=0;for(int v:amounts)total+=v;
                                    if(total==0)return;
                                    float size=Math.min(getWidth(),getHeight())-dp(20);
                                    float l=(getWidth()-size)/2f,t=(getHeight()-size)/2f;
                                    android.graphics.RectF bounds=new android.graphics.RectF(l,t,l+size,t+size);
                                    float angle=-90;
                                    for(int i=0;i<amounts.size();i++){
                                        float sweep=360f*amounts.get(i)/total;
                                        p.setColor(palette[i%palette.length]);p.setStyle(android.graphics.Paint.Style.FILL);
                                        c.drawArc(bounds,angle,sweep,true,p);angle+=sweep;
                                    }
                                }
                            };
                            body.addView(pie,new LinearLayout.LayoutParams(-1,dp(250)));
                            int total=0;for(int v:amounts)total+=v;
                            int idx=0;for(java.util.Map.Entry<String,Integer> e:players.entrySet()){
                                TextView player=text("●  "+e.getKey().substring(e.getKey().indexOf('|')+1)+
                                    "\n    "+playerTeams.get(e.getKey())+" · "+e.getValue()+" puntos ("+
                                    (total==0?0:Math.round(100f*e.getValue()/total))+"%)",16,false);
                                player.setTextColor(palette[idx++%palette.length]);player.setPadding(dp(10),dp(10),dp(10),dp(10));body.addView(player);
                            }
                            java.util.LinkedHashMap<String,Integer> types=new java.util.LinkedHashMap<>();
                            types.put("Ensayos",0);types.put("Transformaciones",0);
                            types.put("Golpes de castigo",0);types.put("Drops",0);
                            int[][] teamTypeCounts=new int[2][4];
                            for(int team=0;team<arrays.length;team++){
                                JSONArray arr=arrays[team];if(arr==null)continue;
                                for(int j=0;j<arr.length();j++){
                                    JSONArray r=arr.optJSONArray(j);if(r==null||r.length()<3)continue;
                                    if(!r.optString(0).matches("[0-9]{1,3}"))continue;
                                    String code=r.optString(1).trim().toUpperCase(java.util.Locale.ROOT);
                                    int index="E".equals(code)?0:"T".equals(code)?1:
                                        ("GC".equals(code)||"P".equals(code)||"F".equals(code))?2:
                                        "D".equals(code)?3:-1;
                                    if(index>=0)teamTypeCounts[team][index]++;
                                }
                            }
                            String[] typeNames={"Ensayos","Transformaciones","Golpes de castigo","Drops"};
                            for(int k=0;k<typeNames.length;k++)
                                types.put(typeNames[k],teamTypeCounts[0][k]+teamTypeCounts[1][k]);
                            TextView typeHeading=text("ANOTACIONES POR TIPO",17,true);
                            typeHeading.setPadding(0,dp(22),0,dp(8));body.addView(typeHeading);
                            final ArrayList<Integer> counts=new ArrayList<>(types.values());
                            final int[] typeColors={0xff58b7e7,0xffe99a64,0xff7ecf9e,0xffbd95ed};
                            View typePie=new View(RugbyHubActivity.this){
                                android.graphics.Paint p=new android.graphics.Paint(3);
                                @Override protected void onDraw(android.graphics.Canvas c){
                                    super.onDraw(c);
                                    int sum=0;for(int n:counts)sum+=n;if(sum==0)return;
                                    float size=Math.min(getWidth(),getHeight())-dp(20);
                                    float left=(getWidth()-size)/2f,top=(getHeight()-size)/2f;
                                    android.graphics.RectF rect=new android.graphics.RectF(left,top,left+size,top+size);
                                    float start=-90;
                                    for(int k=0;k<counts.size();k++){
                                        float sweep=360f*counts.get(k)/sum;
                                        p.setColor(typeColors[k]);p.setStyle(android.graphics.Paint.Style.FILL);
                                        c.drawArc(rect,start,sweep,true,p);start+=sweep;
                                    }
                                }
                            };
                            body.addView(typePie,new LinearLayout.LayoutParams(-1,dp(250)));
                            int sum=0;for(int n:counts)sum+=n;
                            TextView typeExplanation=text("Porcentaje calculado sobre el número de anotaciones. Los puntos se muestran por separado.",14,false);
                            typeExplanation.setTextColor(0xffaab9c9);typeExplanation.setPadding(dp(8),dp(10),dp(8),dp(12));body.addView(typeExplanation);
                            int[] typePointTotals=new int[2];
                            for(int team=0;team<2;team++){
                                LinearLayout teamCard=new LinearLayout(RugbyHubActivity.this);
                                teamCard.setOrientation(1);
                                teamCard.setPadding(dp(12),dp(12),dp(12),dp(12));
                                teamCard.setBackgroundColor(0xff1b3042);
                                LinearLayout.LayoutParams teamParams=new LinearLayout.LayoutParams(-1,-2);
                                teamParams.topMargin=dp(12);
                                body.addView(teamCard,teamParams);
                                String teamName=team==0?homeName:awayName;
                                int official=score.optInt(team);
                                TextView teamTitle=text((team==0?"LOCAL · ":"VISITANTE · ")+teamName,16,true);
                                teamTitle.setTextColor(team==0?0xff58b7e7:0xffe99a64);
                                teamCard.addView(teamTitle);
                                for(int k=0;k<typeNames.length;k++){
                                    int n=teamTypeCounts[team][k];
                                    int pts=k==0?5:k==1?2:3;
                                    typePointTotals[team]+=n*pts;
                                    if(n==0)continue;
                                    TextView detail=text(typeNames[k]+": "+n+" × "+pts+" = "+(n*pts)+" puntos",15,false);
                                    detail.setPadding(0,dp(7),0,0);
                                    teamCard.addView(detail);
                                }
                                TextView teamTotal=text("TOTAL  "+typePointTotals[team]+" puntos",16,true);
                                teamTotal.setPadding(0,dp(12),0,0);
                                teamCard.addView(teamTotal);
                                if(typePointTotals[team]!=official){
                                    TextView mismatch=text("⚠ No coincide con el marcador oficial ("+official+")",13,true);
                                    mismatch.setTextColor(0xffffc777);
                                    mismatch.setPadding(0,dp(7),0,0);
                                    teamCard.addView(mismatch);
                                }
                            }
                            if(typePointTotals[0]==score.optInt(0)&&typePointTotals[1]==score.optInt(1)){
                                TextView verified=text("✓ Recuento comprobado",11,false);
                                verified.setTextColor(0xff8295a5);
                                verified.setGravity(Gravity.END);
                                verified.setPadding(0,dp(6),dp(4),0);
                                body.addView(verified);
                            }
                            int typeIndex=0;for(java.util.Map.Entry<String,Integer> e:types.entrySet()){
                                int color=typeColors[typeIndex++];
                                if(e.getValue()==0)continue;
                                int typePoints="Ensayos".equals(e.getKey())?5:"Transformaciones".equals(e.getKey())?2:3;
                                TextView item=text("●  "+e.getKey()+" · "+e.getValue()+" anotaciones · "+
                                    (e.getValue()*typePoints)+" puntos ("+
                                    (sum==0?0:Math.round(100f*e.getValue()/sum))+"%)",16,false);
                                item.setTextColor(color);item.setPadding(dp(10),dp(10),dp(10),dp(10));
                                body.addView(item);
                            }
                        }
                        // Present local scoring events before visitor scoring events.
                        JSONArray orderedSections=new JSONArray();
                        for(int pass=0;pass<3;pass++){
                            for(int i=0;i<sections.length();i++){
                                JSONObject sec=sections.optJSONObject(i);if(sec==null)continue;
                                String name=sec.optString("title");
                                JSONArray rows=sec.optJSONArray("rows");
                                boolean localScoring="Anotadores".equalsIgnoreCase(name)&&rows==homeEvents;
                                boolean awayScoring="Anotadores".equalsIgnoreCase(name)&&rows==awayEvents;
                                if((pass==0&&localScoring)||(pass==1&&awayScoring)||
                                    (pass==2&&!localScoring&&!awayScoring))orderedSections.put(sec);
                            }
                        }
                        if(homeEvents==null||homeEvents.length()==0){
                            TextView noHome=text("ANOTACIONES · EQUIPO LOCAL",17,true);
                            noHome.setPadding(0,dp(22),0,dp(8));body.addView(noHome);
                            TextView emptyHome=text("Sin anotaciones",15,false);
                            emptyHome.setTextColor(0xffaab9c9);body.addView(emptyHome);
                        }
                        for(int i=0;i<orderedSections.length();i++){
                            JSONObject sec=orderedSections.optJSONObject(i);if(sec==null)continue;
                            String sectionName=sec.optString("title");
                            if("Anotadores".equalsIgnoreCase(sectionName)){
                                JSONArray sectionRows=sec.optJSONArray("rows");
                                if(sectionRows!=homeEvents&&sectionRows!=awayEvents)continue;
                                sectionName="ANOTACIONES · "+(sectionRows==homeEvents?"EQUIPO LOCAL":"EQUIPO VISITANTE");
                            }
                            TextView heading=text(sectionName.toUpperCase(java.util.Locale.ROOT),18,true);
                            heading.setPadding(0,dp(22),0,dp(9));body.addView(heading);
                            JSONArray rows=sec.optJSONArray("rows");
                            if(rows==null||rows.length()==0){
                                TextView none=text("Sin registros",13,false);
                                none.setTextColor(0xffaab9c9);body.addView(none);continue;
                            }
                            for(int j=0;j<rows.length();j++){
                                JSONArray row=rows.optJSONArray(j);if(row==null)continue;
                                if("Anotadores".equalsIgnoreCase(sec.optString("title"))&&
                                    (row.length()<3||!row.optString(0).matches("[0-9]{1,3}")||
                                     !row.optString(1).matches("(?i)E|T|GC|P|F|D")))continue;
                                StringBuilder line=new StringBuilder();
                                String category=sec.optString("title").toLowerCase(java.util.Locale.ROOT);
                                if(category.contains("cambios")&&row.length()>=3){
                                    line.append("SALE ↓  ").append(row.optString(0)).append("\n");
                                    line.append("ENTRA ↑  ").append(row.optString(1)).append("\n");
                                    for(int k=2;k<row.length();k++){if(k>2)line.append(" · ");line.append(row.optString(k));}
                                }else for(int k=0;k<row.length();k++){
                                    if(k>0)line.append("  ·  ");line.append(row.optString(k));
                                }
                                if(category.contains("cambios")&&row.length()>=3){
                                    LinearLayout changeCard=new LinearLayout(RugbyHubActivity.this);
                                    changeCard.setOrientation(1);changeCard.setPadding(dp(12),dp(10),dp(12),dp(10));
                                    changeCard.setBackgroundColor(0xff1b3042);
                                    TextView out=text("↓ SALE  "+row.optString(0),16,true);
                                    out.setTextColor(0xffff9c9c);changeCard.addView(out);
                                    TextView in=text("↑ ENTRA  "+row.optString(1),16,true);
                                    in.setTextColor(0xff8be3b6);in.setPadding(0,dp(5),0,0);
                                    changeCard.addView(in);
                                    StringBuilder detail=new StringBuilder();
                                    for(int k=2;k<row.length();k++){
                                        if(k>2)detail.append(" · ");detail.append(row.optString(k));
                                    }
                                    TextView reason=text(detail.toString(),13,false);
                                    reason.setTextColor(0xffaab9c9);reason.setPadding(0,dp(6),0,0);
                                    changeCard.addView(reason);
                                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                                    cp.bottomMargin=dp(8);body.addView(changeCard,cp);
                                    continue;
                                }
                                TextView card=text(line.toString(),16,false);
                                card.setPadding(dp(13),dp(13),dp(13),dp(13));
                                card.setBackgroundColor(category.contains("expulsiones definitivas")?0xff512a32:
                                    category.contains("expulsiones temporales")?0xff51412b:0xff1b3042);
                                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
                                params.bottomMargin=dp(6);body.addView(card,params);
                            }
                        }
                        if(awayEvents==null||awayEvents.length()==0){
                            TextView noAway=text("ANOTACIONES · EQUIPO VISITANTE",17,true);
                            noAway.setPadding(0,dp(22),0,dp(8));body.addView(noAway);
                            TextView emptyAway=text("Sin anotaciones",15,false);
                            emptyAway.setTextColor(0xffaab9c9);body.addView(emptyAway);
                        }
                    }catch(Exception ex){
                        loading.setText("No se han podido extraer las estadísticas completas. Usa «Web original» para consultarlas.");
                    }
                });
            }
        });
        dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);
        dialog.setOnDismissListener(d->extractor.destroy());extractor.loadUrl(url);
    }
    private void showActPdf(String url){
        android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(6),dp(8),dp(6),dp(8));root.setBackgroundColor(0xff101a24);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button close=new Button(this);close.setText("Cerrar");toolbar.addView(close);
        Button outside=new Button(this);outside.setText("Abrir fuera");toolbar.addView(outside);
        close.setOnClickListener(v->dialog.dismiss());
        outside.setOnClickListener(v->openExternal(url));
        root.addView(toolbar);
        TextView status=text("Descargando acta oficial…",13,false);root.addView(status);
        TextView hint=text("Amplía con dos dedos y arrastra para leer",12,false);root.addView(hint);
        android.widget.ImageView pageImage=new android.widget.ImageView(this);
        pageImage.setScaleType(android.widget.ImageView.ScaleType.MATRIX);
        android.graphics.Matrix zoomMatrix=new android.graphics.Matrix();
        final float[] zoom={1f},lastX={0f},lastY={0f};
        android.view.ScaleGestureDetector pinch=new android.view.ScaleGestureDetector(this,
            new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                    float factor=detector.getScaleFactor();
                    float next=Math.max(1f,Math.min(5f,zoom[0]*factor));
                    factor=next/zoom[0];zoom[0]=next;
                    zoomMatrix.postScale(factor,factor,detector.getFocusX(),detector.getFocusY());
                    pageImage.setImageMatrix(zoomMatrix);return true;
                }
            });
        pageImage.setOnTouchListener((v,event)->{
            pinch.onTouchEvent(event);
            if(event.getPointerCount()==1){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                    lastX[0]=event.getX();lastY[0]=event.getY();return true;
                }
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE&&zoom[0]>1f){
                    zoomMatrix.postTranslate(event.getX()-lastX[0],event.getY()-lastY[0]);
                    pageImage.setImageMatrix(zoomMatrix);
                    lastX[0]=event.getX();lastY[0]=event.getY();return true;
                }
            }
            return true;
        });
        android.widget.FrameLayout scroller=new android.widget.FrameLayout(this);
        scroller.addView(pageImage,new android.widget.FrameLayout.LayoutParams(-1,-1));
        root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER);
        Button previous=new Button(this);previous.setText("Anterior");
        Button next=new Button(this);next.setText("Siguiente");
        navigation.addView(previous);navigation.addView(next);root.addView(navigation);
        previous.setEnabled(false);next.setEnabled(false);
        final android.graphics.pdf.PdfRenderer[] renderer={null};
        final android.os.ParcelFileDescriptor[] descriptor={null};
        final java.io.File[] temporary={null};
        final int[] index={0};
        Runnable render=()->{
            try{
                android.graphics.pdf.PdfRenderer pdf=renderer[0];
                if(pdf==null)return;
                android.graphics.pdf.PdfRenderer.Page page=pdf.openPage(index[0]);
                int width=Math.max(dp(360),getResources().getDisplayMetrics().widthPixels-dp(20));
                int height=Math.max(1,(int)((double)width*page.getHeight()/page.getWidth()));
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();pageImage.setImageBitmap(bitmap);
                zoom[0]=1f;zoomMatrix.reset();
                float viewWidth=getResources().getDisplayMetrics().widthPixels-dp(12);
                float viewHeight=getResources().getDisplayMetrics().heightPixels-dp(250);
                float fit=Math.min(viewWidth/width,viewHeight/height);
                zoomMatrix.postScale(fit,fit);
                zoomMatrix.postTranslate((viewWidth-width*fit)/2f,(viewHeight-height*fit)/2f);
                pageImage.setImageMatrix(zoomMatrix);
                status.setText("Página "+(index[0]+1)+" de "+pdf.getPageCount());
                previous.setEnabled(index[0]>0);next.setEnabled(index[0]<pdf.getPageCount()-1);
            }catch(Exception ex){status.setText("No se pudo mostrar esta página.");}
        };
        previous.setOnClickListener(v->{index[0]--;render.run();});
        next.setOnClickListener(v->{index[0]++;render.run();});
        dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);
        dialog.setOnDismissListener(d->{
            try{if(renderer[0]!=null)renderer[0].close();}catch(Exception ignored){}
            try{if(descriptor[0]!=null)descriptor[0].close();}catch(Exception ignored){}
            if(temporary[0]!=null)temporary[0].delete();
        });
        new Thread(()->{
            java.net.HttpURLConnection connection=null;
            try{
                connection=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
                connection.setConnectTimeout(10000);connection.setReadTimeout(20000);
                java.io.File file=java.io.File.createTempFile("rugby_act_",".pdf",getCacheDir());
                temporary[0]=file;
                try(java.io.InputStream in=connection.getInputStream();
                    java.io.FileOutputStream out=new java.io.FileOutputStream(file)){
                    byte[] buffer=new byte[16384];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                runOnUiThread(()->{
                    if(!dialog.isShowing())return;
                    try{
                        descriptor[0]=android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                        renderer[0]=new android.graphics.pdf.PdfRenderer(descriptor[0]);
                        render.run();
                    }catch(Exception ex){status.setText("No se pudo visualizar el PDF. Usa «Abrir fuera».");}
                });
            }catch(Exception ex){runOnUiThread(()->{if(dialog.isShowing())status.setText("No se pudo descargar el acta. Usa «Abrir fuera».");});}
            finally{if(connection!=null)connection.disconnect();}
        }).start();
    }
    private void detailLine(LinearLayout parent,String label,String value){
        if(value==null||value.trim().isEmpty())return;
        TextView line=text(label+": "+value,14,false);
        line.setPadding(0,dp(6),0,dp(6));parent.addView(line);
    }
    private void openExternal(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
        catch(Exception ex){Toast.makeText(this,"No se pudo abrir el enlace",Toast.LENGTH_SHORT).show();}
    }
    private final java.util.Map<String,android.graphics.Bitmap> logoCache=
        new java.util.concurrent.ConcurrentHashMap<>();
    private android.widget.ImageView teamLogo(String url){
        android.widget.ImageView image=new android.widget.ImageView(this);
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if(url==null||url.isEmpty())return image;
        image.setTag(url);
        android.graphics.Bitmap cached=logoCache.get(url);
        if(cached!=null){image.setImageBitmap(cached);return image;}
        new Thread(()->{
            java.net.HttpURLConnection conn=null;
            try{
                conn=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
                conn.setConnectTimeout(7000);conn.setReadTimeout(7000);
                android.graphics.Bitmap bitmap;
                try(java.io.InputStream stream=conn.getInputStream()){
                    bitmap=android.graphics.BitmapFactory.decodeStream(stream);
                }
                if(bitmap!=null){
                    logoCache.put(url,bitmap);
                    runOnUiThread(()->{if(url.equals(image.getTag()))image.setImageBitmap(bitmap);});
                }
            }catch(Exception ignored){}
            finally{if(conn!=null)conn.disconnect();}
        }).start();
        return image;
    }
    private void showMatches(){
        clear();
        screenTitle.setText("Rugby M14");
        screenSubtitle.setText("Todas las jornadas y organizadores sincronizados");
        Button refresh=new Button(this);
        refresh.setText("Actualizar datos");
        list.addView(refresh);
        TextView status=text("",12,false);status.setTextColor(0xffaab9c9);
        list.addView(status);
        refresh.setOnClickListener(v->updateOfficial(status));
        Button inspect=new Button(this);
        inspect.setText("Comprobar estructura de jornadas");
        list.addView(inspect);
        inspect.setOnClickListener(v->inspectOfficial());
        android.content.SharedPreferences prefs=getSharedPreferences("rugby_official",MODE_PRIVATE);
        long updated=prefs.getLong("updated",0);
        status.setText(updated==0?"Aún no se han descargado datos oficiales":
            "Última actualización: "+new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(updated)));
        JSONArray records;
        try{records=new JSONArray(prefs.getString("matches","[]"));}
        catch(Exception ex){records=new JSONArray();}
        if(records.length()==0){
            list.addView(text("Pulsa Actualizar datos para descargar los partidos desde la federación.",15,false));
            return;
        }
        LinkedHashMap<String,ArrayList<JSONObject>> grouped=new LinkedHashMap<>();
        for(int i=0;i<records.length();i++){
            JSONObject item=records.optJSONObject(i);if(item==null)continue;
            String key=item.optString("jornada",item.optString("date")+" · "+item.optString("organizer"));
            if(!grouped.containsKey(key))grouped.put(key,new ArrayList<>());
            grouped.get(key).add(item);
        }
        for(Map.Entry<String,ArrayList<JSONObject>> group:grouped.entrySet()){
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xff223343);bg.setCornerRadius(dp(14));card.setBackground(bg);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(12);list.addView(card,cp);
            String heading=group.getKey();
            String[] parts=heading.split("(?i)\\.\\s*Organizador:\\s*",2);
            TextView roundTitle=text(parts[0].replaceFirst("(?i)\\s*-\\s*1.ª?\\s*Fase\\.?","").trim(),17,true);
            card.addView(roundTitle);
            if(parts.length>1){
                TextView organizer=text("Organizador: "+parts[1],12,false);
                organizer.setTextColor(0xffaab9c9);card.addView(organizer);
            }
            String groupDate=group.getValue().isEmpty()?"":group.getValue().get(0).optString("date");
            if(!groupDate.isEmpty()){
                TextView headingDate=text(groupDate,14,false);
                headingDate.setTextColor(0xffaab9c9);
                LinearLayout.LayoutParams hd=new LinearLayout.LayoutParams(-1,-2);hd.topMargin=dp(7);
                card.addView(headingDate,hd);
            }
            int matchIndex=0;
            for(JSONObject match:group.getValue()){
                if(matchIndex++>0){
                    View separator=new View(this);separator.setBackgroundColor(0xff3b4b5b);
                    LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(1));
                    sp.topMargin=dp(10);card.addView(separator,sp);
                }
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0,dp(12),0,dp(8));card.addView(row);
                row.setOnClickListener(v->openMatchDetail(match));
                LinearLayout homeColumn=new LinearLayout(this);homeColumn.setOrientation(1);
                homeColumn.setGravity(Gravity.CENTER);row.addView(homeColumn,new LinearLayout.LayoutParams(0,-2,1));
                android.widget.ImageView homeLogo=teamLogo(match.optString("homeLogo"));
                homeColumn.addView(homeLogo,new LinearLayout.LayoutParams(dp(42),dp(42)));
                TextView home=text(match.optString("home"),12,true);home.setGravity(Gravity.CENTER);
                homeColumn.addView(home);
                LinearLayout center=new LinearLayout(this);center.setOrientation(1);
                center.setGravity(Gravity.CENTER);row.addView(center,new LinearLayout.LayoutParams(dp(84),-2));
                TextView score=text(match.optString("score","").isEmpty()?"VS":match.optString("score"),19,true);
                score.setTextColor(0xff85ddbd);score.setGravity(Gravity.CENTER);center.addView(score);
                TextView time=text(match.optString("time","").isEmpty()?"Hora pendiente":match.optString("time"),12,false);
                time.setGravity(Gravity.CENTER);center.addView(time);
                LinearLayout awayColumn=new LinearLayout(this);awayColumn.setOrientation(1);
                awayColumn.setGravity(Gravity.CENTER);row.addView(awayColumn,new LinearLayout.LayoutParams(0,-2,1));
                android.widget.ImageView awayLogo=teamLogo(match.optString("awayLogo"));
                awayColumn.addView(awayLogo,new LinearLayout.LayoutParams(dp(42),dp(42)));
                TextView away=text(match.optString("away"),12,true);away.setGravity(Gravity.CENTER);
                awayColumn.addView(away);
            }
        }
    }
}
