package es.farcompeticiones;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.RectF;
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
    long lastAutomaticRefreshAttempt=0L;
    TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(15,12,15,12);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));home();}
    Bitmap farLogoHighContrast(){
        Bitmap original=BitmapFactory.decodeResource(getResources(),R.drawable.logo_far);
        Bitmap result=original.copy(Bitmap.Config.ARGB_8888,true);
        float[] hsv=new float[3];
        for(int y=0;y<result.getHeight();y++)for(int x=0;x<result.getWidth();x++){
            int pixel=result.getPixel(x,y),alpha=Color.alpha(pixel);
            if(alpha==0)continue;
            Color.colorToHSV(pixel,hsv);
            int r=Color.red(pixel),g=Color.green(pixel),b=Color.blue(pixel);
            // Conservar los verdes del símbolo y aclararlos suavemente.
            boolean isGreen=(g>=r+9&&g>=b+5&&g>=19)
                ||(hsv[0]>=95f&&hsv[0]<=175f&&hsv[1]>.26f);
            if(isGreen){
                hsv[1]=Math.max(.60f,Math.min(.96f,hsv[1]));
                hsv[2]=Math.min(.75f,Math.max(.35f,hsv[2]*1.8f+.13f));
                result.setPixel(x,y,Color.HSVToColor(alpha,hsv));
            }else if(x>result.getWidth()*.47f&&r<105&&g<105&&b<105){
                // Letras FAR blancas para contrastar con el fondo oscuro.
                result.setPixel(x,y,Color.argb(alpha,255,255,255));
            }
        }
        original.recycle();
        return result;
    }
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
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    android.graphics.drawable.GradientDrawable gradient(int top,int bottom,int stroke){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        d.setCornerRadius(dp(18));d.setStroke(dp(2),stroke);return d;
    }
    void home(){
        page="landing";
        if(dataBrowser!=null){dataBrowser.stopLoading();dataBrowser.destroy();dataBrowser=null;}
        browser=null;
        ScrollView scroll=new ScrollView(this);scroll.setFitsSystemWindows(true);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        LinearLayout column=new LinearLayout(this);column.setOrientation(1);
        column.setPadding(dp(12),dp(15),dp(12),dp(24));column.setBackgroundColor(bg);
        scroll.addView(column);setContentView(scroll);
        root=column;body=column;
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(0);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout words=new LinearLayout(this);words.setOrientation(1);
        TextView name=txt("FAR COMPETICIONES",22,Color.WHITE);name.setTypeface(null,Typeface.BOLD);
        words.addView(name);
        TextView tagline=txt("Rugby andaluz",14,accent);words.addView(tagline);
        heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        // Escudo FAR transparente sobre el fondo oscuro.
        ImageView crest=new ImageView(this);
        crest.setImageBitmap(farLogoHighContrast());
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setContentDescription("Escudo Federación Andaluza de Rugby");
        LinearLayout.LayoutParams crestParams=new LinearLayout.LayoutParams(dp(74),dp(74));
        crestParams.setMargins(dp(6),0,dp(6),0);heading.addView(crest,crestParams);
        column.addView(heading);
        ImageView hero=new ImageView(this);
        hero.setImageResource(R.drawable.cabecera_rugby);
        hero.setScaleType(ImageView.ScaleType.FIT_XY);
        hero.setContentDescription("Cabecera de rugby");
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,dp(145));
        heroLp.setMargins(0,dp(2),0,dp(3));column.addView(hero,heroLp);
        TextView prompt=txt("¿Qué quieres consultar?",20,Color.WHITE);prompt.setTypeface(null,Typeface.BOLD);
        column.addView(prompt);
        LinearLayout first=new LinearLayout(this);first.setOrientation(0);
        first.addView(homeTile("trophy","Competiciones","Calendarios, resultados y actas",0xff40101b,0xff270d18,0xffff3d55,()->competitionsHome()),new LinearLayout.LayoutParams(0,dp(196),1));
        first.addView(homeTile("book","Sistemas de competición","Fases y normas oficiales",0xff063c30,0xff09271f,0xff23df89,()->M14RulesActivity.open(this)),new LinearLayout.LayoutParams(0,dp(196),1));
        column.addView(first);
        LinearLayout second=new LinearLayout(this);second.setOrientation(0);
        second.addView(homeTile("globe","Portales oficiales","MatchReady y FAR",0xff063c30,0xff09271f,0xff23df89,()->officialPortals()),new LinearLayout.LayoutParams(0,dp(196),1));
        second.addView(homeTile("gear","Ajustes","Temporadas y circulares",0xff40101b,0xff270d18,0xffff3d55,()->settingsHome()),new LinearLayout.LayoutParams(0,dp(196),1));
        column.addView(second);
        TextView note=txt("ⓘ  Datos oficiales · Normas revisadas antes de aplicarse",12,0xffb5c5d1);
        note.setBackground(gradient(0xff173a35,0xff102a2e,0xff2d6457));
        LinearLayout.LayoutParams noteLp=new LinearLayout.LayoutParams(-1,-2);
        noteLp.setMargins(dp(4),dp(14),dp(4),0);column.addView(note,noteLp);
    }
    final class FarMark extends View {
        final Paint p=new Paint(3);
        FarMark(android.content.Context context){super(context);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setColor(0xff00bc6c);
            c.drawOval(w*.08f,h*.20f,w*.92f,h*.78f,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            p.setTextSize(h*.38f);p.setTextAlign(Paint.Align.CENTER);
            c.drawText("FAR",w*.5f,h*.60f,p);
            p.setTextSize(h*.10f);c.drawText("RUGBY ANDALUZ",w*.5f,h*.92f,p);
        }
    }
    final class RugbyHeader extends View {
        final Paint p=new Paint(3);
        RugbyHeader(android.content.Context context){super(context);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();
            p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff0b1829,0xff123e3e,0xff091725},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            Path red=new Path();red.moveTo(0,h*.35f);red.cubicTo(w*.24f,h*.04f,w*.45f,h*.05f,w*.66f,h*.15f);
            red.lineTo(w*.66f,h*.24f);red.cubicTo(w*.43f,h*.16f,w*.23f,h*.16f,0,h*.55f);red.close();
            p.setColor(0xffb3122c);c.drawPath(red,p);
            Path green=new Path();green.moveTo(0,h*.55f);green.cubicTo(w*.24f,h*.16f,w*.43f,h*.16f,w*.66f,h*.24f);
            green.lineTo(w*.66f,h*.30f);green.cubicTo(w*.43f,h*.25f,w*.22f,h*.27f,0,h*.69f);green.close();
            p.setColor(0xff00684c);c.drawPath(green,p);
            p.setColor(0xff0a2630);
            // Siluetas estilizadas de una melé, independientes del contenido de la aplicación.
            for(int i=0;i<7;i++){
                float x=w*(.25f+i*.065f),y=h*(.58f+(i%2)*.025f);
                c.drawCircle(x,y-h*.15f,h*.048f,p);
                c.drawOval(x-w*.052f,y-h*.11f,x+w*.058f,y+h*.035f,p);
                p.setStrokeWidth(dp(5));c.drawLine(x-w*.01f,y,x-w*.055f,y+h*.12f,p);
                c.drawLine(x+w*.01f,y,x+w*.065f,y+h*.11f,p);
            }
            p.setColor(0xff258d3f);c.drawRect(0,h*.78f,w,h,p);
            p.setColor(0xff0d512b);
            for(int i=0;i<36;i++){float x=i*w/35f;c.drawLine(x,h*.82f,x+dp(2),h*.72f+(i%4)*dp(2),p);}
            c.save();c.rotate(-23,w*.82f,h*.56f);
            p.setColor(0xffd8e3df);c.drawOval(w*.70f,h*.38f,w*.95f,h*.74f,p);
            p.setColor(0xff11684a);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));
            c.drawOval(w*.72f,h*.40f,w*.93f,h*.72f,p);p.setStyle(Paint.Style.FILL);c.restore();
        }
    }
    final class TileIcon extends View {
        final String kind;final Paint p=new Paint(3);
        TileIcon(android.content.Context c,String k){super(c);kind=k;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);Canvas c=canvas;float w=getWidth(),h=getHeight();
            c.save();c.translate(w*.5f,h*.48f);float z=Math.min(w,h)/100f;c.scale(z,z);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xff26323a);p.setShadowLayer(9,5,7,0xff020609);
            drawIcon(c,p,kind);
            p.clearShadowLayer();c.translate(-2,-3);p.setColor(0xfff4fffc);p.setStrokeWidth(5);drawIcon(c,p,kind);
            p.setColor(0xff8de8d0);p.setStrokeWidth(1.6f);c.translate(-1,-1);drawIcon(c,p,kind);
            c.restore();p.setStyle(Paint.Style.FILL);
        }
        void drawIcon(Canvas c,Paint p,String kind){
            if(kind.equals("trophy")){
                c.drawRoundRect(new RectF(-25,-35,25,9),4,4,p);
                c.drawArc(new RectF(-43,-30,-14,5),70,230,false,p);
                c.drawArc(new RectF(14,-30,43,5),-120,230,false,p);
                c.drawLine(0,9,0,31,p);c.drawLine(-17,32,17,32,p);c.drawLine(-24,39,24,39,p);
            }else if(kind.equals("book")){
                Path a=new Path();a.moveTo(0,-27);a.cubicTo(-13,-37,-29,-33,-39,-26);
                a.lineTo(-39,31);a.cubicTo(-22,24,-12,26,0,34);
                a.cubicTo(12,26,22,24,39,31);a.lineTo(39,-26);
                a.cubicTo(29,-33,13,-37,0,-27);a.lineTo(0,34);c.drawPath(a,p);
            }else if(kind.equals("globe")){
                c.drawCircle(0,0,36,p);c.drawOval(new RectF(-17,-36,17,36),p);
                c.drawLine(-35,0,35,0,p);c.drawArc(new RectF(-32,-19,32,19),0,180,false,p);
                c.drawArc(new RectF(-32,-19,32,19),180,180,false,p);
            }else{
                Path gear=new Path();
                for(int i=0;i<32;i++){
                    double a=(i*2*Math.PI/32)-Math.PI/2;
                    int j=i%4;float r=(j==0||j==3)?42:31;
                    float x=(float)(Math.cos(a)*r),y=(float)(Math.sin(a)*r);
                    if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);
                }
                gear.close();
                p.setStyle(Paint.Style.FILL);c.drawPath(gear,p);
                p.setColor(0xff18242d);c.drawCircle(0,0,17,p);
                p.setColor(0xffb5d4d3);c.drawCircle(0,0,11,p);
                p.setColor(0xff18242d);c.drawCircle(0,0,7,p);
                p.setStyle(Paint.Style.STROKE);
            }
        }
    }
    LinearLayout homeTile(String symbol,String heading,String detail,int top,int bottom,int outline,Runnable click){
        // Tarjetas originales de la maqueta: incluyen brillos, iconos 3D y tipografia.
        int imageRes=symbol.equals("trophy")?R.drawable.tarjeta_competiciones:
            symbol.equals("book")?R.drawable.tarjeta_sistemas:
            symbol.equals("globe")?R.drawable.tarjeta_portales:R.drawable.tarjeta_ajustes;
        ImageView artwork=new ImageView(this);
        artwork.setImageResource(imageRes);
        artwork.setScaleType(ImageView.ScaleType.FIT_XY);
        artwork.setAdjustViewBounds(false);
        artwork.setContentDescription(heading+": "+detail);
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setOrientation(1);
        LinearLayout.LayoutParams artLp=new LinearLayout.LayoutParams(-1,-1);
        artLp.setMargins(dp(3),dp(3),dp(3),dp(3));
        wrapper.addView(artwork,artLp);
        wrapper.setOnClickListener(v->click.run());
        artwork.setOnClickListener(v->click.run());
        return wrapper;
    }
    void dashboardCard(String heading,String description,Runnable click){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(12,12,12,12);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(panel);shape.setCornerRadius(18*getResources().getDisplayMetrics().density);
        card.setBackground(shape);
        TextView title=txt(heading,17,Color.WHITE);title.setTypeface(null,Typeface.BOLD);card.addView(title);
        card.addView(txt(description,13,0xffb5c5d1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,7,0,7);body.addView(card,lp);
        card.setOnClickListener(v->click.run());
    }
    void settingsHome(){
        page="settings";layout("Ajustes");
        body.addView(txt("Configuración de FAR Competiciones",14,accent));
        dashboardCard("📅  GESTIÓN DE TEMPORADAS","Crear temporadas y organizar sus categorías",()->openCirculars());
        dashboardCard("📁  CIRCULARES Y REGLAMENTOS","Añadir y consultar documentos oficiales por temporada",()->openCirculars());
        body.addView(txt("Las circulares registradas no modifican las clasificaciones hasta revisar y aprobar sus reglas.",12,Color.LTGRAY));
        button("‹ Inicio",()->home());
    }
    void openCirculars(){
        Intent i=new Intent(this,CircularsActivity.class);
        i.putExtra("season",season);i.putExtra("category",category);startActivity(i);
    }
    void officialPortals(){
        page="portals";layout("Portales oficiales");
        dashboardCard("🌐  MatchReady","Calendarios y clasificaciones de la FAR",()->openBrowser("https://farugby.matchready.es/"));
        dashboardCard("🌐  Federación Andaluza","Web oficial FAR",()->openExternal("https://farugby.com/"));
        String last=getPreferences(0).getString("last_url","");
        if(last.startsWith("https://farugby.matchready.es/")||last.startsWith("https://farugby.com/"))
            dashboardCard("↻  Última consulta","Continuar donde lo dejaste",()->openBrowser(last));
        button("‹ Inicio",()->home());
    }
    LinearLayout competitionListContainer;
    TextView competitionLabel(String value,int size,boolean bold){
        TextView t=txt(value,size,Color.WHITE);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    LinearLayout competitionTile(int top,int bottom,int outline,String icon,String name,Runnable action){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12),dp(9),dp(12),dp(9));
        tile.setBackground(gradient(top,bottom,outline));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(dp(2),dp(4),dp(2),dp(4));
        competitionListContainer.addView(tile,params);
        TextView mark=competitionLabel(icon,22,false);mark.setPadding(dp(4),0,dp(10),0);tile.addView(mark);
        TextView label=competitionLabel(name,15,true);label.setPadding(0,dp(4),0,dp(4));
        tile.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=competitionLabel("›",25,false);arrow.setPadding(dp(6),0,dp(2),0);tile.addView(arrow);
        tile.setOnClickListener(v->action.run());
        return tile;
    }
    void competitionSelector(String caption,String chosen,String icon,boolean red,Runnable click){
        int top=red?0xff550e19:0xff073e2b,bottom=red?0xff370a14:0xff082e23;
        LinearLayout tile=competitionTile(top,bottom,red?0xffa94451:0xff428b67,icon,caption+"  ·  "+chosen,click);
        tile.setMinimumHeight(dp(58));
    }
    void competitionsHome(){
        page="home";layout("Competiciones");
        // Navegación discreta junto al título, sin ocupar una fila central.
        TextView heading=(TextView)root.getChildAt(0);
        heading.setText("‹   Competiciones");
        heading.setOnClickListener(v->home());
        body.addView(txt("Selecciona temporada y categoría",15,accent));
        competitionListContainer=new LinearLayout(this);
        competitionListContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(competitionListContainer);
        list();
    }
    void list(){
        if(competitionListContainer==null)return;
        competitionListContainer.removeAllViews();
        String[] seasons={"2026/27","2025/26"};
        competitionSelector("Temporada",season,"▦",true,()->{
            new android.app.AlertDialog.Builder(this).setTitle("Temporada")
                .setSingleChoiceItems(seasons,season.equals("2025/26")?1:0,(dialog,which)->{
                    season=seasons[which];dialog.dismiss();list();
                }).setNegativeButton("Cancelar",null).show();
        });
        String[] cats={"M14","M16","M18","Sénior masculino","Sénior femenino","Otras categorías"};
        competitionSelector("Categoría",category,"♟",false,()->{
            int selected=0;for(int i=0;i<cats.length;i++)if(cats[i].equals(category))selected=i;
            new android.app.AlertDialog.Builder(this).setTitle("Categoría")
                .setSingleChoiceItems(cats,selected,(dialog,which)->{
                    category=cats[which];dialog.dismiss();list();
                }).setNegativeButton("Cancelar",null).show();
        });
        LinearLayout sectionRow=new LinearLayout(this);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionLp=new LinearLayout.LayoutParams(-1,-2);
        sectionLp.setMargins(0,dp(8),0,dp(5));
        competitionListContainer.addView(sectionRow,sectionLp);
        TextView section=txt("Competiciones · "+category+" · "+season,19,accent);
        section.setTypeface(null,Typeface.BOLD);
        sectionRow.addView(section,new LinearLayout.LayoutParams(0,-2,1));
        View stripes=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path path=new Path();
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                float w=getWidth(),h=getHeight();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(2.3f));
                path.reset();path.moveTo(w*.07f,h*.65f);
                path.cubicTo(w*.34f,h*.30f,w*.62f,h*.20f,w*.94f,h*.18f);
                paint.setColor(0xffcf263d);canvas.drawPath(path,paint);
                paint.setStrokeWidth(dp(1.8f));
                path.reset();path.moveTo(w*.17f,h*.88f);
                path.cubicTo(w*.46f,h*.55f,w*.73f,h*.47f,w*.97f,h*.46f);
                paint.setColor(0xff168a52);canvas.drawPath(path,paint);
            }
        };
        sectionRow.addView(stripes,new LinearLayout.LayoutParams(dp(61),dp(27)));
        String[] entries;
        if(category.equals("M14")&&season.equals("2026/27"))entries=new String[]{"Primera División","Segunda División · Grupo 1","Segunda División · Grupo 2","Segunda División A · 2.ª fase","Segunda División B · 2.ª fase","Copa FAR"};
        else if(category.equals("M14")&&season.equals("2025/26"))entries=new String[]{"Primera División","Segunda División A · 2.ª fase","Segunda División B · 2.ª fase"};
        else entries=category.equals("M14")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:category.equals("M16")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:category.equals("M18")?new String[]{"Primera División","Segunda División · Grupos","Copa FAR"}:new String[]{"Consultar competiciones oficiales"};
        for(int i=0;i<entries.length;i++){
            final String entry=entries[i];
            boolean red=i%2==0;
            competitionTile(red?0xff510b15:0xff063c29,red?0xff350912:0xff082d21,
                red?0xff9e3d4a:0xff3c8860,"♜",entry,()->details(entry));
        }
        if(category.equals("M14")&&season.equals("2026/27")){
            competitionTile(0xff0b4830,0xff092c23,0xff4b9a73,"▤",
                "Sistema de competición · M14 2026/27",()->M14RulesActivity.open(this));
        }
        TextView note=txt(category.equals("M14")&&season.equals("2026/27")?
            "ⓘ  Competiciones contrastadas con la web oficial FAR.":
            "ⓘ  Estructura orientativa: verificar divisiones y grupos para esta temporada.",12,0xffa5c6b9);
        LinearLayout.LayoutParams noteLp=new LinearLayout.LayoutParams(-1,-2);
        noteLp.setMargins(0,dp(12),0,dp(6));
        competitionListContainer.addView(note,noteLp);
    }
    String officialCalendar(){
        if(!category.equals("M14"))return "";
        if(season.equals("2025/26")){
            if(selectedCompetition.equals("Primera División"))return "https://farugby.matchready.es/es/public/calendar/33/11/combined/";
            if(selectedCompetition.equals("Segunda División A · 2.ª fase"))return "https://farugby.matchready.es/es/public/calendar/34/147/combined/";
            if(selectedCompetition.equals("Segunda División B · 2.ª fase"))return "https://farugby.matchready.es/es/public/calendar/34/148/combined/";
            return "";
        }
        if(!season.equals("2026/27"))return "";
        if(selectedCompetition.equals("Primera División"))return "https://farugby.matchready.es/es/public/calendar/35/11/combined/";
        if(selectedCompetition.equals("Segunda División · Grupo 1"))return "https://farugby.matchready.es/es/public/calendar/35/12/combined/";
        if(selectedCompetition.equals("Segunda División · Grupo 2"))return "https://farugby.matchready.es/es/public/calendar/35/15/combined/";
        if(selectedCompetition.equals("Copa FAR"))return "https://farugby.matchready.es/es/public/calendar/35/83/combined/";
        if(selectedCompetition.contains("2.ª fase"))return getPreferences(0).getString("url_"+season+"_"+category+"_"+selectedCompetition,"").trim();
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

            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);body.addView(actions);
            Button refresh=new Button(this);refresh.setText("↻ Actualizar");refresh.setTextSize(12);refresh.setAllCaps(false);
            actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));refresh.setOnClickListener(v->refreshNative(official));
            Button source=new Button(this);source.setText("Web oficial");source.setTextSize(12);source.setAllCaps(false);
            actions.addView(source,new LinearLayout.LayoutParams(0,-2,1));source.setOnClickListener(v->openBrowser(official));
            showCachedNative();
            // Al abrir la clasificación M14, comprobar periódicamente si MatchReady ha publicado una fase nueva.
            // El intervalo evita descargas continuas al cambiar de pestaña y al terminar una importación.
            if(tab.equals("Clasificación")&&category.equals("M14")&&(season.equals("2026/27")||season.equals("2025/26"))){
                long now=System.currentTimeMillis();
                long last=getPreferences(0).getLong("checked_"+cacheKey(),0L);
                if(now-last>=6L*60L*60L*1000L&&now-lastAutomaticRefreshAttempt>=60L*1000L){
                    lastAutomaticRefreshAttempt=now;
                    refreshNative(official);
                }
            }
        }else{
            body.addView(txt("Todavía no hay un calendario oficial configurado para esta competición.",14,Color.LTGRAY));
            String key="url_"+season+"_"+category+"_"+selectedCompetition;
            EditText url=new EditText(this);url.setSingleLine(true);url.setHint("Enlace público MatchReady");url.setText(getPreferences(0).getString(key,""));body.addView(url);
            button("Guardar y abrir",()->{String link=url.getText().toString().trim();if(link.startsWith("https://farugby.matchready.es/")){getPreferences(0).edit().putString(key,link).apply();openBrowser(link);}else Toast.makeText(this,"Enlace MatchReady no válido",Toast.LENGTH_LONG).show();});
        }
        button("‹ Competiciones",()->competitionsHome());
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
    String normalizedTeamKey(String name){
        return java.text.Normalizer.normalize(name==null?"":name.toLowerCase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]","")
            .replaceAll("[^a-z0-9]","").trim();
    }
    String crestFromMatches(org.json.JSONObject data,String team){
        String key=normalizedTeamKey(team);
        if(key.isEmpty())return "";
        org.json.JSONArray tables=data.optJSONArray("tables");
        if(tables==null)return "";
        for(int i=0;i<tables.length();i++){
            org.json.JSONObject table=tables.optJSONObject(i);
            if(table==null||!"matches".equals(table.optString("kind")))continue;
            org.json.JSONArray rows=table.optJSONArray("rows");
            if(rows==null)continue;
            for(int j=0;j<rows.length();j++){
                org.json.JSONObject row=rows.optJSONObject(j);
                if(row==null)continue;
                if(key.equals(normalizedTeamKey(row.optString("home","")))){
                    String crest=row.optString("homeCrest","");
                    if(crest.startsWith("https://"))return crest;
                }
                if(key.equals(normalizedTeamKey(row.optString("away","")))){
                    String crest=row.optString("awayCrest","");
                    if(crest.startsWith("https://"))return crest;
                }
            }
        }
        return "";
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
    // Indicadores orientativos: nunca equivalen a una clasificación definitiva ni a una plaza adjudicada.
    // Solo etiquetas oficiales explícitas: una mezcla de fases no permite deducir cuál representa la tabla.
    int m14StandingPhase(org.json.JSONObject data,org.json.JSONObject standing){
        String competition=selectedCompetition.toLowerCase(java.util.Locale.ROOT);
        if(competition.contains("segunda división a")||competition.contains("segunda división b")||
           competition.contains("segunda a")||competition.contains("segunda b"))return 2;
        String heading=standing.optString("heading","").toLowerCase(java.util.Locale.ROOT);
        if(heading.matches("(?s).*(?:2[.ªº ]*fase|segunda fase).*"))return 2;
        if(heading.matches("(?s).*(?:1[.ªº ]*fase|primera fase).*"))return 1;
        // La clasificación acumulada de Primera no permite inferir la fase por fechas o partidos jugados.
        org.json.JSONArray all=data.optJSONArray("tables");
        boolean first=false,second=false;
        if(all!=null)for(int i=0;i<all.length();i++){
            org.json.JSONObject t=all.optJSONObject(i);
            if(t==null||!"matches".equals(t.optString("kind")))continue;
            String h=t.optString("heading","").toLowerCase(java.util.Locale.ROOT);
            if(h.matches("(?s).*(?:1[.ªº ]*fase|primera fase).*"))first=true;
            if(h.matches("(?s).*(?:2[.ªº ]*fase|segunda fase).*"))second=true;
        }
        if(first&&!second)return 1;
        if(second&&!first)return 2;
        // Si ambas fases están presentes y la tabla no identifica su fase,
        // no atribuir los puestos a ninguna de ellas.
        return 0;
    }
    String m14PositionGuide(int rank){
        if(!category.equals("M14")||!(season.equals("2026/27")||season.equals("2025/26")))return "";
        if(selectedCompetition.equals("Primera División")){
            if(rank>=1&&rank<=5)return "Referencia 1.ª fase: permanencia en Primera";
            if(rank==6)return "Referencia 1.ª fase: triangular de promoción";
        }
        if(selectedCompetition.startsWith("Segunda División · Grupo")){
            if(rank==1)return "Referencia 1.ª fase: triangular de promoción";
            if(rank==2||rank==3)return "Referencia 1.ª fase: Segunda A";
            if(rank>=4&&rank<=6)return "Referencia 1.ª fase: Segunda B";
        }
        return "";
    }
    String m14SecondPhaseGuide(int rank){
        String competition=selectedCompetition.toLowerCase(java.util.Locale.ROOT);
        if(competition.contains("primera división")&&rank==6)return "promoción final";
        if((competition.contains("segunda división a")||competition.contains("segunda a")||competition.contains("segunda división a"))&&rank==1)return "promoción final";
        return "";
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
    LinearLayout matchTeam(String name,String crest,boolean right){
        LinearLayout layout=new LinearLayout(this);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if(right)layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if(crest.startsWith("https://")){
            ImageView logo=new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int px=(int)(getResources().getDisplayMetrics().density*27);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px,px);
            lp.setMargins(3,0,5,0);layout.addView(logo,lp);
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
        TextView text=txt(name,12,Color.WHITE);
        text.setMaxLines(3);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setGravity((right?android.view.Gravity.RIGHT:android.view.Gravity.LEFT)|android.view.Gravity.CENTER_VERTICAL);
        layout.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        return layout;
    }
    String formatMatchDate(String value){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})(?:\\s+(\\d{1,2}:\\d{2}))?").matcher(value);
        if(!m.find())return value;
        try{
            java.text.SimpleDateFormat input=new java.text.SimpleDateFormat("dd/MM/yyyy",new java.util.Locale("es","ES"));
            input.setLenient(false);
            java.util.Date date=input.parse(m.group(1)+"/"+m.group(2)+"/"+m.group(3));
            java.text.SimpleDateFormat output=new java.text.SimpleDateFormat("EEEE d 'de' MMMM",new java.util.Locale("es","ES"));
            String formatted=output.format(date);
            return Character.toUpperCase(formatted.charAt(0))+formatted.substring(1)+(m.group(4)==null?"":" · "+m.group(4)+" h");
        }catch(Exception ignored){return value;}
    }
    String matchDayLabel(String value){
        String formatted=formatMatchDate(value);
        return formatted.replaceFirst(" · \\d{1,2}:\\d{2} h$","");
    }
    String matchHour(String value){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b").matcher(value);
        return m.find()?m.group(1):"";
    }
    String shortRoundLabel(String heading){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)Jornada\\s+([0-9]+(?:\\.[0-9]+)?)").matcher(heading);
        return m.find()?"Jornada "+m.group(1):heading;
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
    String teamKey(String name){
        if(name==null)return "";
        String normalized=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return normalized.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
    }
    String matchCrest(org.json.JSONArray tables,String name,String direct){
        if(direct!=null&&direct.startsWith("https://"))return direct;
        String key=teamKey(name);
        if(key.isEmpty())return "";
        for(int t=0;t<tables.length();t++){
            org.json.JSONObject group=tables.optJSONObject(t);
            if(group==null||!"standing".equals(group.optString("kind")))continue;
            org.json.JSONArray rows=group.optJSONArray("rows");if(rows==null)continue;
            for(int r=0;r<rows.length();r++){
                org.json.JSONObject row=rows.optJSONObject(r);
                if(row==null)continue;
                String other=teamKey(row.optString("team",""));
                if(other.equals(key))return row.optString("crest","");
            }
        }
        return "";
    }
    void showMatchDetail(org.json.JSONObject row,String round,String organizer,String homeLogo,String awayLogo){
        LinearLayout details=new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(14,10,14,14);
        details.setBackgroundColor(bg);
        TextView teams=txt(row.optString("home","")+"  —  "+row.optString("away",""),18,Color.WHITE);
        teams.setTypeface(null,Typeface.BOLD);details.addView(teams);
        String score=row.optString("score","");
        if(isScore(score))details.addView(txt("Resultado: "+score,20,accent));
        details.addView(txt("Fecha y hora: "+formatMatchDate(row.optString("when","")),14,Color.WHITE));
        details.addView(txt("Jornada: "+shortRoundLabel(round),14,Color.WHITE));
        if(!organizer.isEmpty())details.addView(txt("Organiza: "+organizer,14,Color.LTGRAY));
        String extra=row.optString("extra","").trim();
        if(!extra.isEmpty())details.addView(txt(extra,13,Color.LTGRAY));
        String statisticsUrl=row.optString("statsUrl","");
        if(statisticsUrl.startsWith("https://farugby.matchready.es/")){
            Button loadActa=new Button(this);
            loadActa.setText("Consultar acta y estadísticas");
            details.addView(loadActa);
            loadActa.setOnClickListener(v->loadOfficialActa(statisticsUrl,details,loadActa));
        }
        org.json.JSONObject act=row.optJSONObject("acta");
        if(act!=null){
            addActaSection(details,"Titulares · Local",act.optJSONArray("titularesLocal"));
            addActaSection(details,"Suplentes · Local",act.optJSONArray("suplentesLocal"));
            addActaSection(details,"Titulares · Visitante",act.optJSONArray("titularesVisitante"));
            addActaSection(details,"Suplentes · Visitante",act.optJSONArray("suplentesVisitante"));
            addActaSection(details,"Ensayos",act.optJSONArray("ensayos"));
            addActaSection(details,"Transformaciones",act.optJSONArray("transformaciones"));
            addActaSection(details,"Golpes de castigo",act.optJSONArray("golpes"));
            addActaSection(details,"Tarjetas",act.optJSONArray("tarjetas"));
            addActaSection(details,"Sustituciones e incidencias",act.optJSONArray("incidencias"));
        }else{
            details.addView(txt("Acta oficial pendiente de importar. Los datos de jugadores y anotaciones se mostrarán aquí cuando se puedan leer de MatchReady.",13,Color.LTGRAY));
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(details);
        new android.app.AlertDialog.Builder(this).setTitle("Ficha del partido").setView(scroll).setPositiveButton("Cerrar",null).show();
    }
    void loadOfficialActa(String url,LinearLayout parent,Button button){
        button.setEnabled(false);button.setText("Cargando datos oficiales…");
        WebView actView=new WebView(this);
        actView.getSettings().setJavaScriptEnabled(true);
        actView.getSettings().setDomStorageEnabled(true);
        actView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String loaded){
                if(!loaded.startsWith("https://farugby.matchready.es/"))return;
                String js="(function(){try{var norm=function(x){return (x||'').replace(/\\s+/g,' ').trim()};"+
                    "var sections=Array.from(document.querySelectorAll('table')).map(function(t){"+
                    "var prev=t.previousElementSibling,heading='';for(var i=0;i<5&&prev;i++,prev=prev.previousElementSibling){"+
                    "if(/H[1-6]/.test(prev.tagName)){heading=norm(prev.innerText);break;}"+
                    "if(prev.querySelector){var h=prev.querySelector('h1,h2,h3,h4,h5,h6');if(h){heading=norm(h.innerText);break;}}}"+
                    "var rows=Array.from(t.querySelectorAll('tr')).map(function(r){return norm(r.innerText)}).filter(Boolean);"+
                    "return {heading:heading||'Datos oficiales',rows:rows};}).filter(function(x){return x.rows.length>0});"+
                    "return JSON.stringify({sections:sections});}catch(e){return JSON.stringify({error:String(e)})}})()";
                view.evaluateJavascript(js,result->{
                    try{
                        String decoded=new org.json.JSONTokener(result).nextValue().toString();
                        org.json.JSONObject data=new org.json.JSONObject(decoded);
                        org.json.JSONArray sections=data.optJSONArray("sections");
                        if(sections==null||sections.length()==0){
                            button.setText("No se encontraron datos del acta");return;
                        }
                        button.setVisibility(View.GONE);
                        for(int i=0;i<sections.length();i++){
                            org.json.JSONObject section=sections.optJSONObject(i);if(section==null)continue;
                            addActaSection(parent,section.optString("heading","Datos oficiales"),section.optJSONArray("rows"));
                        }
                    }catch(Exception ex){button.setText("No se pudo interpretar el acta");}
                    finally{view.destroy();}
                });
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){
                if(request.isForMainFrame()){button.setText("No se pudo cargar el acta");button.setEnabled(true);}
            }
        });
        actView.loadUrl(url);
    }
    void addActaSection(LinearLayout parent,String heading,org.json.JSONArray items){
        if(items==null||items.length()==0)return;
        TextView title=txt(heading,16,accent);title.setTypeface(null,Typeface.BOLD);parent.addView(title);
        for(int i=0;i<items.length();i++){
            Object item=items.opt(i);
            String value=item instanceof org.json.JSONObject?((org.json.JSONObject)item).optString("texto",item.toString()):String.valueOf(item);
            parent.addView(txt(value,14,Color.WHITE));
        }
    }
    void renderNative(org.json.JSONObject data)throws org.json.JSONException{
        org.json.JSONArray tables=data.optJSONArray("tables");if(tables==null)return;
        int count=0;
        if(!selectedTab.equals("Clasificación")){
            java.util.ArrayList<Integer> groupIds=new java.util.ArrayList<>();
            java.util.ArrayList<String> groupNames=new java.util.ArrayList<>();
            for(int t=0;t<tables.length();t++){
                org.json.JSONObject group=tables.optJSONObject(t);
                if(group!=null&&group.optString("kind").equals("matches")&&group.optJSONArray("rows")!=null&&group.optJSONArray("rows").length()>0){
                    boolean any=false;
                    for(int n=0;n<group.optJSONArray("rows").length();n++){org.json.JSONObject row=group.optJSONArray("rows").optJSONObject(n);if(row!=null&&matchVisible(row)){any=true;break;}}
                    if(!any)continue;
                    if(selectedTab.equals("Resultados")){groupIds.add(0,t);groupNames.add(0,shortRoundLabel(group.optString("heading","Jornada "+(t+1))));}else{groupIds.add(t);groupNames.add(shortRoundLabel(group.optString("heading","Jornada "+(t+1))));}
                }
            }
            if(!groupIds.isEmpty()){
                if(selectedMatchGroup>=groupIds.size())selectedMatchGroup=-1;
                Spinner picker=new Spinner(this);
                java.util.ArrayList<String> options=new java.util.ArrayList<>();
                options.add(selectedTab.equals("Calendario")&&calendarUpcomingOnly?"Próximas jornadas":selectedTab.equals("Resultados")?"Todos los resultados":"Todas las jornadas");
                options.addAll(groupNames);
                if(selectedTab.equals("Calendario"))options.add(calendarUpcomingOnly?"Ver calendario completo":"Ver próximos partidos");
                ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
                picker.setAdapter(adapter);picker.setSelection(selectedMatchGroup+1);LinearLayout.LayoutParams pickerParams=new LinearLayout.LayoutParams(-1,(int)(42*getResources().getDisplayMetrics().density));body.addView(picker,pickerParams);
                picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                    public void onNothingSelected(AdapterView<?> p){}
                    public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                        if(selectedTab.equals("Calendario")&&position==options.size()-1){
                            calendarUpcomingOnly=!calendarUpcomingOnly;
                            selectedMatchGroup=-1;
                            showCompetitionTab("Calendario");
                            return;
                        }
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
                boolean m14Legend=category.equals("M14")&&(season.equals("2026/27")||season.equals("2025/26"))&&!selectedCompetition.equals("Copa FAR");
                int detectedPhase=m14Legend?m14StandingPhase(data,table):0;
                if(m14Legend)body.addView(txt(detectedPhase==0?"Fase sin confirmar · colores de puestos desactivados":detectedPhase==1?"1.ª fase · puestos orientativos":"2.ª fase · puestos orientativos",12,detectedPhase==0?Color.LTGRAY:accent));
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
                    int rank=-1;
                    try{rank=Integer.parseInt(row.optString("position","").replaceAll("[^0-9]",""));}catch(NumberFormatException ignored){}
                    int positionColor=Color.WHITE;
                    if(m14Legend&&detectedPhase!=0){
                        String positionGuide=detectedPhase==1?m14PositionGuide(rank):m14SecondPhaseGuide(rank);
                        if(!positionGuide.isEmpty())positionColor=(positionGuide.contains("triangular")||positionGuide.contains("promoción final"))?0xffffca65:
                            positionGuide.contains("Segunda B")?0xff9db0c0:0xff66e1a4;
                    }
                    addStandingFixed(line,vals[0],unit*3/5,positionColor);
                    String crest=row.optString("crest","");
                    if(!crest.startsWith("https://"))crest=crestFromMatches(data,vals[1]);
                    addStandingTeam(line,vals[1],crest,(int)(getResources().getDisplayMetrics().density*168));
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
                if(m14Legend){
                    body.addView(txt("LEYENDA · PUESTOS ORIENTATIVOS",13,accent));
                    if(detectedPhase==1&&selectedCompetition.equals("Primera División")){
                        body.addView(txt("● Verde (1.º–5.º): acceso previsto a Primera División en la segunda fase.",12,0xff66e1a4));
                        body.addView(txt("● Amarillo (6.º): triangular de promoción.",12,0xffffca65));
                    }else if(detectedPhase==1&&selectedCompetition.startsWith("Segunda División · Grupo")){
                        body.addView(txt("● Amarillo (1.º): triangular de promoción.",12,0xffffca65));
                        body.addView(txt("● Verde (2.º–3.º): acceso previsto a Segunda División A.",12,0xff66e1a4));
                        body.addView(txt("● Gris (4.º–6.º): acceso previsto a Segunda División B.",12,0xff9db0c0));
                    }else if(detectedPhase==2){
                        if(selectedCompetition.equals("Primera División"))body.addView(txt("● Amarillo (6.º): promoción final frente al primero de Segunda A.",12,0xffffca65));
                        else if((selectedCompetition.toLowerCase(java.util.Locale.ROOT).contains("segunda a")||selectedCompetition.toLowerCase(java.util.Locale.ROOT).contains("segunda división a")))body.addView(txt("● Amarillo (1.º): promoción final frente al sexto de Primera.",12,0xffffca65));
                        else body.addView(txt("Sin colores de promoción para este grupo.",12,Color.LTGRAY));
                    }else{
                        body.addView(txt("No se puede confirmar la fase a partir de las etiquetas oficiales disponibles. Actualiza los datos para volver a comprobarlo.",12,Color.LTGRAY));
                    }
                    body.addView(txt("Indicaciones provisionales. La FAR puede aplicar sanciones, renuncias y resoluciones que modifiquen los puestos.",11,Color.LTGRAY));
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
                boolean roundAlreadyInSelector=selectedMatchGroup>=0;
                String roundTitle=number.isEmpty()?shortRoundLabel(full):"Jornada "+number;
                String sectionTitle=roundAlreadyInSelector?phase:roundTitle+(phase.isEmpty()?"":" · "+phase);
                if(!sectionTitle.isEmpty()){
                    TextView head=txt(sectionTitle,roundAlreadyInSelector?12:15,accent);
                    head.setTypeface(null,Typeface.BOLD);
                    head.setPadding(0,2,0,0);
                    body.addView(head);
                }
                if(organizer>=0)body.addView(txt("Organiza: "+full.substring(organizer+"organizador:".length()).replaceFirst("^\\s*[:\\-]\\s*","").trim(),12,Color.LTGRAY));
                String lastDate="";
                for(int j=0;j<rows.length();j++){
                    org.json.JSONObject row=rows.optJSONObject(j);if(row==null)continue;
                    String score=row.optString("score","");
                    if(!matchVisible(row))continue;
                    String dateLabel=matchDayLabel(row.optString("when",""));
                    if(!dateLabel.equals(lastDate)){
                        TextView dateView=txt(dateLabel,12,accent);
                        dateView.setTypeface(null,Typeface.BOLD);
                        dateView.setPadding(5,2,0,2);
                        body.addView(dateView);
                        lastDate=dateLabel;
                    }
                    LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(8,5,8,5);card.setBackgroundColor(panel);
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,1,0,2);body.addView(card,cp);
                    LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(line);
                    LinearLayout left=matchTeam(row.optString("home",""),matchCrest(tables,row.optString("home",""),row.optString("homeCrest","")),false);
                    boolean finished=selectedTab.equals("Resultados")&&isScore(score);
                    TextView mid=txt(selectedTab.equals("Calendario")&&isUpcomingMatch(row)?"VS":isScore(score)?score:"VS",finished?20:19,finished?Color.WHITE:selectedTab.equals("Calendario")&&!isUpcomingMatch(row)?Color.LTGRAY:accent);
                    mid.setTypeface(null,Typeface.BOLD);mid.setGravity(android.view.Gravity.CENTER);
                    mid.setMinWidth((int)(getResources().getDisplayMetrics().density*(finished?66:52)));
                    if(finished){
                        android.graphics.drawable.GradientDrawable scoreBg=new android.graphics.drawable.GradientDrawable();
                        scoreBg.setColor(Color.rgb(35,75,71));
                        scoreBg.setCornerRadius(9*getResources().getDisplayMetrics().density);
                        mid.setBackground(scoreBg);
                        mid.setPadding(5,7,5,7);
                    }
                    LinearLayout right=matchTeam(row.optString("away",""),matchCrest(tables,row.optString("away",""),row.optString("awayCrest","")),true);
                    line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
                    LinearLayout center=new LinearLayout(this);
                    center.setOrientation(LinearLayout.VERTICAL);
                    center.setGravity(android.view.Gravity.CENTER);
                    center.addView(mid,new LinearLayout.LayoutParams(-2,-2));
                    if(selectedTab.equals("Resultados")){
                        String hour=matchHour(row.optString("when",""));
                        if(!hour.isEmpty()){
                            TextView time=txt(hour+" h",10,Color.LTGRAY);
                            time.setGravity(android.view.Gravity.CENTER);
                            center.addView(time);
                        }
                    }
                    line.addView(center,new LinearLayout.LayoutParams(-2,-2));
                    line.addView(right,new LinearLayout.LayoutParams(0,-2,1));
                    String organizerText=organizer>=0?full.substring(organizer+"organizador:".length()).replaceFirst("^\\s*[:\\-]\\s*","").trim():"";
                    String homeLogo=matchCrest(tables,row.optString("home",""),row.optString("homeCrest",""));
                    String awayLogo=matchCrest(tables,row.optString("away",""),row.optString("awayCrest",""));
                    card.setOnClickListener(v->{
                        String act=row.optString("actUrl","");
                        if(act.startsWith("https://farugby.matchready.es/")){
                            Intent intent=new Intent(this,RugbyHubActivity.class);
                            intent.putExtra("far_act_url",act);
                            intent.putExtra("far_home",row.optString("home",""));
                            intent.putExtra("far_away",row.optString("away",""));
                            intent.putExtra("far_official_score",row.optString("score",""));
                            startActivity(intent);
                        }else showMatchDetail(row,full,organizerText,homeLogo,awayLogo);
                    });
                    String extra=row.optString("extra","");if(!extra.isEmpty()){TextView details=txt(extra,11,Color.LTGRAY);details.setVisibility(View.GONE);card.addView(details);}
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
                    String js="(function(){try{\nvar norm=function(s){return (s||'').trim().replace(/\\s+/g,' ')};\nvar tables=Array.from(document.querySelectorAll('table')).map(function(t){\n var tr=Array.from(t.querySelectorAll('tr'));\n var first=tr.find(function(r){return r.querySelector('th')});\n if(!first)return null;\n var h=Array.from(first.children).map(function(c){return norm(c.innerText).toLowerCase()});\n var find=function(names){return h.findIndex(function(v){return names.some(function(n){return v===n||v.indexOf(n)>=0})})};\n var home=find(['equipo local']),away=find(['equipo visitante']),score=find(['resultado']),date=find(['fecha/hora']);\n var team=find(['equipo']),played=find(['j']),points=find(['puntos','ptos','pts','punt']),position=0;\n var kind=home>=0&&away>=0&&score>=0&&date>=0?'matches':team>=0&&played>=0&&points>=0?'standing':'other';\n if(kind==='other')return null;\n if(kind==='standing'){var valid=tr.slice(tr.indexOf(first)+1).filter(function(r){var c=Array.from(r.children).filter(function(x){return x.matches('td,th')}).map(function(x){return norm(x.innerText)});return c.length>Math.max(team,played,points)&&/^\\d+$/.test(c[played])&&/^\\d+$/.test(c[points])&&c[team].length>1&&c[team].length<100});if(valid.length<3)return null;}\n var node=t,heading='';\n for(var n=0;n<8&&node;n++){node=node.previousElementSibling||node.parentElement;if(!node||node===document.body)break;if(node.matches&&node.matches('h1,h2,h3,h4,h5')){heading=norm(node.innerText);break}var x=node.querySelector&&node.querySelector('h1,h2,h3,h4,h5');if(x){heading=norm(x.innerText);break}}\n var rows=tr.slice(tr.indexOf(first)+1).map(function(r){\n var cells=Array.from(r.children).filter(function(c){return c.matches('td,th')}).map(function(c){return norm(c.innerText)});\n if(kind==='matches'){if(cells.length<=Math.max(home,away,score,date))return null;var a=cells[home],b=cells[away];if(!a||!b||a.toLowerCase()==='equipo local'||b.toLowerCase()==='equipo visitante')return null;var raw=Array.from(r.children).filter(function(c){return c.matches('td,th')});var hi=raw[home]&&raw[home].querySelector('img'),ai=raw[away]&&raw[away].querySelector('img');var links=Array.from(r.querySelectorAll('a[href]')).map(function(x){return x.href});var actAnchor=raw[6]&&raw[6].querySelector('a[href]');var statsAnchor=raw[score]&&raw[score].querySelector('a[href]');var actLink=actAnchor?actAnchor.href:'';var statsLink=statsAnchor?statsAnchor.href:'';return {actUrl:actLink,statsUrl:statsLink,when:cells[date],home:a.replace(/Árbitro:.*$/i,'').trim(),homeCrest:hi?(hi.currentSrc||hi.src||''):'',score:cells[score],away:b.replace(/Campo:.*$/i,'').trim(),awayCrest:ai?(ai.currentSrc||ai.src||''):'',extra:((a.match(/Árbitro:.*$/i)||[])[0]||'')+' '+((b.match(/Campo:.*$/i)||[])[0]||'')}}\n if(cells.length<=Math.max(team,played,points))return null;\n var stats={};h.forEach(function(label,index){if(index!==position&&index!==team&&index!==played&&index!==points&&label)stats[label]=cells[index]||''});\n var teamCell=Array.from(r.children).filter(function(c){return c.matches('td,th')})[team];var crestImg=teamCell&&teamCell.querySelector('img');var crest=crestImg?(crestImg.currentSrc||crestImg.src||''):'';return {position:cells[position],team:cells[team],played:cells[played],points:cells[points],stats:stats,crest:crest};\n }).filter(Boolean);\n return {kind:kind,heading:heading|| (kind==='matches'?'Jornada':'Clasificación'),rows:rows};\n}).filter(Boolean);\nreturn JSON.stringify({url:location.href,title:document.title,format:2,tables:tables});\n}catch(e){return JSON.stringify({error:String(e)})}})()";
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
                            getPreferences(0).edit().putString(key,obj.toString()).putLong("checked_"+key,System.currentTimeMillis()).apply();
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
