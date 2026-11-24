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
    android.graphics.drawable.Drawable lineIcon(String kind){
        android.graphics.drawable.Drawable d=new android.graphics.drawable.Drawable(){
            final Paint p=new Paint(3);
            @Override public void draw(Canvas original){
                android.graphics.Rect bounds=getBounds();
                Canvas c=original;int save=c.save();c.translate(bounds.left,bounds.top);
                c.scale(bounds.width()/24f,bounds.height()/24f);
                p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                if(kind.equals("calendar")){
                    c.drawRoundRect(3,5,21,21,2,2,p);c.drawLine(3,10,21,10,p);c.drawLine(8,3,8,7,p);c.drawLine(16,3,16,7,p);
                    for(int x=7;x<=17;x+=5)for(int y=14;y<=18;y+=4)c.drawCircle(x,y,.65f,p);
                }else if(kind.equals("results")){
                    c.drawRect(4,12,8,20,p);c.drawRect(10,7,14,20,p);c.drawRect(16,3,20,20,p);
                }else if(kind.equals("trophy")){
                    c.drawRect(8,4,16,13,p);c.drawArc(3,5,11,14,90,180,false,p);c.drawArc(13,5,21,14,270,180,false,p);
                    c.drawLine(12,13,12,19,p);c.drawLine(8,20,16,20,p);
                }else if(kind.equals("refresh")){
                    c.drawArc(4,4,20,20,215,260,false,p);c.drawLine(17,4,20,5,p);c.drawLine(20,5,20,9,p);
                    c.drawArc(4,4,20,20,35,135,false,p);c.drawLine(4,15,4,19,p);c.drawLine(4,19,8,19,p);
                }else if(kind.equals("organizer")){
                    c.drawCircle(12,7,3,p);c.drawArc(5,12,19,22,185,170,false,p);
                }else if(kind.equals("chevron")){
                    c.drawLine(5,9,12,16,p);c.drawLine(12,16,19,9,p);
                }else if(kind.equals("external")){
                    c.drawRoundRect(3,8,16,21,2,2,p);c.drawLine(11,13,21,3,p);c.drawLine(14,3,21,3,p);c.drawLine(21,3,21,10,p);
                }
                c.restoreToCount(save);
            }
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
        d.setBounds(0,0,dp(17),dp(17));return d;
    }
    CharSequence iconLabel(String kind,String label){
        android.text.SpannableString span=new android.text.SpannableString("  "+label);
        int navKind="calendar".equals(kind)?2:"results".equals(kind)?1:"trophy".equals(kind)?5:"refresh".equals(kind)?6:"external".equals(kind)?7:"organizer".equals(kind)?3:-1;
        android.graphics.drawable.Drawable icon=navKind>=0?standingNavIcon(navKind,Color.WHITE):lineIcon(kind);
        icon.setBounds(0,0,dp(17),dp(17));
        span.setSpan(new android.text.style.ImageSpan(icon,android.text.style.DynamicDrawableSpan.ALIGN_BASELINE),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
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
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(w*.02f,h*.73f);
                path.cubicTo(w*.28f,h*.28f,w*.64f,h*.08f,w*.99f,h*.13f);
                path.cubicTo(w*.62f,h*.20f,w*.38f,h*.48f,w*.16f,h*.73f);
                path.close();
                paint.setColor(0xffcf263d);
                canvas.drawPath(path,paint);
                path.reset();
                path.moveTo(w*.18f,h*.97f);
                path.cubicTo(w*.45f,h*.55f,w*.75f,h*.36f,w*.99f,h*.41f);
                path.cubicTo(w*.71f,h*.50f,w*.52f,h*.71f,w*.32f,h*.97f);
                path.close();
                paint.setColor(0xff168a52);
                canvas.drawPath(path,paint);
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
        TextView back=txt("←",25,Color.WHITE);
        back.setGravity(android.view.Gravity.CENTER);
        LinearLayout titleBar=(LinearLayout)root;
        if(titleBar.getChildCount()>0&&titleBar.getChildAt(0) instanceof TextView){
            TextView currentTitle=(TextView)titleBar.getChildAt(0);
            titleBar.removeViewAt(0);
            LinearLayout header=new LinearLayout(this);header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            header.addView(back,new LinearLayout.LayoutParams(dp(30),dp(45)));
            header.addView(currentTitle,new LinearLayout.LayoutParams(0,-2,1));
            View stripes=new View(this){
                Paint p=new Paint(3);
                @Override protected void onDraw(Canvas c){
                    super.onDraw(c);
                    float w=getWidth(),h=getHeight();
                    Path red=new Path();red.moveTo(0,h*.68f);red.quadTo(w*.54f,h*.12f,w,h*.23f);
                    red.quadTo(w*.58f,h*.24f,0,h*.86f);red.close();
                    p.setColor(0xffdf314b);c.drawPath(red,p);
                    Path green=new Path();green.moveTo(w*.13f,h*.95f);green.quadTo(w*.60f,h*.39f,w,h*.49f);
                    green.quadTo(w*.65f,h*.50f,w*.13f,h);green.close();
                    p.setColor(0xff169e62);c.drawPath(green,p);
                }
            };
            header.addView(stripes,new LinearLayout.LayoutParams(dp(65),dp(35)));
            root.addView(header,0);
            back.setOnClickListener(v->competitionsHome());
        }
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);body.addView(tabs);
        int tabIndex=0;
        for(String item:new String[]{"Calendario","Resultados","Clasificación"}){
            final boolean active=item.equals(tab);
            TextView b=txt("",11,Color.WHITE);
            b.setText(iconLabel(tabIndex==0?"calendar":tabIndex==1?"results":"trophy",item));
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setTypeface(null,Typeface.BOLD);b.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable tabBg=new android.graphics.drawable.GradientDrawable();
            tabBg.setColor(active?0xff08643d:0xff052c25);tabBg.setCornerRadius(22*getResources().getDisplayMetrics().density);
            tabBg.setStroke((int)(1.3f*getResources().getDisplayMetrics().density),0xff178e5a);b.setBackground(tabBg);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,(int)(46*getResources().getDisplayMetrics().density),1);
            tp.setMargins(2,2,2,8);tabs.addView(b,tp);
            b.setOnClickListener(v->{if(!item.equals(selectedTab)){selectedMatchGroup=item.equals("Resultados")?0:-1;}showCompetitionTab(item);});
            tabIndex++;
        }
        String official=officialCalendar();
        if(!official.isEmpty()){

            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);body.addView(actions);
            for(int action=0;action<2;action++){
                final boolean update=action==0;
                TextView control=txt("",12,Color.WHITE);
                control.setText(iconLabel(update?"refresh":"external",update?"Actualizar":"Web oficial"));
                control.setSingleLine(true);
                control.setTypeface(null,Typeface.BOLD);control.setGravity(android.view.Gravity.CENTER);
                android.graphics.drawable.GradientDrawable controlBg=new android.graphics.drawable.GradientDrawable();
                controlBg.setColor(0xff173c3b);controlBg.setCornerRadius(20*getResources().getDisplayMetrics().density);
                controlBg.setStroke((int)(getResources().getDisplayMetrics().density),0xff396a60);
                control.setBackground(controlBg);
                LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,(int)(39*getResources().getDisplayMetrics().density),1);
                ap.setMargins(8,0,8,10);actions.addView(control,ap);
                control.setOnClickListener(v->{if(update)refreshNative(official);else openBrowser(official);});
            }
            LinearLayout statsActions=new LinearLayout(this);statsActions.setOrientation(LinearLayout.HORIZONTAL);body.addView(statsActions);
            for(String statsTab:new String[]{"Estadísticas","Ranking global"}){
                boolean players=statsTab.equals("Ranking global");
                TextView control=txt("",12,Color.WHITE);
                control.setText(iconLabel("results",players?"Ranking jugadores":"Estadísticas equipos"));
                control.setGravity(android.view.Gravity.CENTER);control.setTypeface(null,Typeface.BOLD);
                android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
                bg.setColor(tab.equals(statsTab)?0xff08643d:0xff173c3b);bg.setCornerRadius(dp(22));bg.setStroke(dp(1),tab.equals(statsTab)?0xff178e5a:0xff396a60);control.setBackground(bg);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);lp.setMargins(8,0,8,10);statsActions.addView(control,lp);
                control.setOnClickListener(v->showCompetitionTab(statsTab));
            }
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
        // La flecha de la cabecera ya permite volver a Competiciones.
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

    private int standingNumber(String value){
        try{return Integer.parseInt(value.trim().replaceAll("[^0-9-]",""));}
        catch(Exception ignored){return 0;}
    }
    private void standingBar(LinearLayout container,String label,int value,int max,int color){
        TextView caption=txt(label+"  ·  "+value,13,Color.WHITE);
        container.addView(caption);
        android.widget.FrameLayout track=new android.widget.FrameLayout(this);
        android.graphics.drawable.GradientDrawable base=new android.graphics.drawable.GradientDrawable();
        base.setColor(0xff152a39);base.setCornerRadius(dp(9));
        track.setBackground(base);
        LinearLayout.LayoutParams trackLp=new LinearLayout.LayoutParams(-1,dp(22));
        trackLp.setMargins(dp(12),0,dp(12),dp(8));
        container.addView(track,trackLp);
        android.view.View fill=new android.view.View(this);
        android.graphics.drawable.GradientDrawable shine=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xffe2fff3,color,0xff165b49});
        shine.setCornerRadius(dp(8));
        fill.setBackground(shine);
        fill.setElevation(dp(3));
        track.addView(fill,new android.widget.FrameLayout.LayoutParams(0,-1));
        track.addOnLayoutChangeListener((view,l,t,rr,b,ol,ot,orr,ob)->{
            android.view.ViewGroup.LayoutParams lp=fill.getLayoutParams();
            int width=(int)((long)track.getWidth()*Math.min(Math.max(0,value),Math.max(1,max))/Math.max(1,max));
            if(lp.width!=width){lp.width=width;fill.setLayoutParams(lp);}
        });
    }

    private void standingMetric(LinearLayout target,String value,String label){standingMetric(target,value,label,0xff75e6b2);}
    private void standingMetric(LinearLayout target,String value,String label,int metricColor){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);
        card.setPadding(dp(8),dp(7),dp(8),dp(7));
        android.graphics.drawable.GradientDrawable bgCard=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{0xff34566a,0xff1a3447,0xff122737});
        bgCard.setCornerRadius(dp(12));bgCard.setStroke(dp(1),0xff3d7380);
        card.setBackground(bgCard);card.setElevation(dp(4));
        TextView big=txt(value,19,metricColor);big.setGravity(android.view.Gravity.CENTER);
        big.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(big);
        TextView small=txt(label,10,0xffd2e4ef);small.setGravity(android.view.Gravity.CENTER);small.setMinLines(2);card.addView(small);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),dp(5),dp(3),dp(5));target.addView(card,lp);
    }
    private void standingSection(LinearLayout target,String title){
        TextView t=txt(title,15,0xff75e6b2);t.setTypeface(null,android.graphics.Typeface.BOLD);
        target.addView(t);
    }
    private void standingMinuteRanking3D(LinearLayout target,String[] names,int[] verified,int[] estimated,
                                         java.util.Map<String,android.view.View> playerCards){
        standingMinuteRanking3D(target,names,verified,estimated,playerCards,0xff50dca9);
    }
    private void standingMinuteRanking3D(LinearLayout target,String[] names,int[] verified,int[] estimated,java.util.Map<String,android.view.View> playerCards,int barColor){
        android.view.View chart=new android.view.View(this){
            final android.graphics.Paint p=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas c){
                super.onDraw(c);
                c.save();c.scale(getWidth()/360f,getHeight()/245f);
                int max=1;
                for(int i=0;i<verified.length;i++)max=Math.max(max,verified[i]+estimated[i]);
                p.setColor(0xff456073);p.setStrokeWidth(1);c.drawLine(10,192,350,192,p);
                float slot=340f/Math.max(1,names.length);
                for(int i=0;i<names.length;i++){
                    float x=12+i*slot+slot*.20f;
                    float w=Math.min(25,slot*.54f);
                    float h=158f*(verified[i]+estimated[i])/max;
                    float estimatedH=158f*estimated[i]/max;
                    float top=188-h;
                    android.graphics.Path side=new android.graphics.Path();
                    side.moveTo(x+w,top);side.lineTo(x+w+6,top-6);
                    side.lineTo(x+w+6,186);side.lineTo(x+w,192);side.close();
                    p.setColor(0xff254b60);c.drawPath(side,p);
                    p.setColor(barColor);c.drawRect(x,top+estimatedH,x+w,192,p);
                    if(estimatedH>0){p.setColor(0xffeeb57d);c.drawRect(x,top,x+w,top+estimatedH,p);}
                    android.graphics.Path cap=new android.graphics.Path();
                    cap.moveTo(x,top);cap.lineTo(x+6,top-6);cap.lineTo(x+w+6,top-6);cap.lineTo(x+w,top);cap.close();
                    p.setColor(0xffc5e5e9);c.drawPath(cap,p);
                    p.setColor(0xffedf7fc);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    p.setTextSize(12);p.setTextAlign(android.graphics.Paint.Align.CENTER);
                    c.drawText(String.valueOf(verified[i]+estimated[i]),x+w/2,Math.max(13,top-10),p);
                    p.setTextSize(11);p.setTypeface(android.graphics.Typeface.DEFAULT);
                    String surname=names[i].split(",")[0].trim();
                    if(surname.contains(" "))surname=surname.split(" ")[0];
                    if(surname.length()>9)surname=surname.substring(0,8)+"…";
                    p.setTextSize(10);p.setTextAlign(android.graphics.Paint.Align.LEFT);
                    c.save();c.rotate(-48,x+w/2,214);
                    c.drawText(surname,x+w/2,214,p);c.restore();
                }
                c.restore();
            }
        };
        chart.setBackgroundColor(0xff1b2d3f);
        target.addView(chart,new LinearLayout.LayoutParams(-1,dp(245)));
        chart.setOnTouchListener((v,event)->{
            if(event.getAction()!=android.view.MotionEvent.ACTION_UP)return true;
            float logicalX=event.getX()*360f/Math.max(1,v.getWidth());
            int index=Math.max(0,Math.min(names.length-1,(int)((logicalX-12)/(340f/Math.max(1,names.length)))));
            android.view.View card=playerCards.get(names[index]);
            if(card!=null)card.performClick();
            return true;
        });
    }

    private void standingColumns3D(LinearLayout target,String[] labels,int[] values,int[] colors){
        android.view.View chart=new android.view.View(this){
            final android.graphics.Paint p=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas c){
                super.onDraw(c);
                c.save();c.scale(getWidth()/360f,getHeight()/205f);
                int max=1;for(int v:values)max=Math.max(max,v);
                p.setColor(0xff375064);p.setStrokeWidth(1);c.drawLine(12,151,348,151,p);
                float slot=330f/Math.max(1,values.length);
                for(int i=0;i<values.length;i++){
                    float x=17+i*slot+slot*.24f;
                    float barWidth=Math.min(35,slot*.5f);
                    float height=values[i]*110f/max,top=147-height;
                    int color=colors[i%colors.length];
                    if(values[i]>0){
                    android.graphics.Path side=new android.graphics.Path();
                    side.moveTo(x+barWidth,top);side.lineTo(x+barWidth+9,top-8);
                    side.lineTo(x+barWidth+9,143);side.lineTo(x+barWidth,151);side.close();
                    p.setColor(0xff235568);c.drawPath(side,p);
                    p.setShader(new android.graphics.LinearGradient(x,top,x+barWidth,top,
                        0xffc2ffed,color,android.graphics.Shader.TileMode.CLAMP));
                    c.drawRect(x,top,x+barWidth,151,p);p.setShader(null);
                    android.graphics.Path cap=new android.graphics.Path();
                    cap.moveTo(x,top);cap.lineTo(x+9,top-8);
                    cap.lineTo(x+barWidth+9,top-8);cap.lineTo(x+barWidth,top);cap.close();
                    p.setColor(0x99ffffff);c.drawPath(cap,p);
                    }
                    p.setColor(0xfff1f7fc);p.setTextSize(14);
                    p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    p.setTextAlign(android.graphics.Paint.Align.CENTER);
                    c.drawText(String.valueOf(values[i]),x+barWidth*.5f,Math.max(18,top-13),p);
                    p.setTextSize(12);p.setTypeface(android.graphics.Typeface.DEFAULT);
                    c.drawText(labels[i],x+barWidth*.5f,174,p);
                }
                c.restore();
            }
        };
        android.graphics.drawable.GradientDrawable panelBg=new android.graphics.drawable.GradientDrawable();
        panelBg.setColor(0xff1b2d3f);panelBg.setCornerRadius(dp(12));
        chart.setBackground(panelBg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(205));
        lp.setMargins(dp(4),dp(5),dp(4),dp(12));target.addView(chart,lp);
    }
    private int standingShade(int color,float factor){
        return android.graphics.Color.rgb(
            Math.min(255,(int)(android.graphics.Color.red(color)*factor)),
            Math.min(255,(int)(android.graphics.Color.green(color)*factor)),
            Math.min(255,(int)(android.graphics.Color.blue(color)*factor)));
    }
    private void standingPie(LinearLayout target,int[] values,int[] colors,String[] names){
        int sum=0;for(int v:values)sum+=Math.max(0,v);
        final int total=sum;
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);
        card.setPadding(dp(8),dp(6),dp(8),dp(10));card.setBackgroundColor(0xff1b2d3f);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(dp(4),dp(4),dp(4),dp(10));target.addView(card,cp);
        android.view.View chart=new android.view.View(this){
            final android.graphics.Paint p=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                super.onDraw(canvas);
                canvas.save();canvas.scale(getWidth()/360f,getHeight()/170f);
                if(total<=0){
                    p.setColor(0xffb6c6d1);p.setTextSize(14);
                    canvas.drawText("Sin resultados registrados",70,90,p);
                    canvas.restore();return;
                }
                android.graphics.RectF oval=new android.graphics.RectF(65,15,295,135);
                for(int depth=18;depth>=1;depth-=2){
                    android.graphics.RectF base=new android.graphics.RectF(65,15+depth,295,135+depth);
                    float angle=-90;
                    for(int i=0;i<values.length;i++){
                        float sweep=360f*Math.max(0,values[i])/total;
                        p.setColor(standingShade(colors[i%colors.length],0.58f));
                        canvas.drawArc(base,angle,sweep,true,p);angle+=sweep;
                    }
                }
                float start=-90;
                for(int i=0;i<values.length;i++){
                    float sweep=360f*Math.max(0,values[i])/total;
                    p.setColor(colors[i%colors.length]);canvas.drawArc(oval,start,sweep,true,p);
                    android.graphics.RectF shine=new android.graphics.RectF(78,20,282,115);
                    p.setColor(0x22ffffff);canvas.drawArc(shine,start,sweep,true,p);
                    start+=sweep;
                }
                canvas.restore();
            }
        };
        chart.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);
        card.addView(chart,new LinearLayout.LayoutParams(-1,dp(175)));
        for(int i=0;i<values.length;i++){
            TextView legend=txt("●  "+names[i]+": "+values[i]+(total>0?"  ("+Math.round(values[i]*100f/total)+" %)":""),12,colors[i]);
            legend.setPadding(dp(8),dp(4),dp(8),dp(4));card.addView(legend);
        }
    }
    private java.util.ArrayList<org.json.JSONObject> standingTeamMatches(org.json.JSONObject data,String team){
        java.util.ArrayList<org.json.JSONObject> found=new java.util.ArrayList<>();
        if(data==null)return found;
        org.json.JSONArray tables=data.optJSONArray("tables");if(tables==null)return found;
        String key=teamKey(team);
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(int i=0;i<tables.length();i++){
            org.json.JSONObject table=tables.optJSONObject(i);
            if(table==null||!"matches".equals(table.optString("kind")))continue;
            org.json.JSONArray rows=table.optJSONArray("rows");if(rows==null)continue;
            for(int j=0;j<rows.length();j++){
                org.json.JSONObject row=rows.optJSONObject(j);if(row==null||!isPlayedMatch(row))continue;
                if(!key.equals(teamKey(row.optString("home","")))&&!key.equals(teamKey(row.optString("away",""))))continue;
                String id=teamKey(row.optString("home",""))+"|"+teamKey(row.optString("away",""))+"|"+row.optString("score","")+"|"+row.optString("when","");
                if(seen.add(id))found.add(row);
            }
        }
        java.util.Collections.sort(found,(a,b)->Long.compare(matchTimestamp(a,false),matchTimestamp(b,false)));
        return found;
    }
    private void standingEvolutionChart(LinearLayout target,java.util.ArrayList<Integer> scored,java.util.ArrayList<Integer> conceded){
        final int n=Math.min(scored.size(),conceded.size());
        final int[] own=new int[n+1],opp=new int[n+1];
        for(int i=0;i<n;i++){own[i+1]=own[i]+scored.get(i);opp[i+1]=opp[i]+conceded.get(i);}
        android.view.View chart=new android.view.View(this){
            final android.graphics.Paint p=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas c){
                super.onDraw(c);c.save();c.scale(getWidth()/360f,getHeight()/245f);
                int max=1;for(int i=0;i<=n;i++)max=Math.max(max,Math.max(own[i],opp[i]));
                p.setColor(0xff456074);p.setStrokeWidth(1);
                for(int k=0;k<5;k++){
                    float y=195-k*39;
                    c.drawLine(34,y,340,y,p);
                    p.setColor(0xffb9d0dd);p.setTextSize(10);p.setTextAlign(android.graphics.Paint.Align.RIGHT);
                    c.drawText(String.valueOf(Math.round(max*k/4f)),29,y+4,p);
                    p.setColor(0xff456074);
                }
                for(int j=0;j<2;j++){
                    int[] series=j==0?own:opp;
                    int color=j==0?0xff4cdda7:0xff5caefa;
                    android.graphics.Path path=new android.graphics.Path();
                    for(int i=0;i<=n;i++){
                        float x=34+306f*i/Math.max(1,n);
                        float y=195-156f*series[i]/max;
                        if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
                    }
                    p.setColor(0x66000000);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(7);
                    c.save();c.translate(3,4);c.drawPath(path,p);c.restore();
                    p.setColor(color);p.setStrokeWidth(3.5f);c.drawPath(path,p);
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    for(int i=0;i<=n;i++){
                        float x=34+306f*i/Math.max(1,n),y=195-156f*series[i]/max;
                        c.drawCircle(x,y,4,p);
                    }
                }
                p.setColor(0xffd6e6f0);p.setTextSize(11);p.setTextAlign(android.graphics.Paint.Align.CENTER);
                for(int i=0;i<=n;i++)c.drawText(i==0?"Inicio":"P"+i,34+306f*i/Math.max(1,n),216,p);
                for(int i=1;i<=n;i++){
                    float x=34+306f*i/Math.max(1,n);
                    boolean win=scored.get(i-1)>conceded.get(i-1);
                    boolean draw=scored.get(i-1).equals(conceded.get(i-1));
                    p.setColor(draw?0xffffc365:win?0xff4cdda7:0xffed7979);
                    c.drawCircle(x,230,3.8f,p);
                }
                c.restore();
            }
        };
        chart.setBackgroundColor(0xff1b2d3f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(245));
        lp.setMargins(dp(4),dp(8),dp(4),dp(10));target.addView(chart,lp);
        LinearLayout legend=new LinearLayout(this);legend.setOrientation(1);target.addView(legend);
        TextView green=txt("●  Tantos a favor acumulados",11,0xff4cdda7);
        TextView blue=txt("●  Tantos en contra acumulados",11,0xff5caefa);
        green.setPadding(dp(8),dp(1),dp(8),dp(1));
        blue.setPadding(dp(8),dp(1),dp(8),dp(1));
        legend.addView(green);legend.addView(blue);
        TextView note=txt("P1, P2…: orden de partidos  ·  ● Verde: victoria  ● Rojo: derrota  ● Amarillo: empate",10,0xffa9c4d2);
        note.setPadding(dp(8),dp(2),dp(8),dp(3));target.addView(note);
    }
    private int[] standingMatchScore(String score){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)\\s*[-–:]\\s*(\\d+)").matcher(score);
        if(!m.find())return null;
        try{return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))};}
        catch(Exception e){return null;}
    }
    /** PDF text extraction can list substitutions in column order rather than minute order. */
    private String orderedSubstitutionLines(String changes){
        java.util.ArrayList<String> output=new java.util.ArrayList<>();
        java.util.ArrayList<String> block=new java.util.ArrayList<>();
        for(String line:changes.split("\\r?\\n")){
            String t=line.trim();
            if(t.matches("(?i)Equipo (local|visitante):.*")){
                if(!block.isEmpty()){sortSubstitutionBlock(block);output.addAll(block);block.clear();}
                output.add(t);
            }else if(t.matches("^\\d+\\. Min .*"))block.add(t);
            else if(!t.isEmpty())output.add(t);
        }
        if(!block.isEmpty()){sortSubstitutionBlock(block);output.addAll(block);}
        return android.text.TextUtils.join("\n",output);
    }

    private void sortSubstitutionBlock(java.util.ArrayList<String> block){
        java.util.regex.Pattern minutePattern=java.util.regex.Pattern.compile("^\\d+\\. Min (\\d+).*");
        java.util.Collections.sort(block,(a,b)->{
            java.util.regex.Matcher ma=minutePattern.matcher(a),mb=minutePattern.matcher(b);
            if(!ma.matches()||!mb.matches())return 0;
            return Integer.compare(Integer.parseInt(ma.group(1)),Integer.parseInt(mb.group(1)));
        });
    }

    private String substitutionFailureDetail(String lineup,String changes,boolean local,int duration){
        java.util.HashSet<String> known=new java.util.HashSet<>();
        java.util.HashSet<String> active=new java.util.HashSet<>();
        int side=-1;boolean starter=false;
        java.util.regex.Pattern player=java.util.regex.Pattern.compile("^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*(\\d{7}).*$");
        for(String line:lineup.split("\\r?\\n")){
            String t=line.trim();
            if(t.equals("EQUIPO LOCAL"))side=0;
            else if(t.equals("EQUIPO VISITANTE"))side=1;
            else if(t.startsWith("TITULARES ("))starter=true;
            else if(t.startsWith("SUPLENTES ("))starter=false;
            else if(side==(local?0:1)){
                java.util.regex.Matcher m=player.matcher(t);
                if(m.matches()){
                    if(!known.add(m.group(1)))return "Dorsal duplicado en alineación: "+m.group(1);
                    if(starter)active.add(m.group(1));
                }
            }
        }
        if(active.isEmpty())return "No se identifican titulares";
        side=-1;int last=-1;
        java.util.regex.Pattern change=java.util.regex.Pattern.compile("^\\d+\\. Min (\\d+) · Cambio · Entra nº (\\d+) · Sale nº (\\d+).*$");
        for(String line:orderedSubstitutionLines(changes).split("\\r?\\n")){
            String t=line.trim();
            if(t.matches("(?i)Equipo local:.*"))side=0;
            else if(t.matches("(?i)Equipo visitante:.*"))side=1;
            else if(side==(local?0:1)&&t.matches("^\\d+\\. Min .*")){
                java.util.regex.Matcher m=change.matcher(t);
                if(!m.matches())return "Formato de cambio no reconocido: "+t;
                int minute=Integer.parseInt(m.group(1));
                String in=m.group(2),out=m.group(3);
                if(minute<last||minute>duration)return "Minuto fuera de orden o del partido: "+minute;
                if(!known.contains(in)||!known.contains(out))return "Dorsal no identificado: entra "+in+", sale "+out+" (min "+minute+")";
                if(!active.contains(out))return "Dorsal "+out+" no estaba en campo al salir (min "+minute+")";
                if(active.contains(in))return "Dorsal "+in+" ya estaba en campo al entrar (min "+minute+")";
                active.remove(out);active.add(in);last=minute;
            }
        }
        return "Cambios no conciliados; revisar acta oficial";
    }

    /** Calculate only fully traceable playing intervals from an official act. */
    /** Overlap of a player's documented field intervals with a temporary suspension. */
    private int suspensionOverlap(String lineup,String changes,boolean local,String dorsal,int start,int end,int duration){
        if(start<0||end<=start||end>duration)return -1;
        int side=-1;boolean starter=false;boolean playing=false;boolean found=false;
        java.util.regex.Pattern player=java.util.regex.Pattern.compile("^(\\d+)\\s*·.*");
        for(String line:lineup.split("\\r?\\n")){
            String t=line.trim();
            if(t.equals("EQUIPO LOCAL"))side=0;
            else if(t.equals("EQUIPO VISITANTE"))side=1;
            else if(t.startsWith("TITULARES ("))starter=true;
            else if(t.startsWith("SUPLENTES ("))starter=false;
            else if(side==(local?0:1)){
                java.util.regex.Matcher m=player.matcher(t);
                if(m.matches()&&dorsal.equals(m.group(1))){found=true;playing=starter;}
            }
        }
        if(!found)return -1;
        int last=0,overlap=0;side=-1;
        java.util.regex.Pattern change=java.util.regex.Pattern.compile("^\\d+\\. Min (\\d+) · Cambio · Entra nº (\\d+) · Sale nº (\\d+).*$");
        for(String line:orderedSubstitutionLines(changes).split("\\r?\\n")){
            String t=line.trim();
            if(t.matches("(?i)Equipo local:.*"))side=0;
            else if(t.matches("(?i)Equipo visitante:.*"))side=1;
            else if(side==(local?0:1)){
                java.util.regex.Matcher m=change.matcher(t);
                if(!m.matches())continue;
                int minute=Integer.parseInt(m.group(1));
                if(minute<last||minute>duration)return -1;
                if(playing)overlap+=Math.max(0,Math.min(end,minute)-Math.max(start,last));
                if(dorsal.equals(m.group(3)))playing=false;
                if(dorsal.equals(m.group(2)))playing=true;
                last=minute;
            }
        }
        if(playing)overlap+=Math.max(0,Math.min(end,duration)-Math.max(start,last));
        return overlap;
    }

    private java.util.Map<String,Integer> verifiedActMinutes(String lineup,String changes,boolean local,int duration){
        return verifiedActMinutes(lineup,changes,local,duration,new java.util.HashMap<>());
    }
    private java.util.Map<String,Integer> verifiedActMinutes(String lineup,String changes,boolean local,int duration,
        java.util.Map<String,java.util.ArrayList<String>> trace){
        if(duration<=0||changes==null||changes.trim().isEmpty())return null;
        java.util.Map<String,String> byDorsal=new java.util.HashMap<>();
        java.util.Map<String,Integer> minutes=new java.util.HashMap<>();
        java.util.Map<String,Integer> enteredAt=new java.util.HashMap<>();
        java.util.HashSet<String> onField=new java.util.HashSet<>();
        int side=-1;boolean starter=false;
        java.util.regex.Pattern player=java.util.regex.Pattern.compile("^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*(\\d{7}).*$");
        for(String line:lineup.split("\\r?\\n")){
            String t=line.trim();
            if(t.equals("EQUIPO LOCAL"))side=0;
            else if(t.equals("EQUIPO VISITANTE"))side=1;
            else if(t.startsWith("TITULARES ("))starter=true;
            else if(t.startsWith("SUPLENTES ("))starter=false;
            else if(side==(local?0:1)){
                java.util.regex.Matcher m=player.matcher(t);
                if(m.matches()){
                    String dorsal=m.group(1),id=m.group(3)+"|"+m.group(2).trim();
                    if(byDorsal.containsKey(dorsal))return null;
                    byDorsal.put(dorsal,id);minutes.put(id,0);
                    if(starter){onField.add(dorsal);enteredAt.put(dorsal,0);}
                }
            }
        }
        if(onField.isEmpty())return null;
        side=-1;int lastMinute=-1;int events=0;
        java.util.regex.Pattern event=java.util.regex.Pattern.compile(
            "^\\d+\\. Min (\\d+) · Cambio · Entra nº (\\d+) · Sale nº (\\d+).*$");
        for(String line:orderedSubstitutionLines(changes).split("\\r?\\n")){
            String t=line.trim();
            if(t.matches("(?i)Equipo local:.*"))side=0;
            else if(t.matches("(?i)Equipo visitante:.*"))side=1;
            else if(side==(local?0:1)&&t.matches("^\\d+\\. Min .*")){
                java.util.regex.Matcher m=event.matcher(t);
                if(!m.matches())return null;
                int minute=Integer.parseInt(m.group(1));
                String in=m.group(2),out=m.group(3);
                if(minute<0||minute>duration||minute<lastMinute||!onField.contains(out)
                    ||onField.contains(in)||!byDorsal.containsKey(in)||!byDorsal.containsKey(out))return null;
                String id=byDorsal.get(out);
                trace.computeIfAbsent(id,k->new java.util.ArrayList<>()).add("Sale min "+minute);
                trace.computeIfAbsent(byDorsal.get(in),k->new java.util.ArrayList<>()).add("Entra min "+minute);
                minutes.put(id,minutes.get(id)+minute-enteredAt.get(out));
                onField.remove(out);enteredAt.remove(out);
                onField.add(in);enteredAt.put(in,minute);
                lastMinute=minute;events++;
            }
        }
        // An explicit changes table with no rows means no substitutions; starters play the full match.
        // A missing changes table remains unverified.
        for(String dorsal:onField){
            String id=byDorsal.get(dorsal);
            minutes.put(id,minutes.get(id)+duration-enteredAt.get(dorsal));
        }
        return minutes;
    }

    private void showStandingTeamStatistics(String team,String[] values,org.json.JSONObject data){
        android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(1);
        frame.setBackgroundColor(0xff172b3c);
        TextView title=txt(team+" · ESTADÍSTICAS",18,0xff75e6b2);
        title.setTypeface(null,android.graphics.Typeface.BOLD);frame.addView(title);
        frame.addView(txt("Temporada "+season+" · "+selectedCompetition,11,0xffb8d4cc));
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(1);tabs.setPadding(dp(6),dp(4),dp(6),dp(4));tabs.setBackgroundColor(0xff102233);frame.addView(tabs); android.view.View navDivider=new android.view.View(this);navDivider.setBackgroundColor(0xff45677d);LinearLayout.LayoutParams navDividerLp=new LinearLayout.LayoutParams(-1,dp(2));navDividerLp.setMargins(dp(10),dp(5),dp(10),dp(9));frame.addView(navDivider,navDividerLp); final TextView[] tabButtons=new TextView[6];
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        LinearLayout content=new LinearLayout(this);content.setOrientation(1);
        content.setPadding(dp(8),dp(4),dp(8),dp(12));
        scroll.addView(content);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView close=txt("Cerrar",14,Color.WHITE);
        close.setGravity(android.view.Gravity.CENTER);
        close.setTypeface(null,android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable closeBg=new android.graphics.drawable.GradientDrawable();
        closeBg.setColor(0xff606066);closeBg.setCornerRadius(dp(4));
        close.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(-1,dp(43));
        closeLp.setMargins(dp(14),dp(6),dp(14),dp(10));
        close.setOnClickListener(v->dialog.dismiss());frame.addView(close,closeLp);
        dialog.setContentView(frame);
        String[] labels={"Resumen","Evolución","Partidos","Jugadores","Ataque/Def.","Ranking"};
        final int[] active={0};
        final boolean[] loadingActs={false};
        final java.util.HashSet<String> attemptedActs=new java.util.HashSet<>();
        final java.util.LinkedHashMap<String,String> actErrors=new java.util.LinkedHashMap<>();
        final Runnable[] redrawRef=new Runnable[1];
        Runnable redraw=()->{
            content.removeAllViews();
            for(int i=0;i<tabButtons.length;i++){
                TextView tab=tabButtons[i];
                tab.setTextColor(i==active[0]?0xff092c25:0xffd9eaff);
                tab.setCompoundDrawables(null,standingNavIcon(i,i==active[0]?0xff092c25:0xffd9eaff),null,null);
                android.graphics.drawable.GradientDrawable tabBg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{i==active[0]?0xff83e9bf:0xff244f86,i==active[0]?0xff40bc92:0xff17365c});tabBg.setCornerRadius(dp(7));tabBg.setStroke(dp(1),i==active[0]?0xffa5f6d4:0xff507eb1);tab.setBackground(tabBg);
            }
            if(active[0]==4){
                java.util.ArrayList<org.json.JSONObject> games=standingTeamMatches(data,team);
                android.content.SharedPreferences cache=getSharedPreferences("far_official_acts",MODE_PRIVATE);
                java.util.ArrayList<String> missing=new java.util.ArrayList<>();
                for(org.json.JSONObject game:games){String url=game.optString("actUrl","");if(url.startsWith("https://farugby.matchready.es/")&&!cache.contains(url)&&!attemptedActs.contains(url)&&!missing.contains(url))missing.add(url);}
                if(!loadingActs[0]&&!missing.isEmpty()){
                    loadingActs[0]=true;attemptedActs.addAll(missing);
                    new Thread(()->{com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());for(String url:missing){try{org.json.JSONObject parsed=RugbyHubActivity.extractOfficialAct(url);if(!parsed.optString("lineups","").trim().isEmpty())cache.edit().putString(url,parsed.toString()).apply();}catch(Exception ignored){}}
                    runOnUiThread(()->{loadingActs[0]=false;if(dialog.isShowing()&&active[0]==4&&redrawRef[0]!=null)redrawRef[0].run();});}).start();
                }
                if(loadingActs[0])content.addView(txt("Consultando actas oficiales…",12,0xff75e6b2));
                int pfTotal=0,paTotal=0,checked=0,playedGames=0;
                int[][] totals=new int[2][4], venue=new int[2][3], typePoints=new int[2][4]; int bestF=-1,worstF=Integer.MAX_VALUE,bestD=Integer.MAX_VALUE,worstD=-1; String bf="",wf="",bd="",wd="";
                java.util.ArrayList<Integer> pfSeries=new java.util.ArrayList<>(),paSeries=new java.util.ArrayList<>();
                LinearLayout history=new LinearLayout(this);history.setOrientation(1);
                for(org.json.JSONObject game:games){
                    int[] result=standingMatchScore(game.optString("score",""));if(result==null)continue;
                    boolean local=teamKey(team).equals(teamKey(game.optString("home","")));
                    int pf=result[local?0:1],pa=result[local?1:0];pfTotal+=pf;paTotal+=pa;playedGames++;pfSeries.add(pf);paSeries.add(pa);
                    String opponent=game.optString(local?"away":"home"); int v=local?0:1;venue[v][0]++;venue[v][1]+=pf;venue[v][2]+=pa; if(pf>bestF){bestF=pf;bf=opponent;}if(pf<worstF){worstF=pf;wf=opponent;}if(pa<bestD){bestD=pa;bd=opponent;}if(pa>worstD){worstD=pa;wd=opponent;} int[][] counts=new int[2][4], eventPoints=new int[2][4];int[] sums=new int[2];boolean valid=true;boolean[] sides=new boolean[2];int side=-1;
                    try{org.json.JSONObject act=new org.json.JSONObject(cache.getString(game.optString("actUrl",""),"{}"));
                    for(String raw:act.optString("scoreboard","").split("\\r?\\n")){
                        String line=raw.trim();if(line.startsWith("Equipo local:")){side=0;sides[0]=true;continue;}if(line.startsWith("Equipo visitante:")){side=1;sides[1]=true;continue;}if(side<0)continue;
                        java.util.regex.Matcher event=java.util.regex.Pattern.compile("(?i)Min\\s*(\\d+)\\s*[-·]\\s*([^\\n]*?)\\s*\\((\\d+)\\s*puntos?\\)").matcher(line);
                        if(event.find()){String kind=event.group(2).toLowerCase(java.util.Locale.ROOT);int pts=Integer.parseInt(event.group(3));sums[side]+=pts;int k=kind.contains("ensayo")?0:kind.contains("transform")?1:kind.contains("castigo")||kind.contains("penal")?2:kind.contains("drop")?3:-1;if(k>=0){counts[side][k]++;eventPoints[side][k]+=pts;}else valid=false;}
                        else if(line.matches("(?i)^\\d+\\.\\s*Min.*"))valid=false;
                    }}catch(Exception e){valid=false;}
                    valid=valid&&sides[0]&&sides[1]&&sums[0]==result[0]&&sums[1]==result[1];
                    LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(10),dp(10),dp(10),dp(10));
                    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xff203b4c);bg.setCornerRadius(dp(9));card.setBackground(bg);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));history.addView(card,lp);
                    card.addView(txt(game.optString("home")+" – "+game.optString("away"),13,0xffe2f5fa));
                    card.addView(txt((local?"LOCAL":"VISITANTE")+" · "+(pf>pa?"Victoria":pf==pa?"Empate":"Derrota"),12,pf>pa?0xff75e6b2:pf==pa?0xffffc365:0xffed7979)); LinearLayout matchRow=new LinearLayout(this);card.addView(matchRow);standingMetric(matchRow,""+pf,"Favor");standingMetric(matchRow,""+pa,"Contra");
                    if(valid){checked++;for(int k=0;k<4;k++){totals[0][k]+=counts[local?0:1][k];totals[1][k]+=counts[local?1:0][k];typePoints[0][k]+=eventPoints[local?0:1][k];typePoints[1][k]+=eventPoints[local?1:0][k];}
                    card.addView(txt("Ensayos: "+counts[local?0:1][0]+" a favor / "+counts[local?1:0][0]+" en contra",12,0xffd2e4ef));card.addView(txt("Acta conciliada con marcador",11,0xff75e6b2));}
                    else card.addView(txt("Desglose del acta pendiente de conciliación",11,0xffffc365));
                }
                standingSection(content,"Ataque y defensa · "+playedGames+" resultados");
                LinearLayout row=new LinearLayout(this);content.addView(row);standingMetric(row,""+pfTotal,"Tantos a favor");standingMetric(row,""+paTotal,"Tantos en contra");standingMetric(row,String.format(java.util.Locale.ROOT,"%+d",pfTotal-paTotal),"Diferencia");
                LinearLayout avg=new LinearLayout(this);content.addView(avg);standingMetric(avg,playedGames>0?String.format(java.util.Locale.getDefault(),"%.1f",pfTotal/(double)playedGames):"—","Media anotada");standingMetric(avg,playedGames>0?String.format(java.util.Locale.getDefault(),"%.1f",paTotal/(double)playedGames):"—","Media recibida");
                standingColumns3D(content,new String[]{"Favor","Contra"},new int[]{pfTotal,paTotal},new int[]{0xff48dca2,0xffed7979});
                standingSection(content,"Evolución de tantos");if(playedGames>0)standingEvolutionChart(content,pfSeries,paSeries);
                standingSection(content,"Anotaciones · "+checked+"/"+playedGames+" actas conciliadas");
                if(checked>0){
                    standingColumns3D(content,new String[]{"E+","E−","T+","T−","C+","C−","D+","D−"},new int[]{totals[0][0],totals[1][0],totals[0][1],totals[1][1],totals[0][2],totals[1][2],totals[0][3],totals[1][3]},new int[]{0xff48dca2,0xffed7979,0xff48dca2,0xffed7979,0xff48dca2,0xffed7979,0xff48dca2,0xffed7979});
                    content.addView(txt("E: ensayos · T: transformaciones · C: castigos · D: drops. + favor / − contra",11,0xffb8d4cc));
                    standingSection(content,"Origen de los puntos a favor");
                    if(java.util.Arrays.stream(typePoints[0]).sum()>0)standingPie(content,typePoints[0],new int[]{0xff48dca2,0xff56aaf2,0xffffc365,0xff9b8be8},new String[]{"Ensayos","Transformaciones","Castigos","Drops"});
                }
                if(playedGames>0){
                    standingSection(content,"Mejores y peores partidos");
                    content.addView(txt("Mejor ataque · "+bf+" · "+bestF+" puntos",14,0xff75e6b2));
                    content.addView(txt("Menor anotación · "+wf+" · "+worstF+" puntos",14,0xffffc365));
                    content.addView(txt("Mejor defensa · "+bd+" · "+bestD+" recibidos",14,0xff75e6b2));
                    content.addView(txt("Más puntos recibidos · "+wd+" · "+worstD,14,0xffffc365));
                    standingSection(content,"Local y visitante");
                    for(int v=0;v<2;v++){standingSection(content,(v==0?"Local":"Visitante")+" · "+venue[v][0]+" partidos");if(venue[v][0]>0){LinearLayout vr=new LinearLayout(this);content.addView(vr);standingMetric(vr,String.format(java.util.Locale.getDefault(),"%.1f",venue[v][1]/(double)venue[v][0]),"Media favor");standingMetric(vr,String.format(java.util.Locale.getDefault(),"%.1f",venue[v][2]/(double)venue[v][0]),"Media contra");}}
                    standingColumns3D(content,new String[]{"Local +","Local −","Fuera +","Fuera −"},new int[]{venue[0][1],venue[0][2],venue[1][1],venue[1][2]},new int[]{0xff48dca2,0xffed7979,0xff48dca2,0xffed7979});
                }
                content.addView(txt("El desglose incluye únicamente actas cuyo total de anotaciones coincide con ambos marcadores oficiales.",11,0xffb8d4cc));
                standingSection(content,"Comprobación por partido");content.addView(history);
            }else if(active[0]==0){
                int played=standingNumber(values[2]),won=standingNumber(values[3]);
                int drawn=standingNumber(values[4]),lost=standingNumber(values[5]);
                int forPoints=standingNumber(values[6]),against=standingNumber(values[7]);
                int triesFor=standingNumber(values[9]),triesAgainst=standingNumber(values[10]);
                LinearLayout top=new LinearLayout(this);content.addView(top);
                standingMetric(top,values[13],"Puntos");
                standingMetric(top,String.valueOf(played),"Partidos");
                standingMetric(top,played>0?Math.round(won*100f/played)+" %":"—","Victorias");
                LinearLayout second=new LinearLayout(this);content.addView(second);
                standingMetric(second,values[8],"Diferencia");
                standingMetric(second,played>0?String.format(java.util.Locale.getDefault(),"%.1f",forPoints/(double)played):"—","Tantos / partido");
                standingMetric(second,played>0?String.format(java.util.Locale.getDefault(),"%.1f",triesFor/(double)played):"—","Ensayos / partido");
                standingSection(content,"Resultados · distribución 3D");
                standingPie(content,new int[]{won,drawn,lost},
                    new int[]{0xff45dca3,0xffffc365,0xffed7979},
                    new String[]{"Victorias","Empates","Derrotas"});
                standingSection(content,"Tantos · ataque y defensa");
                int maxPoints=Math.max(1,Math.max(forPoints,against));
                standingColumns3D(content,new String[]{"Favor","Contra"},new int[]{forPoints,against},new int[]{0xff48dca2,0xff56aaf2});
                standingSection(content,"Ensayos · ataque y defensa");
                int maxTries=Math.max(1,Math.max(triesFor,triesAgainst));
                standingColumns3D(content,new String[]{"Favor","Contra"},new int[]{triesFor,triesAgainst},new int[]{0xff48dca2,0xff56aaf2});
                standingSection(content,"Bonus");
                int bonusO=standingNumber(values[11]),bonusD=standingNumber(values[12]);
                standingColumns3D(content,new String[]{"Ofens.","Defens."},new int[]{bonusO,bonusD},new int[]{0xff48dca2,0xff56aaf2});
            }else{
                standingSection(content,labels[active[0]]);
                java.util.ArrayList<org.json.JSONObject> matches=standingTeamMatches(data,team);
                if(active[0]==3||active[0]==5){
                    int duration=CompetitionRules.matchMinutes(season,category,selectedCompetition);
                    LinearLayout durationCards=new LinearLayout(this);if(active[0]==3)content.addView(durationCards);
                    standingMetric(durationCards,duration>0?duration+" min":"—","Partido completo");
                    standingMetric(durationCards,duration>0?(duration/2)+" min":"—","Cada tiempo");
                    if(active[0]==3)content.addView(txt(duration>0?"Duración prevista según las reglas verificadas de esta competición.":"Duración pendiente de la circular oficial de esta categoría y temporada.",12,0xffb8d4cc));
                    int actCount=0;
                    java.util.Map<String,Integer> appearances=new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    java.util.Map<String,Integer> starts=new java.util.HashMap<>();
                    java.util.Map<String,Integer> benches=new java.util.HashMap<>();
                    java.util.Map<String,Integer> verifiedMinutes=new java.util.HashMap<>();
                    java.util.Map<String,Integer> verifiedMatches=new java.util.HashMap<>();
                    java.util.Map<String,Integer> estimatedMinutes=new java.util.HashMap<>();
                    java.util.Map<String,Integer> estimatedMatches=new java.util.HashMap<>();
                    java.util.Map<String,java.util.ArrayList<String>> playerMinuteBreakdown=new java.util.HashMap<>();
                    java.util.Map<String,Integer> actualPlayed=new java.util.HashMap<>(), unused=new java.util.HashMap<>(), entries=new java.util.HashMap<>(), exits=new java.util.HashMap<>(), returns=new java.util.HashMap<>(), participationKnown=new java.util.HashMap<>();
                    java.util.Map<String,java.util.ArrayList<String>> changeBreakdown=new java.util.HashMap<>();
                    java.util.Map<String,Integer> tries=new java.util.HashMap<>(), conversions=new java.util.HashMap<>(), penalties=new java.util.HashMap<>(), drops=new java.util.HashMap<>(), points=new java.util.HashMap<>(), yellows=new java.util.HashMap<>(), reds=new java.util.HashMap<>(), sanctionMinutes=new java.util.HashMap<>(), scoringCoverage=new java.util.HashMap<>(), disciplineCoverage=new java.util.HashMap<>();
                    java.util.Map<String,java.util.ArrayList<String>> scoreHistory=new java.util.HashMap<>(), cardHistory=new java.util.HashMap<>(), scoreGames=new java.util.HashMap<>();
                    int[] minuteActCount={0};
                    java.util.ArrayList<String> minuteActDiagnostics=new java.util.ArrayList<>();
                    android.content.SharedPreferences actCache=getSharedPreferences("far_official_acts",MODE_PRIVATE);
                    if(!loadingActs[0]){
                        java.util.ArrayList<String> missing=new java.util.ArrayList<>();
                        for(org.json.JSONObject match:matches){
                            String url=match.optString("actUrl","");
                            if(url.startsWith("https://farugby.matchready.es/")&&(!actCache.contains(url)||!actCache.getString(url,"").contains("temporaryCards"))&&!attemptedActs.contains(url)&&!missing.contains(url))missing.add(url);
                        }
                        if(!missing.isEmpty()){
                            loadingActs[0]=true;
                            attemptedActs.addAll(missing);
                            content.addView(txt("Leyendo automáticamente "+missing.size()+" actas oficiales…",12,0xff75e6b2));
                            new Thread(()->{
                                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
                                for(String url:missing){
                                    try{
                                        org.json.JSONObject parsed=RugbyHubActivity.extractOfficialAct(url);
                                        if(parsed.optString("lineups","").trim().isEmpty())throw new java.io.IOException("El lector no ha identificado las alineaciones");
                                        actCache.edit().putString(url,parsed.toString()).apply();
                                        actErrors.remove(url);
                                    }catch(Exception error){
                                        synchronized(actErrors){actErrors.put(url,error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage()));}
                                    }
                                }
                                runOnUiThread(()->{
                                    loadingActs[0]=false;
                                    if(dialog.isShowing()&&(active[0]==3||active[0]==5)) {
                                        // Refresh the same tab without reopening the dialog.
                                        if(redrawRef[0]!=null)redrawRef[0].run();
                                    }
                                });
                            }).start();
                        }
                    }

                    for(org.json.JSONObject match:matches){
                        String url=match.optString("actUrl","");
                        org.json.JSONObject cached=null;
                        try{String raw=actCache.getString(url,null);if(raw!=null)cached=new org.json.JSONObject(raw);}catch(Exception ignored){}
                        if(cached==null){if(match.optJSONObject("acta")!=null)actCount++;continue;}
                        String lineup=cached.optString("lineups","");
                        if(lineup.isEmpty())continue;
                        boolean local=teamKey(team).equals(teamKey(match.optString("home","")));
                        java.util.Map<String,String> statDorsals=new java.util.HashMap<>();
                        int side=-1;boolean starterSection=false;
                        java.util.HashSet<String> thisMatch=new java.util.HashSet<>();
                        java.util.HashSet<String> matchStarters=new java.util.HashSet<>();
                        java.util.HashSet<String> matchBenches=new java.util.HashSet<>();
                        java.util.regex.Pattern player=java.util.regex.Pattern.compile("^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*(\\d{7}).*$");
                        for(String line:lineup.split("\\r?\\n")){
                            String t=line.trim();
                            if(t.equals("EQUIPO LOCAL"))side=0;
                            else if(t.equals("EQUIPO VISITANTE"))side=1;
                            else if(t.startsWith("JUGADORES SIN EQUIPO"))side=-1;
                            else if(t.startsWith("TITULARES ("))starterSection=true;
                            else if(t.startsWith("SUPLENTES ("))starterSection=false;
                            else if(side==(local?0:1)){
                                java.util.regex.Matcher m=player.matcher(t);
                                if(m.matches()){
                                    String playerId=m.group(3)+"|"+m.group(2).trim();
                                    statDorsals.put(m.group(1),playerId);
                                    thisMatch.add(playerId);
                                    if(starterSection)matchStarters.add(playerId);else matchBenches.add(playerId);
                                }
                            }
                        }
                        if(!thisMatch.isEmpty()){
                            String scoreTable=cached.optString("scoreboard","");
                            if(!scoreTable.trim().isEmpty()){
                                boolean activeScore=false, validScore=true, sideFound=false;
                                java.util.ArrayList<String[]> scoreEvents=new java.util.ArrayList<>();
                                for(String raw:scoreTable.split("\\r?\\n")){
                                    String line=raw.trim();
                                    if(line.startsWith("Equipo local:")){activeScore=local;sideFound|=activeScore;continue;}
                                    if(line.startsWith("Equipo visitante:")){activeScore=!local;sideFound|=activeScore;continue;}
                                    if(!activeScore)continue;
                                    java.util.regex.Matcher ev=java.util.regex.Pattern.compile("(?i)Min\\s*(\\d+)\\s*[-·]\\s*([^\\n]*?)\\s*\\((\\d+)\\s*puntos?\\)\\s*[-·]\\s*Dorsal\\s*(\\d+)").matcher(line);
                                    if(ev.find()){
                                        String id=statDorsals.get(ev.group(4));
                                        if(id==null){validScore=false;continue;}
                                        scoreEvents.add(new String[]{id,ev.group(2).toLowerCase(java.util.Locale.ROOT),ev.group(3),ev.group(1)});
                                    }else if(line.matches("(?i)^\\d+\\.\\s*Min.*"))validScore=false;
                                }
                                if(validScore&&sideFound){
                                    for(String id:thisMatch){scoringCoverage.merge(id,1,Integer::sum);scoreGames.computeIfAbsent(id,k->new java.util.ArrayList<>()).add(match.optString("home")+" – "+match.optString("away"));}
                                    for(String[] ev:scoreEvents){
                                        points.merge(ev[0],Integer.parseInt(ev[2]),Integer::sum);
                                        scoreHistory.computeIfAbsent(ev[0],k->new java.util.ArrayList<>()).add(match.optString("home")+" – "+match.optString("away")+" · min "+ev[3]+" · "+ev[1]+" · "+ev[2]+" pts");
                                        if(ev[1].contains("ensayo"))tries.merge(ev[0],1,Integer::sum);
                                        else if(ev[1].contains("transform"))conversions.merge(ev[0],1,Integer::sum);
                                        else if(ev[1].contains("castigo")||ev[1].contains("penal"))penalties.merge(ev[0],1,Integer::sum);
                                        else if(ev[1].contains("drop"))drops.merge(ev[0],1,Integer::sum);
                                    }
                                }
                            }
                            if(!cached.optString("temporaryCards","").isEmpty()&&!cached.optString("definitiveCards","").isEmpty())
                                for(String id:thisMatch)disciplineCoverage.merge(id,1,Integer::sum);
                            // Sanctions affect the identified player, not the whole team.
                            java.util.HashSet<String> sanctionedDorsals=new java.util.HashSet<>();
                            java.util.Map<String,java.util.ArrayList<int[]>> yellowIntervals=new java.util.HashMap<>();
                            java.util.HashSet<String> redDorsals=new java.util.HashSet<>();
                            boolean unknownSanction=false;
                            java.util.ArrayList<String> sanctionDetails=new java.util.ArrayList<>();
                            for(String keyName:new String[]{"temporaryCards","definitiveCards"}){
                                String rawCards=cached.optString(keyName,"");
                                int cardSide=-1;
                                for(String cardLine:rawCards.split("\\r?\\n")){
                                    String ct=cardLine.trim();
                                    if(ct.matches("(?i)Equipo local:.*"))cardSide=0;
                                    else if(ct.matches("(?i)Equipo visitante:.*"))cardSide=1;
                                    else if(cardSide==(local?0:1)&&ct.matches("^\\d+\\. Min .*")){
                                        java.util.regex.Matcher dm=java.util.regex.Pattern.compile("Dorsal (\\d+)").matcher(ct);
                                        if(dm.find()){
                                            String cardId=statDorsals.get(dm.group(1));
                                            if(cardId!=null){
                                                (keyName.equals("definitiveCards")?reds:yellows).merge(cardId,1,Integer::sum);
                                                cardHistory.computeIfAbsent(cardId,k->new java.util.ArrayList<>()).add(match.optString("home")+" – "+match.optString("away")+" · "+(keyName.equals("definitiveCards")?"Roja":"Amarilla")+" · "+ct);
                                                if(keyName.equals("temporaryCards")){
                                                    int penaltyDuration=CompetitionRules.temporarySuspensionMinutes(season,category);
                                                    if(penaltyDuration>0)sanctionMinutes.merge(cardId,penaltyDuration,Integer::sum);
                                                }
                                            }
                                            sanctionedDorsals.add(dm.group(1));
                                            if(keyName.equals("definitiveCards"))redDorsals.add(dm.group(1));
                                            else{
                                                java.util.regex.Matcher sm=java.util.regex.Pattern.compile("Min (\\d+)").matcher(ct);
                                                int suspension=CompetitionRules.temporarySuspensionMinutes(season,category);
                                                int total=CompetitionRules.matchMinutes(season,category,selectedCompetition);
                                                if(sm.find()&&suspension>0&&total>0){
                                                    int from=Integer.parseInt(sm.group(1));
                                                    if(from>=0&&from<total)
                                                        yellowIntervals.computeIfAbsent(dm.group(1),k->new java.util.ArrayList<>())
                                                            .add(new int[]{from,Math.min(total,from+suspension)});
                                                }
                                            }
                                            java.util.regex.Matcher minuteMatcher=java.util.regex.Pattern.compile("Min (\\d+)").matcher(ct);
                                            String minuteLabel=minuteMatcher.find()?minuteMatcher.group(1):"?";
                                            java.util.regex.Matcher typeMatcher=java.util.regex.Pattern.compile("Tipo ([A-E])").matcher(ct);
                                            String typeLabel=typeMatcher.find()?typeMatcher.group(1):"?";
                                            int sanctionDuration=CompetitionRules.temporarySuspensionMinutes(season,category);
                                            String sanctionEnd="?";
                                            if(keyName.equals("temporaryCards")&&sanctionDuration>0&&!minuteLabel.equals("?")){
                                                int startMinute=Integer.parseInt(minuteLabel);
                                                int matchDuration=CompetitionRules.matchMinutes(season,category,selectedCompetition);
                                                sanctionEnd=String.valueOf(Math.min(matchDuration,startMinute+sanctionDuration));
                                            }
                                            sanctionDetails.add((keyName.equals("temporaryCards")?"Temporal":"Definitiva")
                                                +" · dorsal "+dm.group(1)+" · minuto "+minuteLabel+" · código "+typeLabel
                                                +(keyName.equals("temporaryCards")&&sanctionDuration>0?" · sanción 5 min (FER M14, regla 9.29) · fin reglamentario min "+sanctionEnd:""));
                                        }else unknownSanction=true;
                                    }
                                }
                            }
                            java.util.Map<String,java.util.ArrayList<String>> trace=new java.util.HashMap<>();
                            java.util.Map<String,Integer> perAct=verifiedActMinutes(lineup,cached.optString("changes",""),local,
                                CompetitionRules.matchMinutes(season,category,selectedCompetition),trace);
                            if(perAct!=null){
                                String label=match.optString("home","")+" – "+match.optString("away","");
                                for(String id:thisMatch){
                                    java.util.ArrayList<String> events=trace.getOrDefault(id,new java.util.ArrayList<>());
                                    int en=0,ex=0,re=0;
                                    boolean hasPlayed=matchStarters.contains(id);
                                    for(String event:events){
                                        if(event.startsWith("Entra")){
                                            en++;if(hasPlayed)re++;hasPlayed=true;
                                        }else if(event.startsWith("Sale"))ex++;
                                    }
                                    participationKnown.merge(id,1,Integer::sum);
                                    (hasPlayed?actualPlayed:unused).merge(id,1,Integer::sum);
                                    entries.merge(id,en,Integer::sum);exits.merge(id,ex,Integer::sum);returns.merge(id,re,Integer::sum);
                                    changeBreakdown.computeIfAbsent(id,k->new java.util.ArrayList<>()).add(label+" · "+(matchStarters.contains(id)?"Titular":hasPlayed?"Suplente utilizado":"Sin jugar")+(events.isEmpty()?"":" · "+android.text.TextUtils.join(" · ",events)));
                                }
                            }
                            int excludedPlayers=0;
                            if(perAct!=null&&!sanctionedDorsals.isEmpty()){
                                java.util.HashMap<String,String> dorsalIds=new java.util.HashMap<>();
                                int lineSide=-1;
                                java.util.regex.Pattern dorsalPlayer=java.util.regex.Pattern.compile("^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*(\\d{7}).*$");
                                for(String rawLine:lineup.split("\\r?\\n")){
                                    String t=rawLine.trim();
                                    if(t.equals("EQUIPO LOCAL"))lineSide=0;
                                    else if(t.equals("EQUIPO VISITANTE"))lineSide=1;
                                    else if(lineSide==(local?0:1)){
                                        java.util.regex.Matcher pm=dorsalPlayer.matcher(t);
                                        if(pm.matches())dorsalIds.put(pm.group(1),pm.group(3)+"|"+pm.group(2).trim());
                                    }
                                }
                                for(String dorsal:sanctionedDorsals){
                                    String id=dorsalIds.get(dorsal);
                                    if(id==null){unknownSanction=true;continue;}
                                    Integer base=perAct.remove(id);
                                    if(base==null)continue;
                                    excludedPlayers++;
                                    if(redDorsals.contains(dorsal))continue; // Definitive cards require separate handling.
                                    java.util.ArrayList<int[]> intervals=yellowIntervals.get(dorsal);
                                    if(intervals==null||intervals.isEmpty())continue;
                                    // Avoid double-counting overlapping yellow-card intervals.
                                    java.util.Collections.sort(intervals,(a,b)->Integer.compare(a[0],b[0]));
                                    int deducted=0,previousEnd=-1;
                                    boolean calculable=true;
                                    for(int[] interval:intervals){
                                        int from=Math.max(interval[0],previousEnd);
                                        if(from<interval[1]){
                                            int overlap=suspensionOverlap(lineup,cached.optString("changes",""),local,dorsal,from,interval[1],
                                                CompetitionRules.matchMinutes(season,category,selectedCompetition));
                                            if(overlap<0){calculable=false;break;}
                                            deducted+=overlap;
                                        }
                                        previousEnd=Math.max(previousEnd,interval[1]);
                                    }
                                    if(calculable){
                                        int estimate=Math.max(0,base-deducted);
                                        estimatedMinutes.put(id,estimatedMinutes.getOrDefault(id,0)+estimate);
                                        estimatedMatches.put(id,estimatedMatches.getOrDefault(id,0)+1);
                                        playerMinuteBreakdown.computeIfAbsent(id,k->new java.util.ArrayList<>())
                                            .add(match.optString("home","")+" – "+match.optString("away","")+" · "+estimate
                                                +" min estimados (amarilla: "+deducted+" min de juego descontados; regreso no confirmado)");
                                    }
                                }
                            }
                            // If a card has no identifiable player, no minute count can be certified.
                            if(unknownSanction)perAct=null;
                            String matchLabel=match.optString("home","")+" - "+match.optString("away","");
                            if(perAct==null){
                                String reason=unknownSanction?"Expulsión sin dorsal identificable":
                                    cached.optString("changes","").trim().isEmpty()?"Sin tabla de cambios legible":
                                    substitutionFailureDetail(lineup,cached.optString("changes",""),local,CompetitionRules.matchMinutes(season,category,selectedCompetition));
                                minuteActDiagnostics.add("⚠ "+matchLabel+" · "+reason);
                            }else if(excludedPlayers>0){
                                minuteActDiagnostics.add("◐ "+matchLabel+" · Minutos calculados para "+perAct.size()+" jugadores; "+excludedPlayers+" sancionado(s) pendientes");
                            }else{
                                minuteActDiagnostics.add("✓ "+matchLabel+" · "+CompetitionRules.matchMinutes(season,category,selectedCompetition)+" min analizados");
                            }
                            for(String detail:sanctionDetails)
                                minuteActDiagnostics.add("    ↳ "+matchLabel+" · "+detail+" · reincorporación no confirmada");
                            if(perAct!=null){
                                minuteActCount[0]++;
                                for(java.util.Map.Entry<String,Integer> e:perAct.entrySet()){
                                    verifiedMinutes.put(e.getKey(),verifiedMinutes.getOrDefault(e.getKey(),0)+e.getValue());
                                    verifiedMatches.put(e.getKey(),verifiedMatches.getOrDefault(e.getKey(),0)+1);
                                    playerMinuteBreakdown.computeIfAbsent(e.getKey(),k->new java.util.ArrayList<>())
                                        .add(match.optString("home","")+" – "+match.optString("away","")+" · "+e.getValue()+" min verificados");
                                }
                            }
                            actCount++;
                            for(String id:thisMatch)appearances.put(id,appearances.getOrDefault(id,0)+1);
                            for(String id:matchStarters)starts.put(id,starts.getOrDefault(id,0)+1);
                            for(String id:matchBenches)benches.put(id,benches.getOrDefault(id,0)+1);
                        }
                    }
                    // Canonical identity is the federation licence, not the PDF name spelling.
                    java.util.Map<String,String> canonicalPlayers=new java.util.HashMap<>();
                    for(String id:appearances.keySet()){
                        String[] fields=id.split("\\|",2);
                        if(fields.length!=2)continue;
                        String clean=fields[1].replaceAll("(?:\\s+\\d+)+\\s*$","").trim();
                        String candidate=fields[0]+"|"+clean;
                        String prior=canonicalPlayers.get(fields[0]);
                        if(prior==null||candidate.length()<prior.length())canonicalPlayers.put(fields[0],candidate);
                    }
                    for(java.util.Map<String,Integer> counts:java.util.Arrays.asList(appearances,starts,benches,verifiedMinutes,verifiedMatches,estimatedMinutes,estimatedMatches,actualPlayed,unused,entries,exits,returns,participationKnown,tries,conversions,penalties,drops,points,yellows,reds,sanctionMinutes,scoringCoverage,disciplineCoverage)){
                        java.util.Map<String,Integer> merged=new java.util.HashMap<>();
                        for(java.util.Map.Entry<String,Integer> entry:counts.entrySet()){
                            String id=entry.getKey();
                            String canonical=canonicalPlayers.getOrDefault(id.split("\\|",2)[0],id);
                            merged.put(canonical,merged.getOrDefault(canonical,0)+entry.getValue());
                        }
                        counts.clear();counts.putAll(merged);
                    }
                    java.util.Map<String,java.util.ArrayList<String>> mergedBreakdown=new java.util.HashMap<>();
                    for(java.util.Map.Entry<String,java.util.ArrayList<String>> entry:playerMinuteBreakdown.entrySet()){
                        String id=entry.getKey();
                        String canonical=canonicalPlayers.getOrDefault(id.split("\\|",2)[0],id);
                        mergedBreakdown.computeIfAbsent(canonical,k->new java.util.ArrayList<>()).addAll(entry.getValue());
                    }
                    playerMinuteBreakdown.clear();playerMinuteBreakdown.putAll(mergedBreakdown);
                    java.util.Map<String,java.util.ArrayList<String>> mergedChanges=new java.util.HashMap<>();
                    for(java.util.Map.Entry<String,java.util.ArrayList<String>> e:changeBreakdown.entrySet()){
                        String canonical=canonicalPlayers.getOrDefault(e.getKey().split("\\|",2)[0],e.getKey());
                        mergedChanges.computeIfAbsent(canonical,k->new java.util.ArrayList<>()).addAll(e.getValue());
                    }
                    changeBreakdown.clear();changeBreakdown.putAll(mergedChanges);
                    if(!appearances.isEmpty()){
                        if(active[0]==3)standingSection(content,"Jugadores identificados · actas consultadas");
                        java.util.ArrayList<java.util.Map.Entry<String,Integer>> ranking=new java.util.ArrayList<>(appearances.entrySet());
                        java.util.Collections.sort(ranking,(a,b)->Integer.compare(b.getValue(),a.getValue()));
                        java.util.ArrayList<java.util.Map.Entry<String,Integer>> starterRanking=new java.util.ArrayList<>(ranking);
                        java.util.Collections.sort(starterRanking,(a,b)->{
                            int cmp=Integer.compare(starts.getOrDefault(b.getKey(),0),starts.getOrDefault(a.getKey(),0));
                            if(cmp!=0)return cmp;
                            cmp=Integer.compare(b.getValue(),a.getValue());
                            return cmp!=0?cmp:a.getKey().compareToIgnoreCase(b.getKey());
                        });
                        java.util.Map<String,android.view.View> playerCardLookup=new java.util.HashMap<>();
                        java.util.ArrayList<String> minuteRank=new java.util.ArrayList<>(appearances.keySet());
                        java.util.Collections.sort(minuteRank,(a,b)->{
                            int ta=verifiedMinutes.getOrDefault(a,0)+estimatedMinutes.getOrDefault(a,0);
                            int tb=verifiedMinutes.getOrDefault(b,0)+estimatedMinutes.getOrDefault(b,0);
                            int cmp=Integer.compare(tb,ta);
                            return cmp!=0?cmp:a.compareToIgnoreCase(b);
                        });
                        LinearLayout minuteRankingPanel=new LinearLayout(this);
                        minuteRankingPanel.setOrientation(1);
                        LinearLayout starterRankingPanel=new LinearLayout(this);
                        starterRankingPanel.setOrientation(1);
                        LinearLayout pointsPanel=new LinearLayout(this);pointsPanel.setOrientation(1);
                        LinearLayout triesPanel=new LinearLayout(this);triesPanel.setOrientation(1);
                        for(int rankingType=0;rankingType<2;rankingType++){
                            LinearLayout panel=rankingType==0?pointsPanel:triesPanel;
                            java.util.Map<String,Integer> metric=rankingType==0?points:tries;
                            java.util.ArrayList<String> ranked=new java.util.ArrayList<>(metric.keySet());
                            ranked.removeIf(k->metric.getOrDefault(k,0)<=0);
                            ranked.sort((x,y)->Integer.compare(metric.get(y),metric.get(x)));
                            int count=Math.min(10,ranked.size());
                            standingSection(panel,rankingType==0?"Top 10 · puntos registrados":"Top 10 · ensayos registrados");
                            if(count==0)panel.addView(txt("Sin anotaciones identificadas",12,0xffb8d4cc));
                            else{
                                String[] names=new String[count];int[] numbers=new int[count];
                                for(int k=0;k<count;k++){
                                    String key=ranked.get(k);
                                    names[k]=key.substring(key.indexOf('|')+1);
                                    numbers[k]=metric.get(key);
                                }
                                standingMinuteRanking3D(panel,names,numbers,new int[count],playerCardLookup);
                                for(int k=0;k<count;k++){
                                    final String name=names[k];
                                    TextView line=txt(name+" · "+numbers[k],12,0xffd2e4ef);
                                    line.setOnClickListener(v->{android.view.View card=playerCardLookup.get(name);if(card!=null)card.performClick();});
                                    panel.addView(line);
                                }
                            }
                        }
                        String[] rankLabels={"Minutos","Titularidades","Puntos","Ensayos","Transformaciones","Participación %","Partidos disputados","Entradas","Salidas","Reingresos","Amarillas","Rojas"};
                        java.util.ArrayList<LinearLayout> rankPanels=new java.util.ArrayList<>();
                        rankPanels.add(minuteRankingPanel);rankPanels.add(starterRankingPanel);rankPanels.add(pointsPanel);rankPanels.add(triesPanel);
                        java.util.Map<String,Integer> percentages=new java.util.HashMap<>();
                        for(String id:appearances.keySet()){
                            int known=verifiedMatches.getOrDefault(id,0)+estimatedMatches.getOrDefault(id,0);
                            if(duration>0&&known>0&&known==appearances.get(id))percentages.put(id,Math.round(100f*(verifiedMinutes.getOrDefault(id,0)+estimatedMinutes.getOrDefault(id,0))/(duration*known)));
                        }
                        java.util.List<java.util.Map<String,Integer>> extraMetrics=java.util.Arrays.asList(conversions,percentages,actualPlayed,entries,exits,returns,yellows,reds);
                        for(int ri=0;ri<extraMetrics.size();ri++){
                            final int kind=ri;
                            java.util.Map<String,Integer> metric=extraMetrics.get(ri);
                            LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);rankPanels.add(panel);
                            standingSection(panel,"Top 10 · "+rankLabels[ri+4]);
                            panel.addView(txt(ri==1?"Porcentaje sobre minutos posibles en convocatorias analizadas completas; incluye estimaciones por amarilla.":"Datos registrados en las actas analizadas; la ausencia de datos no equivale a cero.",11,0xffa9c4d2));
                            java.util.ArrayList<String> ids=new java.util.ArrayList<>(metric.keySet());
                            ids.removeIf(k->metric.getOrDefault(k,0)<=0);
                            ids.sort((x,y)->{int c=Integer.compare(metric.get(y),metric.get(x));return c!=0?c:x.compareToIgnoreCase(y);});
                            int n=Math.min(10,ids.size());
                            if(n==0){panel.addView(txt("Sin registros positivos disponibles",12,0xffb8d4cc));continue;}
                            String[] names=new String[n];int[] nums=new int[n];
                            for(int k=0;k<n;k++){String id=ids.get(k);names[k]=id.substring(id.indexOf('|')+1);nums[k]=metric.get(id);}
                            standingMinuteRanking3D(panel,names,nums,new int[n],playerCardLookup,kind==6?0xffffc66d:kind==7?0xffef777d:0xff50dca9);
                            for(int k=0;k<ids.size();k++){
                                String id=ids.get(k),name=id.substring(id.indexOf('|')+1);
                                TextView line=txt((k+1)+". "+name+" · "+metric.get(id)+(kind==1?" %"+(estimatedMatches.getOrDefault(id,0)>0?" (orientativo)":""):""),13,0xffd2e4ef);
                                line.setPadding(dp(10),dp(9),dp(10),dp(9));android.graphics.drawable.GradientDrawable rankBg=new android.graphics.drawable.GradientDrawable();rankBg.setColor(0xff203e50);rankBg.setCornerRadius(dp(8));line.setBackground(rankBg);LinearLayout.LayoutParams rankLp=new LinearLayout.LayoutParams(-1,-2);rankLp.setMargins(0,dp(3),0,dp(3));panel.addView(line,rankLp);
                                line.setOnClickListener(v->{android.view.View card=playerCardLookup.get(name);if(card!=null)card.performClick();});
                            }
                        }
                        TextView rankingSelector=txt("Ranking: Minutos  ▾",15,0xff75e6b2);android.graphics.drawable.GradientDrawable selectorBg=new android.graphics.drawable.GradientDrawable();selectorBg.setColor(0xff203e50);selectorBg.setCornerRadius(dp(10));selectorBg.setStroke(dp(1),0xff406575);rankingSelector.setBackground(selectorBg);
                        rankingSelector.setPadding(dp(10),dp(10),dp(10),dp(10));content.addView(rankingSelector);
                        for(int ri=0;ri<rankPanels.size();ri++){LinearLayout panel=rankPanels.get(ri);content.addView(panel);panel.setVisibility(ri==0?android.view.View.VISIBLE:android.view.View.GONE);}
                        rankingSelector.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Seleccionar ranking").setItems(rankLabels,(rd,choice)->{
                            for(int ri=0;ri<rankPanels.size();ri++)rankPanels.get(ri).setVisibility(ri==choice?android.view.View.VISIBLE:android.view.View.GONE);
                            rankingSelector.setText("Ranking: "+rankLabels[choice]+"  ▾");
                        }).show());
                        int minuteChartSize=Math.min(10,minuteRank.size());
                        if(minuteChartSize>0){
                            String[] minuteNames=new String[minuteChartSize];
                            int[] minuteVerified=new int[minuteChartSize];
                            int[] minuteEstimated=new int[minuteChartSize];
                            standingSection(minuteRankingPanel,"Top 10 · más minutos · gráfico 3D");
                            minuteRankingPanel.addView(txt("Verde: verificados  ·  Naranja: estimados por amarilla. Pulsa el gráfico o un jugador para abrir su ficha.",11,0xffa9c4d2));
                            for(int k=0;k<minuteChartSize;k++){
                                String key=minuteRank.get(k);
                                String[] parts=key.split("\\|",2);
                                minuteNames[k]=parts.length>1?parts[1]:key;
                                minuteVerified[k]=verifiedMinutes.getOrDefault(key,0);
                                minuteEstimated[k]=estimatedMinutes.getOrDefault(key,0);
                            }
                            standingMinuteRanking3D(minuteRankingPanel,minuteNames,minuteVerified,minuteEstimated,playerCardLookup);
                            for(int k=0;k<minuteChartSize;k++){
                                final String playerName=minuteNames[k];
                                TextView legendLine=txt((k+1)+". "+playerName+" · "+minuteVerified[k]+" min verificados"
                                    +(minuteEstimated[k]>0?" + "+minuteEstimated[k]+" estimados":""),12,0xffd2e4ef);
                                legendLine.setPadding(dp(8),dp(5),dp(8),dp(5));
                                legendLine.setOnClickListener(v->{
                                    android.view.View card=playerCardLookup.get(playerName);
                                    if(card!=null)card.performClick();
                                });
                                minuteRankingPanel.addView(legendLine);
                            }
                        }
                        int chartSize=Math.min(10,starterRanking.size());
                        if(chartSize>0){
                            String[] chartNames=new String[chartSize];
                            String[] chartFullNames=new String[chartSize];
                            int[] chartCounts=new int[chartSize];
                            int[] chartColors=new int[chartSize];
                            for(int k=0;k<chartSize;k++){
                                java.util.Map.Entry<String,Integer> entry=starterRanking.get(k);
                                String[] parts=entry.getKey().split("\\|",2);
                                String name=parts.length>1?parts[1]:entry.getKey();
                                chartNames[k]=String.valueOf(k+1);
                                chartFullNames[k]=name;
                                chartCounts[k]=starts.getOrDefault(entry.getKey(),0);
                                chartColors[k]=k%2==0?0xff48dca2:0xff56aaf2;
                            }
                            standingSection(starterRankingPanel,"Top 10 · más titularidades · gráfico 3D");
                            standingMinuteRanking3D(starterRankingPanel,chartFullNames,chartCounts,new int[chartSize],playerCardLookup);
                            LinearLayout legend=new LinearLayout(this);
                            legend.setOrientation(1);
                            legend.setPadding(dp(9),dp(8),dp(9),dp(9));
                            starterRankingPanel.addView(legend);
                            for(int k=0;k<chartSize;k++){
                                java.util.Map.Entry<String,Integer> entry=starterRanking.get(k);
                                String[] parts=entry.getKey().split("\\|",2);
                                String name=parts.length>1?parts[1]:entry.getKey();
                                TextView line=txt((k+1)+".  "+name+"  ·  "+starts.getOrDefault(entry.getKey(),0)+" titularidades / "+entry.getValue()+" partidos",12,0xffd2e4ef);
                                line.setPadding(dp(3),dp(4),dp(3),dp(4));
                                legend.addView(line);line.setOnClickListener(v->{android.view.View card=playerCardLookup.get(name);if(card!=null)card.performClick();});
                            }
                        }
                        content.addView(txt("Minutos comprobados: "+minuteActCount[0]+" de "+actCount+" actas. Los minutos mostrados son parciales.",11,0xffa9c4d2));
                        TextView diagnosticToggle=txt("Ver comprobación de los "+actCount+" partidos  ▾",12,0xff75e6b2);
                        content.addView(diagnosticToggle);
                        LinearLayout diagnosticList=new LinearLayout(this);
                        diagnosticList.setOrientation(1);
                        diagnosticList.setVisibility(android.view.View.GONE);
                        content.addView(diagnosticList);
                        for(String diagnostic:minuteActDiagnostics){
                            TextView detail=txt(diagnostic,11,diagnostic.startsWith("✓")?0xff75e6b2:0xffffb48b);
                            detail.setPadding(dp(7),dp(5),dp(7),dp(5));
                            diagnosticList.addView(detail);
                        }
                        diagnosticToggle.setOnClickListener(v->{
                            boolean show=diagnosticList.getVisibility()!=android.view.View.VISIBLE;
                            diagnosticList.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);
                            diagnosticToggle.setText("Comprobación de partidos  "+(show?"▴":"▾"));
                        });
                        if(active[0]==3)standingSection(content,"Plantilla · "+ranking.size()+" jugadores");
                        android.widget.EditText searchPlayer=new android.widget.EditText(this);
                        searchPlayer.setSingleLine(true);searchPlayer.setTextSize(13);
                        searchPlayer.setHint("Buscar nombre o licencia");
                        searchPlayer.setHintTextColor(0xffa9c4d2);
                        searchPlayer.setTextColor(0xffe2f5fa);
                        content.addView(searchPlayer);
                        android.widget.Spinner playerFilter=new android.widget.Spinner(this);
                        String[] playerFilters={"Todos","Titulares","Suplentes","Todos los partidos","Mayor participación","Más minutos (top 10)"};
                        playerFilter.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,playerFilters));
                        content.addView(playerFilter);
                        LinearLayout rosterList=new LinearLayout(this);rosterList.setOrientation(1);content.addView(rosterList);if(active[0]==5){rosterList.setVisibility(android.view.View.GONE);searchPlayer.setVisibility(android.view.View.GONE);playerFilter.setVisibility(android.view.View.GONE);}
                        java.util.ArrayList<android.view.View> playerRows=new java.util.ArrayList<>();
                        java.util.ArrayList<String> playerSearchKeys=new java.util.ArrayList<>();
                        java.util.ArrayList<Integer> playerStartCounts=new java.util.ArrayList<>();
                        java.util.ArrayList<Integer> playerBenchCounts=new java.util.ArrayList<>();
                        java.util.ArrayList<Integer> playerMatchCounts=new java.util.ArrayList<>();
                        for(java.util.Map.Entry<String,Integer> item:ranking){
                            String[] parts=item.getKey().split("\\|",2);
                            String playerName=parts.length>1?parts[1]:item.getKey();
                            int playedCount=item.getValue();
                            int starterCount=starts.getOrDefault(item.getKey(),0);
                            int benchCount=benches.getOrDefault(item.getKey(),0);
                            LinearLayout playerRow=new LinearLayout(this);
                            playerRow.setOrientation(LinearLayout.HORIZONTAL);
                            playerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            playerRow.setPadding(dp(11),dp(8),dp(10),dp(8));
                            android.graphics.drawable.GradientDrawable playerBg=new android.graphics.drawable.GradientDrawable();
                            playerBg.setColor(0xff203c4f);
                            playerBg.setCornerRadius(dp(11));
                            playerBg.setStroke(dp(1),0xff345c6b);
                            playerRow.setBackground(playerBg);
                            LinearLayout.LayoutParams playerLp=new LinearLayout.LayoutParams(-1,-2);
                            playerLp.setMargins(0,dp(3),0,dp(3));
                            rosterList.addView(playerRow,playerLp);
                            playerRows.add(playerRow);
                            playerSearchKeys.add((playerName+" "+parts[0]).toLowerCase(java.util.Locale.ROOT));
                            playerStartCounts.add(starterCount);playerBenchCounts.add(benchCount);playerMatchCounts.add(playedCount);
                            LinearLayout info=new LinearLayout(this);
                            info.setOrientation(1);
                            playerRow.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                            TextView nameLabel=txt(playerName,13,0xffe2f5fa);
                            nameLabel.setTypeface(null,android.graphics.Typeface.BOLD);
                            info.addView(nameLabel);
                            info.addView(txt(playedCount+" partidos  ·  "+starterCount+" titular  ·  "+benchCount+" suplente",11,0xff75e6b2));
                            if(verifiedMatches.getOrDefault(item.getKey(),0)>0)
                                info.addView(txt(verifiedMinutes.getOrDefault(item.getKey(),0)+" min verificados · "+verifiedMatches.get(item.getKey())+" actas",11,0xffa9c4d2));
                            if(estimatedMatches.getOrDefault(item.getKey(),0)>0)
                                info.addView(txt(estimatedMinutes.getOrDefault(item.getKey(),0)+" min estimados · "+estimatedMatches.get(item.getKey())+" acta(s) con amarilla",11,0xffffc38c));
                            playerRow.addView(txt("›",23,0xff75e6b2));
                            playerCardLookup.put(playerName,playerRow);
                            playerRow.setOnClickListener(v->{
                                LinearLayout details=new LinearLayout(this);
                                details.setOrientation(1);
                                details.setBackgroundColor(0xff162b3b);
                                details.setPadding(dp(20),dp(12),dp(20),dp(8));
                                details.addView(txt(playerName,19,0xff75e6b2));
                                details.addView(txt("Licencia: "+parts[0],12,0xffb8d4cc));
                                LinearLayout playerHeader=new LinearLayout(this);details.addView(playerHeader);
                                standingMetric(playerHeader,""+playedCount,"Convocado");standingMetric(playerHeader,""+starterCount,"Titular");standingMetric(playerHeader,""+benchCount,"Suplente");
                                LinearLayout summaryPanel=new LinearLayout(this);summaryPanel.setOrientation(1);
                                LinearLayout participationPanel=new LinearLayout(this);participationPanel.setOrientation(1);
                                LinearLayout scoringPanel=new LinearLayout(this);scoringPanel.setOrientation(1);
                                LinearLayout disciplinePanel=new LinearLayout(this);disciplinePanel.setOrientation(1);
                                LinearLayout playerTabs=new LinearLayout(this);
                                details.addView(playerTabs);
                                details.addView(summaryPanel);details.addView(participationPanel);details.addView(scoringPanel);details.addView(disciplinePanel);
                                LinearLayout[] playerPanels={summaryPanel,participationPanel,scoringPanel,disciplinePanel};
                                String[] tabNames={"Resumen","Participación","Anotaciones","Disciplina"};
                                TextView[] tabViews=new TextView[4];
                                for(int k=0;k<4;k++){
                                    final int selected=k;
                                    TextView tab=txt(tabNames[k],10,0xffd2e4ef);tabViews[k]=tab;
                                    tab.setGravity(android.view.Gravity.CENTER);tab.setPadding(0,dp(12),0,dp(12));
                                    tab.setBackgroundColor(k==0?0xff28566b:0xff1b3446);
                                    playerTabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
                                    playerPanels[k].setVisibility(k==0?android.view.View.VISIBLE:android.view.View.GONE);
                                    tab.setOnClickListener(tabView->{for(int j=0;j<4;j++){playerPanels[j].setVisibility(j==selected?android.view.View.VISIBLE:android.view.View.GONE);tabViews[j].setBackgroundColor(j==selected?0xff28566b:0xff1b3446);}});
                                }
                                String statId=item.getKey();
                                int playedActual=actualPlayed.getOrDefault(statId,0), known=participationKnown.getOrDefault(statId,0);
                                int mins=verifiedMinutes.getOrDefault(statId,0), checked=verifiedMatches.getOrDefault(statId,0);
                                int estimated=estimatedMinutes.getOrDefault(statId,0), estimatedCount=estimatedMatches.getOrDefault(statId,0);
                                int minuteKnown=checked+estimatedCount;
                                standingSection(participationPanel,"Participación · "+known+"/"+playedCount+" actas");
                                LinearLayout pr=new LinearLayout(this);participationPanel.addView(pr);
                                standingMetric(pr,""+playedActual,"Disputados");standingMetric(pr,""+unused.getOrDefault(statId,0),"Sin jugar");
                                LinearLayout mr=new LinearLayout(this);participationPanel.addView(mr);
                                standingMetric(mr,checked>0?mins+" min":"—","Verificados · "+checked+" actas");
                                standingMetric(mr,estimatedCount>0?estimated+" min":"—","Estimados · "+estimatedCount+" actas",0xffffb76c);
                                if(minuteKnown>0&&duration>0){
                                    int possible=duration*minuteKnown;
                                    int pct=(int)Math.round(100.0*(mins+estimated)/possible);
                                    LinearLayout totals=new LinearLayout(this);participationPanel.addView(totals);
                                    standingMetric(totals,(mins+estimated)+" min",estimatedCount>0?"Total orientativo":"Total verificado");
                                    standingMetric(totals,String.format(java.util.Locale.getDefault(),"%.1f min",(mins+estimated)/(double)minuteKnown),"Media por convocatoria");
                                    standingSection(participationPanel,pct+"% de minutos posibles"+(estimatedCount>0?" · orientativo":""));
                                    standingPie(participationPanel,new int[]{mins,estimated,Math.max(0,possible-mins-estimated)},new int[]{0xff48dca2,0xffffb76c,0xff456073},new String[]{"Verificados","Estimados","No jugados"});
                                }
                                standingSection(participationPanel,"Sustituciones");
                                standingColumns3D(participationPanel,new String[]{"Entradas","Salidas","Reingresos"},new int[]{entries.getOrDefault(statId,0),exits.getOrDefault(statId,0),returns.getOrDefault(statId,0)},new int[]{0xff48dca2,0xff56aaf2,0xff80d9cc});
                                java.util.ArrayList<String> breakdown=playerMinuteBreakdown.get(statId);
                                java.util.ArrayList<String> changesForPlayer=changeBreakdown.get(statId);
                                standingSection(participationPanel,"Participación por partido");
                                java.util.LinkedHashMap<String,java.util.ArrayList<String>> matchCards=new java.util.LinkedHashMap<>();
                                if(changesForPlayer!=null)for(String line:changesForPlayer){
                                    String[] fields=line.split(" · ",2);
                                    matchCards.computeIfAbsent(fields[0].trim(),k->new java.util.ArrayList<>()).add(fields.length>1?fields[1]:"");
                                }
                                if(breakdown!=null)for(String line:breakdown){
                                    String[] fields=line.split(" · ",2);
                                    matchCards.computeIfAbsent(fields[0].trim(),k->new java.util.ArrayList<>()).add(fields.length>1?fields[1]:line);
                                }
                                for(java.util.Map.Entry<String,java.util.ArrayList<String>> matchCard:matchCards.entrySet()){
                                    LinearLayout eventCard=new LinearLayout(this);eventCard.setOrientation(1);eventCard.setPadding(dp(10),dp(9),dp(10),dp(9));
                                    android.graphics.drawable.GradientDrawable eventBg=new android.graphics.drawable.GradientDrawable();
                                    eventBg.setColor(0xff203b4c);eventBg.setCornerRadius(dp(9));eventBg.setStroke(dp(1),0xff365768);eventCard.setBackground(eventBg);
                                    LinearLayout.LayoutParams eventLp=new LinearLayout.LayoutParams(-1,-2);eventLp.setMargins(0,dp(4),0,dp(4));
                                    participationPanel.addView(eventCard,eventLp);
                                    eventCard.addView(txt(matchCard.getKey(),13,0xffe2f5fa));
                                    for(String line:matchCard.getValue()){
                                        java.util.regex.Matcher mm=java.util.regex.Pattern.compile("^(\\d+)\\s+min").matcher(line);
                                        if(mm.find()&&duration>0){
                                            int value=Integer.parseInt(mm.group(1));boolean est=line.contains("estimados");
                                            standingBar(eventCard,est?"Estimados":"Verificados",value,duration,est?0xffffb76c:0xff48dca2);
                                            if(est)eventCard.addView(txt("■ Amarilla · regreso no confirmado",11,0xffffc38c));
                                        }else{
                                            for(String event:line.split(" · ")){
                                                int eventColor=event.startsWith("Sale")?0xffffb76c:event.startsWith("Entra")?0xff75e6b2:0xff8fcaf5;
                                                String marker=event.startsWith("Sale")?"↗ ":event.startsWith("Entra")?"↘ ":"● ";
                                                eventCard.addView(txt(marker+event,12,eventColor));
                                            }
                                        }
                                    }
                                }
                                if(estimatedCount>0)participationPanel.addView(txt("Naranja: minutos estimados descontando la sanción reglamentaria. El regreso efectivo no consta en el acta.",11,0xffffc38c));
                                int t=tries.getOrDefault(statId,0), cv=conversions.getOrDefault(statId,0), pn=penalties.getOrDefault(statId,0), dr=drops.getOrDefault(statId,0), pt=points.getOrDefault(statId,0);
                                int yc=yellows.getOrDefault(statId,0), rc=reds.getOrDefault(statId,0);
                                LinearLayout sr=new LinearLayout(this);summaryPanel.addView(sr);
                                standingMetric(sr,(mins+estimated)+" min",estimatedCount>0?"Orientativos":"Verificados");
                                standingMetric(sr,minuteKnown>0&&duration>0?Math.round(100.0*(mins+estimated)/(duration*minuteKnown))+"%":"—",estimatedCount>0?"Particip. orientativa":"Participación");
                                LinearLayout sr2=new LinearLayout(this);summaryPanel.addView(sr2);standingMetric(sr2,""+pt,"Puntos");standingMetric(sr2,""+t,"Ensayos");
                                LinearLayout sr3=new LinearLayout(this);summaryPanel.addView(sr3);standingMetric(sr3,""+cv,"Transf.");standingMetric(sr3,""+yc,"Amarillas",0xffffc365);standingMetric(sr3,""+rc,"Rojas",0xffed7979);
                                standingColumns3D(summaryPanel,new String[]{"Disputados","Titular","Suplente"},new int[]{playedActual,starterCount,benchCount},new int[]{0xff48dca2,0xff56aaf2,0xff80d9cc});
                                int sc=scoringCoverage.getOrDefault(statId,0);
                                standingSection(scoringPanel,"Anotaciones · "+sc+"/"+playedCount+" actas");
                                LinearLayout ar=new LinearLayout(this);scoringPanel.addView(ar);standingMetric(ar,""+pt,"Puntos registrados");standingMetric(ar,sc>0?String.format(java.util.Locale.getDefault(),"%.2f",pt/(double)sc):"—","Media por acta");
                                LinearLayout ar2=new LinearLayout(this);scoringPanel.addView(ar2);standingMetric(ar2,""+t,"Ensayos");standingMetric(ar2,""+cv,"Transformaciones");
                                LinearLayout ar3=new LinearLayout(this);scoringPanel.addView(ar3);standingMetric(ar3,""+pn,"Castigos");standingMetric(ar3,""+dr,"Drops");
                                if(pt>0&&5*t+2*cv+3*pn+3*dr==pt)standingPie(scoringPanel,new int[]{5*t,2*cv,3*pn,3*dr},new int[]{0xff48dca2,0xff56aaf2,0xffffc365,0xff80d9cc},new String[]{"Ensayos","Transformaciones","Castigos","Drops"});
                                standingColumns3D(scoringPanel,new String[]{"Ensayos","Transf.","Castigos","Drops"},new int[]{t,cv,pn,dr},new int[]{0xff48dca2,0xff56aaf2,0xffffc365,0xff80d9cc});
                                java.util.ArrayList<String> sh=scoreHistory.get(statId);
                                java.util.ArrayList<String> sg=scoreGames.get(statId);
                                if(sg!=null&&!sg.isEmpty()){
                                    java.util.LinkedHashMap<String,Integer> perGame=new java.util.LinkedHashMap<>();
                                    for(String game:sg)perGame.put(game,0);
                                    if(sh!=null)for(String event:sh){String[] f=event.split(" · ");if(f.length==4){try{perGame.merge(f[0],Integer.parseInt(f[3].replace(" pts","")),Integer::sum);}catch(Exception ignored){}}}
                                    int maxPoints=1;for(int n:perGame.values())maxPoints=Math.max(maxPoints,n);
                                    String[] gameLabels=new String[perGame.size()];int[] accumulated=new int[perGame.size()];int[] chartColors=new int[perGame.size()];int idx=0,totalPoints=0;
                                    for(int n:perGame.values()){totalPoints+=n;gameLabels[idx]="P"+(idx+1);accumulated[idx]=totalPoints;chartColors[idx]=0xff48dca2;idx++;}
                                    standingSection(scoringPanel,"Puntos acumulados · actas analizadas");
                                    standingColumns3D(scoringPanel,gameLabels,accumulated,chartColors);
                                    standingSection(scoringPanel,"Anotaciones por partido");
                                    for(java.util.Map.Entry<String,Integer> game:perGame.entrySet()){
                                        LinearLayout gc=new LinearLayout(this);gc.setOrientation(1);gc.setPadding(dp(10),dp(8),dp(10),dp(8));
                                        android.graphics.drawable.GradientDrawable gb=new android.graphics.drawable.GradientDrawable();gb.setColor(0xff203b4c);gb.setCornerRadius(dp(9));gc.setBackground(gb);
                                        LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(-1,-2);gl.setMargins(0,dp(4),0,dp(4));scoringPanel.addView(gc,gl);
                                        gc.addView(txt(game.getKey(),13,0xffe2f5fa));standingBar(gc,"Puntos",game.getValue(),maxPoints,0xff48dca2);
                                        if(game.getValue()==0)gc.addView(txt("Sin anotaciones registradas",11,0xffa9c4d2));
                                        if(sh!=null)for(String event:sh)if(event.startsWith(game.getKey()+" · "))gc.addView(txt(event.substring(game.getKey().length()+3),12,0xffd2e4ef));
                                    }
                                }
                                standingSection(disciplinePanel,"Disciplina · "+disciplineCoverage.getOrDefault(statId,0)+"/"+playedCount+" actas");
                                LinearLayout ds=new LinearLayout(this);disciplinePanel.addView(ds);standingMetric(ds,""+yc,"Amarillas",0xffffc365);standingMetric(ds,""+rc,"Rojas",0xffed7979);standingMetric(ds,""+sanctionMinutes.getOrDefault(statId,0),"Min. sanción",0xffffb76c);
                                standingColumns3D(disciplinePanel,new String[]{"Amarillas","Rojas"},new int[]{yc,rc},new int[]{0xffffc365,0xffed7979});
                                java.util.ArrayList<String> ch=cardHistory.get(statId);
                                if(ch!=null){standingSection(disciplinePanel,"Historial de sanciones");for(String event:ch){TextView cardText=txt("■ "+event+(event.contains(" · Amarilla · ")&&CompetitionRules.temporarySuspensionMinutes(season,category)>0?" · "+CompetitionRules.temporarySuspensionMinutes(season,category)+" min reglamentarios":""),12,event.contains(" · Roja · ")?0xffed7979:0xffffc365);cardText.setPadding(dp(10),dp(10),dp(10),dp(10));android.graphics.drawable.GradientDrawable cbg=new android.graphics.drawable.GradientDrawable();cbg.setColor(0xff203b4c);cbg.setCornerRadius(dp(9));cardText.setBackground(cbg);LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);clp.setMargins(0,dp(4),0,dp(4));disciplinePanel.addView(cardText,clp);}}
                                disciplinePanel.addView(txt("La duración reglamentaria no confirma el regreso efectivo al campo.",11,0xffb8d4cc));
                                LinearLayout panelBody=new LinearLayout(this);panelBody.setOrientation(1);
                                for(LinearLayout panel:playerPanels){details.removeView(panel);panelBody.addView(panel);}
                                android.widget.ScrollView playerScroll=new android.widget.ScrollView(this);playerScroll.setFillViewport(false);playerScroll.addView(panelBody);
                                details.addView(playerScroll,new LinearLayout.LayoutParams(-1,0,1));
                                TextView closePlayer=txt("CERRAR",14,0xff75e6b2);closePlayer.setGravity(android.view.Gravity.CENTER);closePlayer.setPadding(0,dp(14),0,dp(14));details.addView(closePlayer);
                                android.app.AlertDialog playerDialog=new android.app.AlertDialog.Builder(this).setView(details).create();
                                closePlayer.setOnClickListener(closeView->playerDialog.dismiss());playerDialog.show();
                                if(playerDialog.getWindow()!=null){playerDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff162b3b));playerDialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),(int)(getResources().getDisplayMetrics().heightPixels*.86f));}
                            });
                        }
                        Runnable applyPlayerFilter=()->{
                            String term=searchPlayer.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                            int mode=playerFilter.getSelectedItemPosition();
                            for(int k=0;k<playerRows.size();k++){
                                boolean show=playerSearchKeys.get(k).contains(term);
                                if(mode==1)show&=playerStartCounts.get(k)>0;
                                else if(mode==2)show&=playerBenchCounts.get(k)>0;
                                else if(mode==3)show&=playerMatchCounts.get(k)==matches.size();
                                else if(mode==4)show&=playerMatchCounts.get(k)>=Math.max(1,matches.size()-1);
                                else if(mode==5){
                                    String name=playerSearchKeys.get(k);
                                    boolean top=false;
                                    for(int rank=0;rank<Math.min(10,minuteRank.size());rank++)
                                        if(name.equals(minuteRank.get(rank).toLowerCase(java.util.Locale.ROOT)))top=true;
                                    show&=top;
                                }
                                playerRows.get(k).setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);
                            }
                        };
                        searchPlayer.addTextChangedListener(new android.text.TextWatcher(){
                            public void beforeTextChanged(CharSequence t,int start,int count,int after){}
                            public void onTextChanged(CharSequence t,int start,int before,int count){applyPlayerFilter.run();}
                            public void afterTextChanged(android.text.Editable e){}
                        });
                        playerFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                            public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){applyPlayerFilter.run();}
                            public void onNothingSelected(android.widget.AdapterView<?> parent){}
                        });
                    }
                    if(!actErrors.isEmpty()){
                        content.addView(txt("No se pudieron extraer "+actErrors.size()+" actas. Detalle técnico:",12,0xffffb48b));
                        for(java.util.Map.Entry<String,String> err:actErrors.entrySet())
                            content.addView(txt(err.getValue(),10,0xffffb48b));
                    }
                    final int displayedActCount=actCount;
                    TextView actToggle=txt("Actas oficiales · "+displayedActCount+"/"+matches.size()+"  ▾",14,0xff75e6b2);
                    actToggle.setPadding(dp(10),dp(12),dp(10),dp(12));content.addView(actToggle);
                    LinearLayout actDetails=new LinearLayout(this);actDetails.setOrientation(1);
                    actDetails.setVisibility(android.view.View.GONE);content.addView(actDetails);
                    actToggle.setOnClickListener(v->{
                        boolean show=actDetails.getVisibility()!=android.view.View.VISIBLE;
                        actDetails.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);
                        actToggle.setText("Actas oficiales · "+displayedActCount+"/"+matches.size()+(show?"  ▴":"  ▾"));
                    });
                    standingSection(actDetails,"Actas y participación");
                    actDetails.addView(txt(actCount+" de "+matches.size()+" partidos con alineación disponible",13,0xffd2e4ef));
                    actDetails.addView(txt("Las alineaciones se leen automáticamente desde los PDF oficiales de cada partido.",11,0xff75e6b2));
                    actDetails.addView(txt("Los minutos individuales se calcularán con las alineaciones, entradas, salidas y reingresos de cada acta. Sin sustituciones verificadas no se asignarán minutos estimados.",12,0xffb8d4cc));
                    if(matches.isEmpty())actDetails.addView(txt("Todavía no hay partidos oficiales vinculados a este equipo.",12,0xffd2e4ef));
                    for(org.json.JSONObject match:matches){
                        String matchName=match.optString("home","")+"  "+match.optString("score","")+"  "+match.optString("away","");
                        org.json.JSONObject act=match.optJSONObject("acta");
                        actDetails.addView(txt(matchName,12,Color.WHITE));
                        String side=teamKey(team).equals(teamKey(match.optString("home","")))?"Local":"Visitante";
                        org.json.JSONArray starters=act==null?null:act.optJSONArray("titulares"+side);
                        org.json.JSONArray substitutes=act==null?null:act.optJSONArray("suplentes"+side);
                        if(starters!=null||substitutes!=null){
                            int a=starters==null?0:starters.length(),b=substitutes==null?0:substitutes.length();
                            actDetails.addView(txt(a+" titulares · "+b+" suplentes · minutos pendientes de validar",11,0xff75e6b2));
                        }else if(actCache.contains(match.optString("actUrl","")))actDetails.addView(txt("Alineación oficial incorporada al recuento de jugadores",11,0xff75e6b2));
                        else actDetails.addView(txt("Alineación oficial pendiente de lectura",11,0xffa9c4d2));
                        String actUrl=match.optString("actUrl","");
                        if(actUrl.startsWith("https://farugby.matchready.es/")){
                            TextView open=txt("Consultar acta oficial ›",12,0xff75e6b2);
                            open.setPadding(dp(8),dp(4),dp(8),dp(10));actDetails.addView(open);
                            open.setOnClickListener(v->{
                                android.content.Intent intent=new android.content.Intent(this,RugbyHubActivity.class);
                                intent.putExtra("far_act_url",actUrl);
                                intent.putExtra("far_home",match.optString("home",""));
                                intent.putExtra("far_away",match.optString("away",""));
                                startActivity(intent);
                            });
                        }
                    }
                }else if(matches.isEmpty()){
                    content.addView(txt("No hay resultados oficiales vinculados para este equipo en los datos disponibles. No se mostrarán valores estimados.",13,0xffd2e4ef));
                }else{
                    int homePlayed=0,awayPlayed=0,homeWins=0,awayWins=0,homeFor=0,awayFor=0;
                    java.util.ArrayList<Integer> pointsFor=new java.util.ArrayList<>();
                    java.util.ArrayList<Integer> pointsAgainst=new java.util.ArrayList<>();
                    for(org.json.JSONObject match:matches){
                        int[] score=standingMatchScore(match.optString("score",""));if(score==null)continue;
                        boolean home=teamKey(team).equals(teamKey(match.optString("home","")));
                        int pf=home?score[0]:score[1],pa=home?score[1]:score[0];
                        pointsFor.add(pf);pointsAgainst.add(pa);
                        if(home){homePlayed++;homeFor+=pf;if(pf>pa)homeWins++;}
                        else{awayPlayed++;awayFor+=pf;if(pf>pa)awayWins++;}
                    }
                    if(active[0]==1){
                        content.addView(txt("Evolución acumulada · "+pointsFor.size()+" partidos",12,0xffb8d4cc));
                        standingEvolutionChart(content,pointsFor,pointsAgainst);
                        int wins=0,losses=0,draws=0;
                        for(int k=0;k<pointsFor.size();k++){
                            int pf=pointsFor.get(k),pa=pointsAgainst.get(k);
                            if(pf>pa)wins++;else if(pf<pa)losses++;else draws++;
                        }
                        LinearLayout summary=new LinearLayout(this);content.addView(summary);
                        standingMetric(summary,String.valueOf(wins),"Victorias");
                        standingMetric(summary,String.valueOf(draws),"Empates");
                        standingMetric(summary,String.valueOf(losses),"Derrotas");
                        content.addView(txt("Los puntos de clasificación por jornada requieren conocer los bonus de cada partido.",11,0xffa9c4d2));
                    }else{
                        LinearLayout pair=new LinearLayout(this);content.addView(pair);
                        standingMetric(pair,String.valueOf(homePlayed),"En casa");
                        standingMetric(pair,String.valueOf(awayPlayed),"Fuera");
                        standingSection(content,"Victorias · local y visitante");
                        standingColumns3D(content,new String[]{"Local","Visit."},new int[]{homeWins,awayWins},
                            new int[]{0xff48dca2,0xff56aaf2});
                        standingSection(content,"Tantos anotados · local y visitante");
                        standingColumns3D(content,new String[]{"Local","Visit."},new int[]{homeFor,awayFor},
                            new int[]{0xff48dca2,0xff56aaf2});
                        for(org.json.JSONObject match:matches){
                            content.addView(txt(match.optString("home","")+"  "+match.optString("score","")+"  "+match.optString("away",""),12,Color.WHITE));
                        }
                    }
                    content.addView(txt("Datos de los resultados importados disponibles; pueden no incluir todos los partidos de la temporada.",11,0xffb8d4cc));
                }
            }
            scroll.scrollTo(0,0);
        };
        LinearLayout tabRow=null;
        for(int i=0;i<labels.length;i++){
            if(i%3==0){tabRow=new LinearLayout(this);tabs.addView(tabRow,new LinearLayout.LayoutParams(-1,-2));}
            final int index=i;TextView tab=txt(labels[i],12,Color.WHITE);tabButtons[i]=tab;
            tab.setTypeface(null,android.graphics.Typeface.BOLD);
            tab.setCompoundDrawablePadding(dp(3));
            tab.setGravity(android.view.Gravity.CENTER);tab.setPadding(dp(2),dp(5),dp(2),dp(5));tab.setMaxLines(2);
            LinearLayout.LayoutParams tabLp=new LinearLayout.LayoutParams(0,dp(58),1);tabLp.setMargins(dp(3),dp(3),dp(3),dp(3));tabRow.addView(tab,tabLp);
            tab.setOnClickListener(v->{active[0]=index;redraw.run();});
        }
        redrawRef[0]=redraw;
        redraw.run();
        dialog.show();
        android.view.Window window=dialog.getWindow();
        if(window!=null)window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(20),
            getResources().getDisplayMetrics().heightPixels-dp(65));
    }
    android.graphics.drawable.Drawable standingNavIcon(int kind,int color){
        int size=dp(24);
        android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(b);c.scale(size/24f,size/24f);
        android.graphics.Paint p=new android.graphics.Paint(3);p.setColor(color);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        android.graphics.Path q=new android.graphics.Path();
        switch(kind){
            case 0:
                c.drawRoundRect(3,3,10,10,1,1,p);c.drawRoundRect(14,3,21,10,1,1,p);c.drawRoundRect(3,14,10,21,1,1,p);c.drawRoundRect(14,14,21,21,1,1,p);break;
            case 1:
                c.drawLine(3,3,3,21,p);c.drawLine(3,21,22,21,p);q.moveTo(6,16);q.lineTo(11,11);q.lineTo(15,14);q.lineTo(21,5);c.drawPath(q,p);c.drawLine(16,5,21,5,p);c.drawLine(21,5,21,10,p);break;
            case 2:
                c.drawRoundRect(3,5,21,21,2,2,p);c.drawLine(3,10,21,10,p);c.drawLine(8,3,8,7,p);c.drawLine(16,3,16,7,p);c.drawLine(7,14,10,14,p);c.drawLine(14,14,17,14,p);c.drawLine(7,18,10,18,p);break;
            case 3:
                c.drawCircle(9,7,3,p);c.drawCircle(17,8,2.5f,p);c.drawRoundRect(3,13,15,21,3,3,p);q.moveTo(17,13);q.quadTo(22,13,22,20);q.lineTo(17,20);c.drawPath(q,p);break;
            case 4:
                q.moveTo(12,2);q.lineTo(21,5);q.lineTo(20,13);q.quadTo(18,19,12,22);q.quadTo(6,19,4,13);q.lineTo(3,5);q.close();c.drawPath(q,p);
                c.save();c.rotate(-38,11,12);c.drawOval(5,9,17,15,p);c.drawLine(8,12,14,12,p);c.drawLine(10,10.5f,10,13.5f,p);c.drawLine(12,10.5f,12,13.5f,p);c.restore();c.drawLine(15,7,19,7,p);c.drawLine(19,7,19,11,p);break;
            case 5:
                q.moveTo(7,3);q.lineTo(17,3);q.lineTo(17,10);q.quadTo(17,15,12,15);q.quadTo(7,15,7,10);q.close();c.drawPath(q,p);q.reset();q.moveTo(7,5);q.lineTo(3,5);q.quadTo(3,12,8,12);q.moveTo(17,5);q.lineTo(21,5);q.quadTo(21,12,16,12);c.drawPath(q,p);c.drawLine(12,15,12,20,p);c.drawLine(7,21,17,21,p);break;
            case 6:
                c.drawArc(4,4,20,20,35,285,false,p);c.drawLine(20,4,20,10,p);c.drawLine(20,10,14,10,p);break;
            case 7:
                q.moveTo(11,5);q.lineTo(4,5);q.lineTo(4,20);q.lineTo(19,20);q.lineTo(19,13);c.drawPath(q,p);c.drawLine(12,3,21,3,p);c.drawLine(21,3,21,12,p);c.drawLine(11,13,21,3,p);break;
        }
        android.graphics.drawable.BitmapDrawable d=new android.graphics.drawable.BitmapDrawable(getResources(),b);d.setBounds(0,0,size,size);return d;
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
        return CompetitionRules.phase(season,category,selectedCompetition,data,standing);
    }
    String m14PositionGuide(int rank){
        return CompetitionRules.positionGuide(season,category,selectedCompetition,1,rank);
    }
    String m14SecondPhaseGuide(int rank){
        return CompetitionRules.positionGuide(season,category,selectedCompetition,2,rank);
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
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(10,7,10,7);card.setBackgroundColor(0xff162e3c);
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
            lp.setMargins(right?dp(10):dp(3),0,right?dp(4):dp(5),0);layout.addView(logo,lp);
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
    private void competitionPlayerRanking(LinearLayout panel,org.json.JSONObject data,java.util.ArrayList<String> teams){
        android.content.SharedPreferences cache=getSharedPreferences("far_official_acts",MODE_PRIVATE);
        java.util.LinkedHashMap<String,org.json.JSONObject> matches=new java.util.LinkedHashMap<>();
        for(String team:teams)for(org.json.JSONObject g:standingTeamMatches(data,team)){
            if("Descanso".equalsIgnoreCase(g.optString("home").trim())||"Descanso".equalsIgnoreCase(g.optString("away").trim()))continue;
            String url=g.optString("actUrl");if(url.startsWith("https://farugby.matchready.es/"))matches.put(url,g);
        }
        panel.addView(txt("Consultando "+matches.size()+" actas oficiales…",13,0xff75e6b2));
        new Thread(()->{
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
            java.util.Map<String,int[]> totals=new java.util.LinkedHashMap<>();
            java.util.Map<String,String> labels=new java.util.HashMap<>();
            java.util.Map<String,java.util.ArrayList<String>> history=new java.util.HashMap<>();
            java.util.Map<String,String> playerTeams=new java.util.HashMap<>();
            int coverage=0,scoreCoverage=0;
            for(java.util.Map.Entry<String,org.json.JSONObject> entry:matches.entrySet()){
                try{
                    String url=entry.getKey();org.json.JSONObject g=entry.getValue();
                    org.json.JSONObject act=new org.json.JSONObject(cache.getString(url,"{}"));
                    if(act.optString("lineups").isEmpty()){act=RugbyHubActivity.extractOfficialAct(url);if(!act.optString("lineups").isEmpty())cache.edit().putString(url,act.toString()).apply();}
                    java.util.Map<String,String> dorsals=new java.util.HashMap<>();
                    java.util.Set<String> seen=new java.util.HashSet<>();int side=-1;boolean starter=false;
                    for(String raw:act.optString("lineups").split("\\r?\\n")){
                        String line=raw.trim();
                        if(line.equals("EQUIPO LOCAL")){side=0;continue;}
                        if(line.equals("EQUIPO VISITANTE")){side=1;continue;}
                        if(line.startsWith("JUGADORES SIN EQUIPO")){side=-1;continue;}
                        if(line.startsWith("TITULARES (")){starter=true;continue;}
                        if(line.startsWith("SUPLENTES (")){starter=false;continue;}
                        if(side<0)continue;
                        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d+)\\s*·\\s*(.*?)\\s*\\|\\s*(\\d{7}).*$").matcher(line);
                        if(!m.matches())continue;
                        String team=g.optString(side==0?"home":"away"),id=m.group(3)+"|"+teamKey(team);
                        labels.put(id,m.group(2).trim().replaceFirst("\\s+\\d+(?:\\s+\\d+)*$","")+" · "+team);
                        dorsals.put(side+":"+m.group(1),id);playerTeams.put(id,team);
                        if(seen.add(id)){int[] a=totals.computeIfAbsent(id,k->new int[12]);a[0]++;if(starter)a[1]++;}
                    }
                    if(seen.isEmpty())continue;coverage++;
                    java.util.Set<String> sanctioned=new java.util.HashSet<>();
                    boolean unknownCard=false;
                    for(String key:new String[]{"temporaryCards","definitiveCards"}){
                        int cardSide=-1;
                        for(String raw:act.optString(key).split("\\r?\\n")){
                            String line=raw.trim();
                            if(line.matches("(?i)Equipo local:.*"))cardSide=0;
                            else if(line.matches("(?i)Equipo visitante:.*"))cardSide=1;
                            else if(line.matches("^\\d+\\. Min .*")){
                                java.util.regex.Matcher cm=java.util.regex.Pattern.compile("Dorsal (\\d+)").matcher(line);
                                String who=cm.find()?dorsals.get(cardSide+":"+cm.group(1)):null;
                                if(who==null)unknownCard=true;else sanctioned.add(who);
                            }
                        }
                    }
                    java.util.Map<String,Integer> matchMinutes=new java.util.HashMap<>();
                    for(int sideIndex=0;sideIndex<2;sideIndex++){
                        java.util.Map<String,java.util.ArrayList<String>> trace=new java.util.HashMap<>();
                        java.util.Map<String,Integer> mins=verifiedActMinutes(act.optString("lineups"),act.optString("changes"),sideIndex==0,CompetitionRules.matchMinutes(season,category,selectedCompetition),trace);
                        if(mins==null)continue;
                        String team=g.optString(sideIndex==0?"home":"away");
                        for(java.util.Map.Entry<String,Integer> me:mins.entrySet()){
                            String id=me.getKey().split("\\|",2)[0]+"|"+teamKey(team);
                            if(!seen.contains(id))continue;
                            int[] a=totals.get(id);
                            if(me.getValue()>0||trace.getOrDefault(me.getKey(),new java.util.ArrayList<>()).stream().anyMatch(e->e.startsWith("Entra")))a[10]++;
                            if(!unknownCard&&!sanctioned.contains(id)){a[9]+=me.getValue();a[11]++;matchMinutes.put(id,me.getValue());}
                        }
                    }
                    for(String id:seen)history.computeIfAbsent(id,k->new java.util.ArrayList<>()).add(g.optString("home")+" – "+g.optString("away")+" · "+(matchMinutes.containsKey(id)?matchMinutes.get(id)+" min verificados":"Minutos no verificados"));
                    int[] score=standingMatchScore(g.optString("score"));int[] sums=new int[2];boolean valid=score!=null;boolean[] sides=new boolean[2];
                    java.util.ArrayList<String[]> events=new java.util.ArrayList<>();side=-1;
                    for(String raw:act.optString("scoreboard").split("\\r?\\n")){
                        String line=raw.trim();
                        if(line.startsWith("Equipo local:")){side=0;sides[0]=true;continue;}
                        if(line.startsWith("Equipo visitante:")){side=1;sides[1]=true;continue;}
                        if(side<0)continue;
                        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)Min\\s*(\\d+)\\s*[-·]\\s*([^\\n]*?)\\s*\\((\\d+)\\s*puntos?\\)\\s*[-·]\\s*Dorsal\\s*(\\d+)").matcher(line);
                        if(m.find()){String id=dorsals.get(side+":"+m.group(4));if(id==null){valid=false;continue;}sums[side]+=Integer.parseInt(m.group(3));events.add(new String[]{id,m.group(2).toLowerCase(java.util.Locale.ROOT),m.group(3)});}
                        else if(line.matches("(?i)^\\d+\\.\\s*Min.*"))valid=false;
                    }
                    if(valid&&sides[0]&&sides[1]&&sums[0]==score[0]&&sums[1]==score[1]){scoreCoverage++;for(String[] e:events){int[] a=totals.get(e[0]);a[2]+=Integer.parseInt(e[2]);String k=e[1];if(k.contains("ensayo"))a[3]++;else if(k.contains("transform"))a[4]++;else if(k.contains("castigo")||k.contains("penal"))a[5]++;else if(k.contains("drop"))a[6]++;}}
                    for(String key:new String[]{"temporaryCards","definitiveCards"}){side=-1;for(String raw:act.optString(key).split("\\r?\\n")){String line=raw.trim();if(line.matches("(?i)Equipo local:.*"))side=0;else if(line.matches("(?i)Equipo visitante:.*"))side=1;else if(side>=0&&line.matches("^\\d+\\. Min .*")){java.util.regex.Matcher m=java.util.regex.Pattern.compile("Dorsal (\\d+)").matcher(line);if(m.find()){String id=dorsals.get(side+":"+m.group(1));if(id!=null)totals.get(id)[key.equals("temporaryCards")?7:8]++;}}}}
                }catch(Exception ignored){}
            }
            final int checked=coverage,scored=scoreCoverage;
            runOnUiThread(()->{
                if(!panel.isAttachedToWindow())return;
                panel.removeAllViews();panel.addView(txt("Alineaciones: "+checked+"/"+matches.size()+" actas · Anotaciones conciliadas: "+scored+"/"+matches.size(),12,0xffb8d4cc));
                String[] metrics={"Convocatorias","Titularidades","Puntos","Ensayos","Transformaciones","Castigos","Drops","Amarillas","Rojas","Minutos verificados","Partidos jugados"};
                java.util.ArrayList<String> teamOptions=new java.util.ArrayList<>();teamOptions.add("Todos los equipos");teamOptions.addAll(teams);
                Spinner teamPicker=new Spinner(this);teamPicker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,teamOptions));panel.addView(teamPicker);
                Spinner picker=new Spinner(this);picker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,metrics));panel.addView(picker);
                LinearLayout results=new LinearLayout(this);results.setOrientation(1);panel.addView(results);
                Runnable redraw=()->{
                    int position=picker.getSelectedItemPosition();if(position<0)return;
                    int selectedTeam=teamPicker.getSelectedItemPosition();
                    results.removeAllViews();java.util.ArrayList<String> ids=new java.util.ArrayList<>(totals.keySet());ids.removeIf(k->totals.get(k)[position]<=0||(selectedTeam>0&&!teamKey(teamOptions.get(selectedTeam)).equals(teamKey(playerTeams.get(k)))));ids.sort((a,b)->{int c=Integer.compare(totals.get(b)[position],totals.get(a)[position]);return c!=0?c:labels.get(a).compareTo(labels.get(b));});
                    if(ids.isEmpty()){results.addView(txt("Sin registros positivos en las actas analizadas",13,0xffb8d4cc));return;}
                    int n=Math.min(10,ids.size());String[] names=new String[n];int[] values=new int[n],colors=new int[n];for(int i=0;i<n;i++){String k=ids.get(i);names[i]=""+(i+1);values[i]=totals.get(k)[position];colors[i]=position==7?0xffffc365:position==8?0xffed7979:0xff48dca2;}
                    standingColumns3D(results,names,values,colors);
                    for(int i=0;i<ids.size();i++){
                        String k=ids.get(i);int[] a=totals.get(k);
                        TextView row=txt((i==0?"🥇 ":i==1?"🥈 ":i==2?"🥉 ":(i+1)+". ")+labels.get(k)+" · "+a[position]+"  ›",14,0xffe2f5fa);
                        row.setPadding(dp(10),dp(12),dp(10),dp(12));row.setBackgroundColor(i%2==0?0xff203e50:0xff172b3c);results.addView(row);
                        row.setOnClickListener(v->{
                            StringBuilder detail=new StringBuilder();
                            for(int j=0;j<metrics.length;j++)detail.append(metrics[j]).append(": ").append(a[j]).append("\n");
                            detail.append("Actas con minutos verificados: ").append(a[11]).append("/").append(a[0]).append("\n\nHistorial de actas\n");
                            for(String match:history.getOrDefault(k,new java.util.ArrayList<>()))detail.append(match).append("\n");
                            new android.app.AlertDialog.Builder(this).setTitle(labels.get(k)).setMessage(detail.toString()).setPositiveButton("Cerrar",null).show();
                        });
                    }
                };
                AdapterView.OnItemSelectedListener filterListener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){redraw.run();}};
                picker.setOnItemSelectedListener(filterListener);teamPicker.setOnItemSelectedListener(filterListener);picker.setSelection(2);
                panel.addView(txt("Registros disponibles; las actas incompletas no equivalen a cero. Minutos parciales verificados; se excluyen sanciones y cambios no verificables. Partidos jugados según cambios legibles.",11,0xffb8d4cc));
            });
        }).start();
    }
    private void standingSignedDifference(LinearLayout target,String name,int value,int max){
        target.addView(txt(name+" · "+(value>0?"+":"")+value,13,0xffe2f5fa));
        View chart=new View(this){@Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float mid=getWidth()/2f,extent=(mid-dp(4))*Math.abs(value)/Math.max(1,max);android.graphics.Paint p=new android.graphics.Paint(3);p.setColor(0xff203e50);c.drawRoundRect(0,dp(3),getWidth(),getHeight()-dp(3),dp(6),dp(6),p);if(value!=0){p.setShader(new android.graphics.LinearGradient(0,0,0,getHeight(),value>0?0xffb2ffe2:0xffffc2c2,value>0?0xff159b70:0xffb94a4a,android.graphics.Shader.TileMode.CLAMP));c.drawRoundRect(value<0?mid-extent:mid,dp(3),value<0?mid:mid+extent,getHeight()-dp(3),dp(5),dp(5),p);p.setShader(null);}p.setColor(0xffe2f5fa);p.setStrokeWidth(dp(1));c.drawLine(mid,0,mid,getHeight(),p);}};
        target.addView(chart,new LinearLayout.LayoutParams(-1,dp(22)));
    }
    private void competitionStatistics(org.json.JSONObject data){
        java.util.TreeMap<String,String> names=new java.util.TreeMap<>();
        org.json.JSONArray tables=data.optJSONArray("tables");
        if(tables==null)return;
        for(int i=0;i<tables.length();i++){
            org.json.JSONObject t=tables.optJSONObject(i);
            if(t==null||!"matches".equals(t.optString("kind")))continue;
            org.json.JSONArray rows=t.optJSONArray("rows");if(rows==null)continue;
            for(int j=0;j<rows.length();j++){
                org.json.JSONObject g=rows.optJSONObject(j);if(g==null)continue;
                for(String side:new String[]{"home","away"}){String n=g.optString(side).trim();if(!n.isEmpty()&&!n.equalsIgnoreCase("Descanso"))names.put(teamKey(n),n);}
            }
        }
        java.util.ArrayList<String> teams=new java.util.ArrayList<>(names.values());
        if(teams.isEmpty()){body.addView(txt("Sin equipos importados",14,Color.WHITE));return;}
        if(selectedTab.equals("Ranking global")){
        standingSection(body,"Ranking global de jugadores");
        LinearLayout ranking=new LinearLayout(this);ranking.setOrientation(1);body.addView(ranking);
        competitionPlayerRanking(ranking,data,teams);
            return;
        }
        java.util.LinkedHashMap<String,int[]> stats=new java.util.LinkedHashMap<>();
        int games=0,points=0,draws=0;
        for(String team:teams){
            int[] a=new int[5];
            for(org.json.JSONObject g:standingTeamMatches(data,team)){if("Descanso".equalsIgnoreCase(g.optString("home").trim())||"Descanso".equalsIgnoreCase(g.optString("away").trim()))continue;
                int[] score=standingMatchScore(g.optString("score"));if(score==null)continue;
                boolean home=teamKey(team).equals(teamKey(g.optString("home")));
                int f=score[home?0:1],c=score[home?1:0];
                a[0]++;a[1]+=f;a[2]+=c;if(f>c)a[3]++;if(f==c)a[4]++;
                if(home){games++;points+=f+c;if(f==c)draws++;}
            }
            stats.put(team,a);
        }
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(1);body.addView(nav);
        LinearLayout section=new LinearLayout(this);section.setOrientation(1);
        String[] sections={"Resumen","Rankings","Evolución","Comparador"};
        TextView[] buttons=new TextView[4];
        final int[] activeSection={0};
        final int totalGames=games,totalPoints=points,totalDraws=draws;
        Runnable renderSection=()->{
            section.removeAllViews();
            for(int z=0;z<4;z++)buttons[z].setBackgroundColor(z==activeSection[0]?0xff08643d:0xff173c3b);
            if(activeSection[0]==0){
        standingSection(section,"Estadísticas de la competición");
        LinearLayout top=new LinearLayout(this);section.addView(top);
        standingMetric(top,""+teams.size(),"Equipos");standingMetric(top,""+totalGames,"Partidos");standingMetric(top,""+totalPoints,"Tantos");
        LinearLayout averages=new LinearLayout(this);section.addView(averages);
        standingMetric(averages,totalGames>0?String.format(java.util.Locale.getDefault(),"%.1f",totalPoints/(double)totalGames):"—","Tantos / partido");standingMetric(averages,""+totalDraws,"Empates");


        standingSection(section,"Victorias, empates y derrotas");
        int maxPlayed=1;for(int[] a:stats.values())maxPlayed=Math.max(maxPlayed,a[0]);
        for(String team:teams){int[] a=stats.get(team);section.addView(txt(team+" · "+a[0]+" PJ",13,0xff75e6b2));
            LinearLayout bars=new LinearLayout(this);section.addView(bars,new LinearLayout.LayoutParams(-1,dp(24)));
            int[] counts={a[3],a[4],a[0]-a[3]-a[4]};int[] colors={0xff48dca2,0xff56aaf2,0xffed7979};
            for(int z=0;z<3;z++){if(counts[z]==0)continue;TextView cell=txt(""+counts[z],12,0xff102233);cell.setGravity(android.view.Gravity.CENTER);cell.setBackgroundColor(colors[z]);bars.addView(cell,new LinearLayout.LayoutParams(0,-1,counts[z]));}
            if(a[0]<maxPlayed)bars.addView(new View(this),new LinearLayout.LayoutParams(0,-1,maxPlayed-a[0]));
        }
        section.addView(txt("Verde: victorias · Azul: empates · Rojo: derrotas",11,0xffb8d4cc));
        standingSection(section,"Rendimiento local y visitante");
        for(String team:teams){int[][] venue=new int[2][4];
            for(org.json.JSONObject g:standingTeamMatches(data,team)){int[] score=standingMatchScore(g.optString("score"));if(score==null)continue;boolean home=teamKey(team).equals(teamKey(g.optString("home")));int[] v=venue[home?0:1];int f=score[home?0:1],c=score[home?1:0];v[0]++;if(f>c)v[1]++;if(f==c)v[2]++;if(f<c)v[3]++;}
            section.addView(txt(team,13,0xff75e6b2));
            for(int z=0;z<2;z++){int[] v=venue[z];section.addView(txt((z==0?"Local: ":"Visitante: ")+v[0]+" PJ · "+v[1]+" V / "+v[2]+" E / "+v[3]+" D",12,0xffb8d4cc));}
        }

            }else if(activeSection[0]==1){
        for(int metric=0;metric<3;metric++){
            final int mode=metric;
            standingSection(section,metric==0?"Ranking de ataque":metric==1?"Ranking de defensa":"Diferencia de tantos");
            java.util.ArrayList<String> ordered=new java.util.ArrayList<>(teams);
            ordered.sort((x,y)->Integer.compare(mode==0?stats.get(y)[1]:mode==1?stats.get(x)[2]:stats.get(y)[1]-stats.get(y)[2],mode==0?stats.get(x)[1]:mode==1?stats.get(y)[2]:stats.get(x)[1]-stats.get(x)[2]));
            int max=1;for(String team:ordered){int[] a=stats.get(team);max=Math.max(max,Math.abs(mode==0?a[1]:mode==1?a[2]:a[1]-a[2]));}
            for(int i=0;i<ordered.size();i++){String team=ordered.get(i);int[] a=stats.get(team);int value=mode==0?a[1]:mode==1?a[2]:a[1]-a[2];
                if(mode==2){standingSignedDifference(section,(i+1)+". "+team,value,max);continue;} standingBar(section,(i+1)+". "+team+(mode==2?" ("+(value>0?"+":"")+value+")":""),Math.abs(value),max,mode==1?0xff56aaf2:value<0?0xffed7979:0xff48dca2);}
        }

            }else if(activeSection[0]==2){
        standingSection(section,"Evolución conjunta");
        Spinner evolutionPicker=new Spinner(this);evolutionPicker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tantos a favor","Tantos en contra","Diferencia de tantos","Victorias acumuladas"}));chartHost.addView(evolutionPicker);
        LinearLayout chartHost=new LinearLayout(this);chartHost.setOrientation(1);section.addView(chartHost);
        Runnable drawEvolution=()->{chartHost.removeAllViews();int metric=evolutionPicker.getSelectedItemPosition();
        final java.util.ArrayList<int[]> curves=new java.util.ArrayList<>();
        final int[] palette={0xff48dca2,0xff56aaf2,0xffffc365,0xffed7979,0xffc4a0ff,0xfff4a6d7,0xff9de2e2,0xffe5e5e5};
        int maxGames=1,maxPoints=1,minPoints=0;
        for(String team:teams){java.util.ArrayList<Integer> pointsList=new java.util.ArrayList<>();pointsList.add(0);int sum=0;
            for(org.json.JSONObject g:standingTeamMatches(data,team)){int[] score=standingMatchScore(g.optString("score"));if(score==null)continue;boolean home=teamKey(team).equals(teamKey(g.optString("home")));int f=score[home?0:1],c=score[home?1:0];sum+=metric==1?c:metric==2?f-c:metric==3?(f>c?1:0):f;pointsList.add(sum);maxPoints=Math.max(maxPoints,sum);minPoints=Math.min(minPoints,sum);}
            int[] curve=new int[pointsList.size()];for(int i=0;i<curve.length;i++)curve[i]=pointsList.get(i);curves.add(curve);maxGames=Math.max(maxGames,curve.length-1);maxPoints=Math.max(maxPoints,sum);
        }
        final int xMax=maxGames,yMax=maxPoints,yMin=minPoints;
        android.view.View evolution=new android.view.View(this){
            @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);
                android.graphics.Paint p=new android.graphics.Paint(3);float left=dp(38),right=getWidth()-dp(12),top=dp(15),bottom=getHeight()-dp(28);
                p.setTextSize(dp(10));for(int i=0;i<=4;i++){float y=bottom-(bottom-top)*i/4f;p.setColor(0xff45677d);p.setStrokeWidth(dp(1));canvas.drawLine(left,y,right,y,p);p.setColor(0xffb8d4cc);canvas.drawText(""+Math.round(yMin+(yMax-yMin)*i/4f),dp(2),y,p);}
                for(int i=0;i<=xMax;i++){if(xMax>12&&i%((xMax+11)/12)!=0&&i!=xMax)continue;float x=left+(right-left)*i/xMax;p.setColor(0xffb8d4cc);canvas.drawText(""+i,x-dp(3),bottom+dp(16),p);}
                for(int k=0;k<curves.size();k++){int[] c=curves.get(k);p.setColor(palette[k%palette.length]);p.setStrokeWidth(dp(2));for(int i=0;i<c.length;i++){float x=left+(right-left)*i/xMax,y=bottom-(bottom-top)*(c[i]-yMin)/(yMax-yMin);if(i>0)canvas.drawLine(left+(right-left)*(i-1)/xMax,bottom-(bottom-top)*(c[i-1]-yMin)/(yMax-yMin),x,y,p);canvas.drawCircle(x,y,dp(3),p);}}
            }
        };chartHost.addView(evolution,new LinearLayout.LayoutParams(-1,dp(220)));
        for(int i=0;i<teams.size();i++)chartHost.addView(txt("● "+teams.get(i),12,palette[i%palette.length]));
        chartHost.addView(txt("Eje horizontal: número de partidos disputados por cada equipo.",11,0xffb8d4cc));
        };
        evolutionPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){drawEvolution.run();}});drawEvolution.run();

            }else{
        TextView compareButton=txt("Comparar dos equipos  ›",15,Color.WHITE);compareButton.setGravity(android.view.Gravity.CENTER);compareButton.setPadding(dp(8),dp(12),dp(8),dp(12));compareButton.setBackgroundColor(0xff173c3b);section.addView(compareButton);
        compareButton.setOnClickListener(v->{
        android.widget.ScrollView compareScroll=new android.widget.ScrollView(this);
        LinearLayout comparisonHost=new LinearLayout(this);comparisonHost.setOrientation(1);comparisonHost.setPadding(dp(12),dp(8),dp(12),dp(8));compareScroll.addView(comparisonHost);
        Spinner first=new Spinner(this),second=new Spinner(this);
        first.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,teams));
        second.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,teams));
        comparisonHost.addView(first);comparisonHost.addView(second);second.setSelection(Math.min(1,teams.size()-1));
        LinearLayout comparison=new LinearLayout(this);comparison.setOrientation(1);comparisonHost.addView(comparison);
        Runnable redraw=()->{
            comparison.removeAllViews();
            int i=first.getSelectedItemPosition(),j=second.getSelectedItemPosition();if(i<0||j<0)return;
            String x=teams.get(i),y=teams.get(j);int[] a=stats.get(x),b=stats.get(y);
            comparison.addView(txt(x+" (A) · "+y+" (B)",13,0xffb8d4cc));
            standingColumns3D(comparison,new String[]{"A favor","B favor","A contra","B contra"},new int[]{a[1],b[1],a[2],b[2]},new int[]{0xff48dca2,0xff56aaf2,0xff48dca2,0xff56aaf2});
            for(String team:new String[]{x,y}){
                standingSection(comparison,team);
                java.util.ArrayList<Integer> f=new java.util.ArrayList<>(),c=new java.util.ArrayList<>();
                for(org.json.JSONObject g:standingTeamMatches(data,team)){int[] score=standingMatchScore(g.optString("score"));if(score==null)continue;boolean home=teamKey(team).equals(teamKey(g.optString("home")));f.add(score[home?0:1]);c.add(score[home?1:0]);}
                if(!f.isEmpty())standingEvolutionChart(comparison,f,c);
            }
        };
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){redraw.run();}};
        first.setOnItemSelectedListener(listener);second.setOnItemSelectedListener(listener);redraw.run();

        new android.app.AlertDialog.Builder(this).setTitle("Comparador de equipos").setView(compareScroll).setPositiveButton("Cerrar",null).show();
        });

            }
        };
        for(int z=0;z<4;z++){final int index=z;LinearLayout row;if(z%2==0){row=new LinearLayout(this);nav.addView(row);}else row=(LinearLayout)nav.getChildAt(nav.getChildCount()-1);
            TextView button=txt(sections[z],14,Color.WHITE);buttons[z]=button;button.setGravity(android.view.Gravity.CENTER);button.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(button,lp);button.setOnClickListener(v->{activeSection[0]=index;renderSection.run();});}
        body.addView(section);renderSection.run();
        body.addView(txt("Totales de marcadores importados. Los equipos pueden tener distinto número de partidos.",12,0xffb8d4cc));
    }
    void renderNative(org.json.JSONObject data)throws org.json.JSONException{
        if(selectedTab.equals("Estadísticas")||selectedTab.equals("Ranking global")){competitionStatistics(data);return;}
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
                ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options){
                    @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                        TextView selected=txt(options.get(position),15,Color.WHITE);
                        selected.setTypeface(null,Typeface.BOLD);
                        selected.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        selected.setPadding(dp(18),0,dp(36),0);
                        return selected;
                    }
                };
                picker.setAdapter(adapter);picker.setSelection(selectedMatchGroup+1);
                boolean resultPicker=selectedTab.equals("Resultados");
                android.graphics.drawable.GradientDrawable pickerBg=new android.graphics.drawable.GradientDrawable();
                pickerBg.setColor(resultPicker?0xff3b0b19:0xff053c2d);
                pickerBg.setCornerRadius(dp(22));
                pickerBg.setStroke(dp(1.4f),resultPicker?0xffb74459:0xff19a86b);
                android.widget.FrameLayout pickerFrame=new android.widget.FrameLayout(this);
                pickerFrame.setBackground(pickerBg);
                picker.setBackgroundColor(Color.TRANSPARENT);
                pickerFrame.addView(picker,new android.widget.FrameLayout.LayoutParams(-1,-1));
                TextView chevron=txt("",19,Color.WHITE);
                chevron.setGravity(android.view.Gravity.CENTER);
                chevron.setCompoundDrawables(null,null,lineIcon("chevron"),null);
                chevron.setPadding(0,0,dp(12),0);
                android.widget.FrameLayout.LayoutParams arrowLp=new android.widget.FrameLayout.LayoutParams(dp(43),-1,android.view.Gravity.RIGHT);
                pickerFrame.addView(chevron,arrowLp);
                chevron.setOnClickListener(v->picker.performClick());
                LinearLayout.LayoutParams pickerParams=new LinearLayout.LayoutParams(-1,dp(resultPicker?35:39));
                pickerParams.setMargins(0,dp(8),0,dp(8));
                body.addView(pickerFrame,pickerParams);
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
                if(m14Legend)body.addView(txt(detectedPhase==0?"Clasificación · fase sin confirmar":detectedPhase==1?"Clasificación · 1.ª fase":"Clasificación · 2.ª fase",14,detectedPhase==0?Color.LTGRAY:accent));
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
                    // The real classification is rendered by MainActivity, not RugbyHubActivity.
                    // Attach the interaction to the exact team cell (crest AND name).
                    final String selectedStandingTeam=vals[1];
                    final String[] selectedStandingValues=vals.clone();
                    android.view.View teamCell=line.getChildAt(line.getChildCount()-1);
                    teamCell.setClickable(true);
                    teamCell.setFocusable(true);
                    teamCell.setContentDescription("Ver estadísticas de "+selectedStandingTeam);
                    teamCell.setOnClickListener(v->showStandingTeamStatistics(selectedStandingTeam,selectedStandingValues,data));
                    if(teamCell instanceof android.view.ViewGroup){
                        android.view.ViewGroup teamChildren=(android.view.ViewGroup)teamCell;
                        for(int child=0;child<teamChildren.getChildCount();child++){
                            android.view.View target=teamChildren.getChildAt(child);
                            target.setClickable(true);
                            target.setOnClickListener(v->showStandingTeamStatistics(selectedStandingTeam,selectedStandingValues,data));
                        }
                    }
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
                    body.addView(txt("LEYENDA · PUESTOS ORIENTATIVOS",12,accent));
                    if(detectedPhase==1&&selectedCompetition.equals("Primera División")){
                        body.addView(txt("● Verde (1.º–5.º): acceso previsto a Primera en 2.ª fase.",11,0xff66e1a4));
                        body.addView(txt("● Amarillo (6.º): triangular de promoción.",11,0xffffca65));
                    }else if(detectedPhase==1&&selectedCompetition.startsWith("Segunda División · Grupo")){
                        body.addView(txt("● Amarillo (1.º): triangular de promoción.",11,0xffffca65));
                        body.addView(txt("● Verde (2.º–3.º): acceso previsto a Segunda A.",11,0xff66e1a4));
                        body.addView(txt("● Gris (4.º–6.º): acceso previsto a Segunda B.",11,0xff9db0c0));
                    }else if(detectedPhase==2){
                        if(selectedCompetition.equals("Primera División"))body.addView(txt("● Amarillo (6.º): promoción final frente al primero de Segunda A.",11,0xffffca65));
                        else if((selectedCompetition.toLowerCase(java.util.Locale.ROOT).contains("segunda a")||selectedCompetition.toLowerCase(java.util.Locale.ROOT).contains("segunda división a")))body.addView(txt("● Amarillo (1.º): promoción final frente al sexto de Primera.",11,0xffffca65));
                        else body.addView(txt("Sin colores de promoción para este grupo.",12,Color.LTGRAY));
                    }else{
                        body.addView(txt("No se puede confirmar la fase a partir de las etiquetas oficiales disponibles. Actualiza los datos para volver a comprobarlo.",12,Color.LTGRAY));
                    }
                    org.json.JSONObject smart=StandingIntelligence.compactAnalysis(season,category,selectedCompetition,detectedPhase,rows,data);
                    if(smart!=null){
                        org.json.JSONArray options=smart.optJSONArray("teams");
                        int cutoff=selectedCompetition.equals("Primera División")?5:3;
                        if(options!=null&&options.length()>cutoff){
                            org.json.JSONObject border=options.optJSONObject(cutoff-1);
                            org.json.JSONObject below=options.optJSONObject(cutoff);
                            if(border!=null&&below!=null){
                                int difference=border.optInt("points")-below.optInt("points");
                                body.addView(txt("ANÁLISIS INTELIGENTE",12,accent));
                                body.addView(txt("Diferencia en el corte: "+difference+" puntos · "+border.optString("team")+" / "+below.optString("team"),11,0xffa5dbc6));
                            }
                            LinearLayout columns=new LinearLayout(this);
                            columns.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            columns.setBackgroundColor(panel);
                            body.addView(columns);
                            addStandingFixed(columns,"Equipo",dp(150),accent);
                            addStandingFixed(columns,"Rest.",dp(46),accent);
                            addStandingFixed(columns,"Pts",dp(45),accent);
                            addStandingFixed(columns,"Máx.",dp(45),accent);
                            addStandingFixed(columns,"Puesto*",dp(72),accent);
                            final org.json.JSONObject detailData=data;
                            final java.util.ArrayList<TextView> simulatedCells=new java.util.ArrayList<>();
                            final java.util.ArrayList<LinearLayout> simulatedRows=new java.util.ArrayList<>();
                            final java.util.HashMap<String,org.json.JSONObject> teamSimulations=new java.util.HashMap<>();
                            final java.util.ArrayList<String> simulatedNames=new java.util.ArrayList<>();
                            for(int n=0;n<options.length();n++){
                                org.json.JSONObject option=options.optJSONObject(n);
                                if(option==null)continue;
                                LinearLayout compactRow=new LinearLayout(this);
                                compactRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                compactRow.setBackgroundColor(n%2==0?panel:bg);
                                body.addView(compactRow);
                                addStandingFixed(compactRow,option.optString("team"),dp(150),Color.WHITE);
                                addStandingFixed(compactRow,String.valueOf(option.optInt("remaining")),dp(46),Color.WHITE);
                                addStandingFixed(compactRow,String.valueOf(option.optInt("points")),dp(45),Color.WHITE);
                                addStandingFixed(compactRow,String.valueOf(option.optInt("maximum")),dp(45),accent);
                                TextView positionCell=txt(option.optInt("best")+"–"+option.optInt("worst"),11,0xffa5dbc6);
                                positionCell.setWidth(dp(72));
                                compactRow.addView(positionCell);
                                simulatedCells.add(positionCell);
                                simulatedRows.add(compactRow);
                                final String tappedTeam=option.optString("team");
                                compactRow.setClickable(true);
                                compactRow.setOnClickListener(v->{
                                    org.json.JSONObject details=teamSimulations.get(tappedTeam);
                                    if(details==null){
                                        new android.app.AlertDialog.Builder(this).setTitle(tappedTeam).setMessage("Calculando escenarios. Vuelve a pulsar cuando termine la simulación.").setPositiveButton("Cerrar",null).show();
                                        return;
                                    }
                                    org.json.JSONArray distribution=details.optJSONArray("positions");
                                    if(distribution==null)return;
                                    StringBuilder probabilities=new StringBuilder();
                                    int direct=0;
                                    for(int rank=0;rank<distribution.length();rank++){
                                        int occurrences=distribution.optInt(rank);
                                        if(rank<5&&selectedCompetition.equals("Primera División"))direct+=occurrences;
                                        probabilities.append(rank+1).append(".º puesto: ")
                                                .append(String.format(java.util.Locale.getDefault(),"%.1f",occurrences*100.0/3000.0))
                                                .append("%\n");
                                    }
                                    String estimated=String.format(java.util.Locale.getDefault(),"%.1f",direct*100.0/3000.0);
                                    int possibleAhead=0;
                                    int currentPoints=option.optInt("points");
                                    StringBuilder above=new StringBuilder();
                                    StringBuilder challengers=new StringBuilder();
                                    for(int rivalIndex=0;rivalIndex<options.length();rivalIndex++){
                                        org.json.JSONObject rival=options.optJSONObject(rivalIndex);
                                        if(rival==null||rival==option)continue;
                                        int rivalPoints=rival.optInt("points");
                                        int rivalMaximum=rival.optInt("maximum");
                                        String entry="• "+rival.optString("team")+" · "+rivalPoints+
                                                " pts (máximo "+rivalMaximum+")";
                                        // Para garantizar el acceso directo, contamos a todos
                                        // los equipos capaces de igualar los puntos actuales.
                                        if(rivalMaximum>=currentPoints)possibleAhead++;
                                        if(rivalPoints>currentPoints){
                                            if(above.length()>0)above.append("\n");
                                            above.append(entry);
                                        }else if(rivalPoints==currentPoints){
                                            if(challengers.length()>0)challengers.append("\n");
                                            challengers.append(entry).append(" · empatado actualmente");
                                        }else if(rivalMaximum>=currentPoints){
                                            if(challengers.length()>0)challengers.append("\n");
                                            challengers.append(entry);
                                        }
                                    }
                                    String rivals="YA ESTÁN POR DELANTE\n"+
                                            (above.length()>0?above.toString():"Ninguno")+
                                            "\n\nPUEDEN ADELANTARLO O EMPATARLO\n"+
                                            (challengers.length()>0?challengers.toString():"Ninguno según los máximos de puntos.");
                                    boolean assured=selectedCompetition.equals("Primera División")&&possibleAhead<5;
                                    String qualification=selectedCompetition.equals("Primera División")?
                                            "Acceso directo estimado: "+estimated+"%\nClasificación matemática: "+
                                            (assured?"ASEGURADA por límite de puntos.":"NO CONFIRMADA.")+
                                            "\n\nEl 100 % en simulaciones no implica garantía matemática.":
                                            "Los porcentajes son estimaciones, no plazas matemáticamente confirmadas.";
                                    org.json.JSONArray pending=StandingIntelligence.pendingMatches(tappedTeam,detailData);
                                    android.widget.ScrollView scroll=new android.widget.ScrollView(this);
                                    LinearLayout sections=new LinearLayout(this);
                                    sections.setOrientation(LinearLayout.VERTICAL);
                                    sections.setPadding(dp(12),dp(8),dp(12),dp(8));
                                    scroll.addView(sections);
                                    addAnalysisFolder(sections,"Probabilidades por puesto",probabilities.toString(),true);
                                    addAnalysisFolder(sections,"Clasificación y acceso",qualification,false);
                                    addPendingMatchFolder(sections,pending,option.optInt("points"),option.optInt("remaining"));
                                    addCombinedMatchSimulator(sections,pending,option.optInt("points"),option.optInt("remaining"),options,tappedTeam);
                                    addAnalysisFolder(sections,"Rivales directos",rivals,false);
                                    addAnalysisFolder(sections,"Cómo se calcula",
                                            "3.000 simulaciones ponderadas por puntos por partido y tanteos anteriores disponibles. Los empates se resuelven aleatoriamente. No son probabilidades oficiales ni sustituyen los desempates FAR.",false);
                                    new android.app.AlertDialog.Builder(this).setTitle(tappedTeam+" · Análisis inteligente")
                                            .setView(scroll).setPositiveButton("Cerrar",null).show();
                                });
                                simulatedNames.add(option.optString("team"));
                            }
                            final TextView simulationStatus=txt("◌ Calculando escenarios…",11,0xffffca65);
                            body.addView(simulationStatus);
                            final TextView simulationNote=txt("* Puestos orientativos por límites de puntos hasta completar el cálculo.",10,Color.LTGRAY);
                            body.addView(simulationNote);
                            final String simSeason=season,simCategory=category,simCompetition=selectedCompetition;
                            final int simPhase=detectedPhase;
                            final org.json.JSONArray simRows=rows;
                            final org.json.JSONObject simData=data;
                            final LinearLayout originalBody=body;
                            new Thread(()->{
                                try{
                                    final org.json.JSONObject simulation=StandingIntelligence.simulatedPositions(simSeason,simCategory,simCompetition,simPhase,simRows,simData);
                                    runOnUiThread(()->{
                                        if(body!=originalBody||!originalBody.isAttachedToWindow())return;
                                        if(simulation==null){
                                            simulationStatus.setText("⚠ Simulación no disponible");
                                            simulationNote.setText("* Se mantienen los puestos orientativos por límites de puntos.");
                                            return;
                                        }
                                        org.json.JSONArray simulated=simulation.optJSONArray("teams");
                                        if(simulated==null)return;
                                        java.util.HashMap<String,String> ranges=new java.util.HashMap<>();
                                        for(int simIndex=0;simIndex<simulated.length();simIndex++){
                                            org.json.JSONObject item=simulated.optJSONObject(simIndex);
                                            if(item!=null){
                                                ranges.put(item.optString("team"),item.optInt("best")+"–"+item.optInt("worst"));
                                                teamSimulations.put(item.optString("team"),item);
                                            }
                                        }
                                        for(int cellIndex=0;cellIndex<simulatedCells.size();cellIndex++){
                                            String range=ranges.get(simulatedNames.get(cellIndex));
                                            if(range!=null)simulatedCells.get(cellIndex).setText(range);
                                        }
                                        simulationStatus.setText("✓ Simulación completada · 3.000 escenarios · Pulsa un equipo");
                                        simulationStatus.setTextColor(accent);
                                        simulationNote.setText("* Puestos observados en simulaciones de resultados compatibles. No son cotas matemáticas exhaustivas; desempates y resoluciones FAR pendientes.");
                                    });
                                }catch(Throwable error){
                                    runOnUiThread(()->{
                                        if(body==originalBody&&originalBody.isAttachedToWindow())
                                            { simulationStatus.setText("⚠ Simulación no disponible");
                                            simulationNote.setText("* Se mantienen los puestos orientativos por límites de puntos."); }
                                    });
                                }
                            },"far-standings-simulation").start();
                        }
                    }
                    body.addView(txt("Puestos orientativos, sujetos a sanciones, renuncias y resoluciones de la FAR.",11,Color.LTGRAY));
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
                String sectionTitle=roundTitle;
                boolean redRound=(i%2==0);
                int borderColor=redRound?0xffbc4458:0xff25a66b;
                int headerColor=redRound?0xff4b0c19:0xff063c2d;
                LinearLayout roundCard=new LinearLayout(this);roundCard.setOrientation(LinearLayout.VERTICAL);
                android.graphics.drawable.GradientDrawable roundBg=new android.graphics.drawable.GradientDrawable();
                roundBg.setColor(0xff08221f);roundBg.setCornerRadius(17*getResources().getDisplayMetrics().density);
                roundBg.setStroke((int)(1.3f*getResources().getDisplayMetrics().density),borderColor);
                roundCard.setBackground(roundBg);roundCard.setClipToOutline(true);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,8,0,10);body.addView(roundCard,rp);
                String firstDay="";
                for(int j=0;j<rows.length();j++){
                    org.json.JSONObject candidate=rows.optJSONObject(j);
                    if(candidate!=null&&matchVisible(candidate)){
                        java.util.regex.Matcher dm=java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(candidate.optString("when",""));
                        firstDay=dm.find()?dm.group(1):matchDayLabel(candidate.optString("when",""));
                        break;
                    }
                }
                float density=getResources().getDisplayMetrics().density;
                LinearLayout headingRow=new LinearLayout(this);headingRow.setOrientation(LinearLayout.HORIZONTAL);
                headingRow.setGravity(android.view.Gravity.CENTER_VERTICAL);headingRow.setPadding(12,9,12,9);
                headingRow.setBackgroundColor(headerColor);roundCard.addView(headingRow);
                TextView headingText=txt("",15,Color.WHITE);headingText.setText(iconLabel("calendar",sectionTitle));headingText.setTypeface(null,Typeface.BOLD);
                headingRow.addView(headingText,new LinearLayout.LayoutParams(0,-2,1));
                View separator=new View(this);
                separator.setBackgroundColor(borderColor);
                LinearLayout.LayoutParams separatorParams=new LinearLayout.LayoutParams(Math.max(1,(int)density),(int)(22*density));
                separatorParams.setMargins((int)(5*density),0,(int)(12*density),0);
                headingRow.addView(separator,separatorParams);
                TextView dateText=txt("",13,Color.WHITE);dateText.setText(iconLabel("calendar",firstDay));dateText.setTypeface(null,Typeface.BOLD);
                dateText.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL);
                headingRow.addView(dateText);
                if(organizer>=0){
                    TextView organizerView=txt("",12,Color.WHITE);organizerView.setText(iconLabel("organizer","Organiza: "+full.substring(organizer+"organizador:".length()).replaceFirst("^\\s*[:\\-]\\s*","").trim()));
                    organizerView.setBackgroundColor(0xff062b27);
                    organizerView.setPadding(12,10,10,10);roundCard.addView(organizerView);
                }
                String lastDate="";
                for(int j=0;j<rows.length();j++){
                    org.json.JSONObject row=rows.optJSONObject(j);if(row==null)continue;
                    String score=row.optString("score","");
                    if(!matchVisible(row))continue;
                    String dateLabel=matchDayLabel(row.optString("when",""));
                    if(!dateLabel.equals(lastDate)){
                        if(!lastDate.isEmpty()){
                            TextView dateView=txt(dateLabel,11,accent);
                            dateView.setPadding(12,7,0,4);roundCard.addView(dateView);
                        }
                        lastDate=dateLabel;
                    }
                    LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(8,5,8,5);card.setBackgroundColor(panel);
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(8,1,8,2);roundCard.addView(card,cp);
                    LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(line);
                    LinearLayout left=matchTeam(row.optString("home",""),matchCrest(tables,row.optString("home",""),row.optString("homeCrest","")),false);
                    boolean finished=selectedTab.equals("Resultados")&&isScore(score);
                    TextView mid=txt(selectedTab.equals("Calendario")&&isUpcomingMatch(row)?"VS":isScore(score)?score:"VS",finished?20:19,finished?Color.WHITE:selectedTab.equals("Calendario")&&!isUpcomingMatch(row)?Color.LTGRAY:accent);
                    mid.setTypeface(null,Typeface.BOLD);mid.setGravity(android.view.Gravity.CENTER);
                    mid.setMinWidth((int)(getResources().getDisplayMetrics().density*(finished?72:52)));
                    if(finished){
                        android.graphics.drawable.GradientDrawable scoreBg=new android.graphics.drawable.GradientDrawable();
                        scoreBg.setColor(Color.rgb(30,83,77));
                        scoreBg.setCornerRadius(9*getResources().getDisplayMetrics().density);
                        mid.setBackground(scoreBg);
                        mid.setPadding(dp(7),dp(5),dp(7),dp(5));
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
                            TextView time=txt(hour+" h",9,Color.LTGRAY);
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
        else {TextView source=txt("MatchReady · Datos guardados en el móvil",10,0xff8ca6a2);source.setPadding(dp(8),dp(9),0,dp(4));body.addView(source);}
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
                out.append("Página: ").append(info.optString("titulo")).append("\n");
                out.append("URL: ").append(info.optString("url")).append("\n");
                out.append("Tablas: ").append(info.optJSONArray("tablas")!=null?info.optJSONArray("tablas").length():0).append("\n");
                out.append("Encabezados: ").append(info.optJSONArray("encabezados")).append("\n");
                out.append("Columnas y filas: ").append(info.optJSONArray("tablas")).append("\n");
                out.append("Marcos: ").append(info.optJSONArray("iframes"));
                lastInspectedStructure=out.toString();
                new android.app.AlertDialog.Builder(this).setTitle("Estructura detectada").setMessage(lastInspectedStructure).setPositiveButton("Cerrar",null).show();
            }catch(Exception ex){lastInspectedStructure="No se ha podido analizar la página: "+ex.getMessage();Toast.makeText(this,lastInspectedStructure,Toast.LENGTH_LONG).show();}
        });
    }
    @Override public void onBackPressed(){if(browser!=null&&browser.canGoBack())browser.goBack();else if(page.equals("browser")&&!selectedCompetition.isEmpty())showCompetitionTab(selectedTab);else home();}
    @Override protected void onDestroy(){if(browser!=null)browser.destroy();if(dataBrowser!=null)dataBrowser.destroy();super.onDestroy();}
    private void addAnalysisFolder(LinearLayout container,String title,String content,boolean expanded){
        LinearLayout group=new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        container.addView(group);
        TextView header=txt((expanded?"▾ ":"▸ ")+title,14,accent);
        header.setPadding(dp(8),dp(12),dp(8),dp(12));
        header.setBackgroundColor(panel);
        group.addView(header);
        TextView details=txt(content,13,Color.WHITE);
        details.setPadding(dp(12),dp(10),dp(10),dp(14));
        details.setVisibility(expanded?View.VISIBLE:View.GONE);
        group.addView(details);
        header.setOnClickListener(v->{
            boolean open=details.getVisibility()!=View.VISIBLE;
            details.setVisibility(open?View.VISIBLE:View.GONE);
            header.setText((open?"▾ ":"▸ ")+title);
        });
    }

    private void addPendingMatchFolder(LinearLayout container,org.json.JSONArray matches,int currentPoints,int remaining){
        LinearLayout group=new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        container.addView(group);
        TextView header=txt("▸ Partidos pendientes ("+matches.length()+")",14,accent);
        header.setPadding(dp(8),dp(12),dp(8),dp(12));
        header.setBackgroundColor(panel);
        group.addView(header);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6),dp(8),dp(6),dp(8));
        content.setVisibility(View.GONE);
        group.addView(content);
        if(matches.length()==0)
            content.addView(txt("No se han encontrado partidos pendientes en el calendario importado.",12,Color.LTGRAY));

        // Mantener el orden del calendario y agrupar por jornada real,
        // sin fusionar encuentros repetidos de distintas fechas.
        java.util.LinkedHashMap<String,java.util.ArrayList<org.json.JSONObject>> grouped=new java.util.LinkedHashMap<>();
        for(int index=0;index<matches.length();index++){
            org.json.JSONObject match=matches.optJSONObject(index);
            if(match==null)continue;
            String round=match.optString("round","").trim();
            String groupKey=round.isEmpty()?"Jornada sin indicar":round;
            if(!grouped.containsKey(groupKey))
                grouped.put(groupKey,new java.util.ArrayList<org.json.JSONObject>());
            grouped.get(groupKey).add(match);
        }
        int number=0;
        for(java.util.Map.Entry<String,java.util.ArrayList<org.json.JSONObject>> entry:grouped.entrySet()){
            String fullHeading=entry.getKey();
            String roundTitle=fullHeading;
            String organizer="";
            int organizerIndex=fullHeading.toLowerCase(java.util.Locale.ROOT).indexOf("organizador:");
            if(organizerIndex>=0){
                roundTitle=fullHeading.substring(0,organizerIndex).replaceAll("[\\s.·–-]+$","").trim();
                organizer=fullHeading.substring(organizerIndex+"organizador:".length()).trim();
            }
            LinearLayout section=new LinearLayout(this);
            section.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams sectionParams=new LinearLayout.LayoutParams(-1,-2);
            sectionParams.bottomMargin=dp(8);
            content.addView(section,sectionParams);
            TextView roundHeader=txt(roundTitle,13,accent);
            roundHeader.setPadding(dp(8),dp(9),dp(8),dp(3));
            section.addView(roundHeader);
            java.util.LinkedHashSet<String> dates=new java.util.LinkedHashSet<>();
            for(org.json.JSONObject fixture:entry.getValue()){
                String date=fixture.optString("when","").trim();
                if(!date.isEmpty())dates.add(date);
            }
            StringBuilder meta=new StringBuilder();
            if(!organizer.isEmpty())meta.append("Organizador: ").append(organizer);
            if(!dates.isEmpty()){
                if(meta.length()>0)meta.append(" · ");
                int n=0;
                for(String date:dates){
                    if(n++>0)meta.append(", ");
                    meta.append(date);
                }
            }
            if(meta.length()>0){
                TextView org=txt(meta.toString(),11,Color.LTGRAY);
                org.setPadding(dp(8),0,dp(8),dp(6));
                section.addView(org);
            }
            for(org.json.JSONObject match:entry.getValue()){
                number++;
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(10),dp(8),dp(10),dp(8));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
                params.bottomMargin=dp(3);
                section.addView(card,params);
                card.setBackgroundColor(panel);
                String opponent=match.optString("opponent","Rival pendiente");
                card.addView(txt(number+". "+(match.optBoolean("home")?"Local · ":"Visitante · ")+opponent,12,Color.WHITE));
                String when=match.optString("when","").trim();
                String detail="Puntos posibles: 0–5";
                card.addView(txt(detail,11,accent));
                TextView toggle=txt("▸ Probar resultado",11,accent);
                toggle.setPadding(0,dp(6),0,dp(3));
                card.addView(toggle);
                LinearLayout simulation=new LinearLayout(this);
                simulation.setOrientation(LinearLayout.VERTICAL);
                simulation.setVisibility(View.GONE);
                card.addView(simulation);
                TextView explanation=txt("Selecciona un resultado para ver los puntos del equipo. Es un cálculo hipotético, no modifica resultados oficiales ni los porcentajes de las 3.000 simulaciones.",11,Color.LTGRAY);
                simulation.addView(explanation);
                TextView impact=txt("",12,Color.WHITE);
                impact.setPadding(0,dp(5),0,dp(6));
                simulation.addView(impact);
                LinearLayout choices=new LinearLayout(this);
                choices.setOrientation(LinearLayout.HORIZONTAL);
                simulation.addView(choices);
                final String[] outcomes={"Victoria","Empate","Derrota"};
                for(int k=0;k<outcomes.length;k++){
                    final int outcome=k;
                    TextView choice=txt(outcomes[k],11,accent);
                    choice.setPadding(dp(5),dp(9),dp(5),dp(9));
                    LinearLayout.LayoutParams choiceParams=new LinearLayout.LayoutParams(0,-2,1);
                    choices.addView(choice,choiceParams);
                    choice.setOnClickListener(v->{
                        int earned=outcome==0?4:outcome==1?2:0;
                        int pointsAfter=currentPoints+earned;
                        int otherGames=Math.max(0,remaining-1);
                        int possibleMax=pointsAfter+5*otherGames;
                        impact.setText("Puntos: "+currentPoints+" → "+pointsAfter+
                                " (+"+earned+")\nMáximo restante: "+possibleMax+
                                "\nVictoria: 4–5 pts; empate: 2–3 pts; derrota: 0–2 pts, según bonus. "+
                                "La selección usa el mínimo sin bonus.");
                    });
                }
                toggle.setOnClickListener(v->{
                    boolean open=simulation.getVisibility()!=View.VISIBLE;
                    simulation.setVisibility(open?View.VISIBLE:View.GONE);
                    toggle.setText((open?"▾ ":"▸ ")+"Probar resultado");
                });
            }
        }
        header.setOnClickListener(v->{
            boolean open=content.getVisibility()!=View.VISIBLE;
            content.setVisibility(open?View.VISIBLE:View.GONE);
            header.setText((open?"▾ ":"▸ ")+"Partidos pendientes ("+matches.length()+")");
        });
    }

    /** Independent what-if model: each fixture contributes 0..5 league points. */
    private void addCombinedMatchSimulator(LinearLayout container,org.json.JSONArray matches,int currentPoints,int remaining,org.json.JSONArray standings,String teamName){
        LinearLayout group=new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        container.addView(group);
        TextView header=txt("▸ Simular mis partidos",14,accent);
        header.setPadding(dp(8),dp(12),dp(8),dp(12));
        header.setBackgroundColor(panel);
        group.addView(header);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10),dp(10),dp(10),dp(10));
        content.setVisibility(View.GONE);
        group.addView(content);
        if(matches.length()==0){
            content.addView(txt("No hay partidos pendientes disponibles para simular.",12,Color.LTGRAY));
        }else{
            final int count=matches.length();
            final int[] chosen=new int[count];
            java.util.Arrays.fill(chosen,-1);
            TextView summary=txt("",13,Color.WHITE);
            summary.setPadding(0,dp(6),0,dp(10));
            content.addView(summary);
            TextView ranking=txt("",12,accent);
            ranking.setPadding(0,0,0,dp(8));
            content.addView(ranking);
            Runnable update=()->{
                int assigned=0,total=0;
                for(int points:chosen)if(points>=0){assigned++;total+=points;}
                int unknown=Math.max(0,remaining-assigned);
                int projected=currentPoints+total;
                int ceiling=projected+unknown*5;
                summary.setText("Puntos actuales: "+currentPoints+
                    "\nPuntos simulados: +"+total+
                    "\nTotal provisional: "+projected+
                    "\nPartidos sin simular: "+unknown+
                    "\nMáximo alcanzable: "+ceiling);
                int guaranteedAhead=0,possiblyAhead=0;
                if(standings!=null){
                    for(int i=0;i<standings.length();i++){
                        org.json.JSONObject rival=standings.optJSONObject(i);
                        if(rival==null||rival.optString("team","").equalsIgnoreCase(teamName))continue;
                        int rivalPoints=rival.optInt("points",0);
                        int rivalMaximum=rival.optInt("maximum",rivalPoints);
                        if(rivalPoints>ceiling)guaranteedAhead++;
                        if(rivalMaximum>=projected)possiblyAhead++;
                    }
                }
                int best=1+guaranteedAhead;
                int worst=1+possiblyAhead;
                ranking.setText("Posición orientativa posible: "+best+".º–"+worst+".º"+
                    "\nAcceso entre los 5 primeros: "+(worst<=5?"compatible con todos los límites de puntos":
                    best>5?"fuera incluso en el mejor límite de puntos":
                    "todavía depende de los resultados rivales")+
                    "\nNo son probabilidades recalculadas; los desempates y enfrentamientos directos pueden cambiar el intervalo.");
            };
            update.run();
            for(int index=0;index<count;index++){
                org.json.JSONObject match=matches.optJSONObject(index);
                if(match==null)continue;
                final int matchIndex=index;
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(8),dp(5),dp(8),dp(5));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
                params.bottomMargin=dp(3);
                content.addView(card,params);
                card.setBackgroundColor(panel);
                card.addView(txt((index+1)+". "+(match.optBoolean("home")?"Local · ":"Visitante · ")+
                    match.optString("opponent","Rival"),12,Color.WHITE));
                TextView selection=txt("Sin simular · elegir puntos",11,accent);
                selection.setPadding(0,dp(2),0,dp(2));
                card.addView(selection);
                LinearLayout buttons=new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(buttons);
                final java.util.ArrayList<TextView> pointButtons=new java.util.ArrayList<>();
                for(int points=0;points<=5;points++){
                    final int value=points;
                    TextView button=txt(String.valueOf(points),12,accent);
                    button.setGravity(android.view.Gravity.CENTER);
                    button.setPadding(0,dp(5),0,dp(5));
                    buttons.addView(button,new LinearLayout.LayoutParams(0,-2,1));
                    pointButtons.add(button);
                    button.setOnClickListener(v->{
                        chosen[matchIndex]=value;
                        selection.setText("Seleccionado: +"+value+" puntos");
                        for(int k=0;k<pointButtons.size();k++){
                            TextView cell=pointButtons.get(k);
                            cell.setTextColor(k==value?Color.BLACK:accent);
                            cell.setBackgroundColor(k==value?accent:panel);
                        }
                        update.run();
                    });
                }
            }
            final java.util.ArrayList<java.util.ArrayList<TextView>> allButtons=new java.util.ArrayList<>();
            for(int i=0;i<content.getChildCount();i++){
                android.view.View child=content.getChildAt(i);
                if(child instanceof LinearLayout){
                    LinearLayout card=(LinearLayout)child;
                    if(card.getChildCount()>2&&card.getChildAt(2) instanceof LinearLayout){
                        java.util.ArrayList<TextView> cells=new java.util.ArrayList<>();
                        LinearLayout line=(LinearLayout)card.getChildAt(2);
                        for(int k=0;k<line.getChildCount();k++)if(line.getChildAt(k) instanceof TextView)cells.add((TextView)line.getChildAt(k));
                        allButtons.add(cells);
                    }
                }
            }
            TextView reset=txt("↺ Restablecer simulación",12,accent);
            reset.setPadding(dp(8),dp(12),dp(8),dp(12));
            content.addView(reset);
            reset.setOnClickListener(v->{
                java.util.Arrays.fill(chosen,-1);
                for(int i=0;i<content.getChildCount();i++){
                    android.view.View child=content.getChildAt(i);
                    if(child instanceof LinearLayout){
                        LinearLayout card=(LinearLayout)child;
                        if(card.getChildCount()>1&&card.getChildAt(1) instanceof TextView)
                            ((TextView)card.getChildAt(1)).setText("Sin simular · elegir puntos");
                    }
                }
                for(java.util.ArrayList<TextView> cells:allButtons)for(TextView cell:cells){
                    cell.setTextColor(accent);
                    cell.setBackgroundColor(panel);
                }
                update.run();
            });
            content.addView(txt("Elige entre 0 y 5 puntos por partido, incluidos posibles bonus. "+
                "La simulación es orientativa y no cambia la clasificación oficial ni las probabilidades calculadas.",11,Color.LTGRAY));
        }
        header.setOnClickListener(v->{
            boolean open=content.getVisibility()!=View.VISIBLE;
            content.setVisibility(open?View.VISIBLE:View.GONE);
            header.setText((open?"▾ ":"▸ ")+"Simular mis partidos");
        });
    }

}
