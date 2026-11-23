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
        android.graphics.drawable.Drawable icon=lineIcon(kind);
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
        track.post(()->{
            android.view.ViewGroup.LayoutParams lp=fill.getLayoutParams();
            lp.width=Math.max(value>0?dp(5):0,(int)((long)track.getWidth()*Math.max(0,value)/Math.max(1,max)));
            fill.setLayoutParams(lp);
        });
    }

    private void standingMetric(LinearLayout target,String value,String label){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);
        card.setPadding(dp(8),dp(7),dp(8),dp(7));
        android.graphics.drawable.GradientDrawable bgCard=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{0xff34566a,0xff1a3447,0xff122737});
        bgCard.setCornerRadius(dp(12));bgCard.setStroke(dp(1),0xff3d7380);
        card.setBackground(bgCard);card.setElevation(dp(4));
        TextView big=txt(value,19,0xff75e6b2);big.setGravity(android.view.Gravity.CENTER);
        big.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(big);
        TextView small=txt(label,10,0xffd2e4ef);small.setGravity(android.view.Gravity.CENTER);card.addView(small);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),dp(5),dp(3),dp(5));target.addView(card,lp);
    }
    private void standingSection(LinearLayout target,String title){
        TextView t=txt(title,15,0xff75e6b2);t.setTypeface(null,android.graphics.Typeface.BOLD);
        target.addView(t);
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
    /** Calculate only fully traceable playing intervals from an official act. */
    private java.util.Map<String,Integer> verifiedActMinutes(String lineup,String changes,boolean local,int duration){
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
        for(String line:changes.split("\\r?\\n")){
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
        LinearLayout tabs=new LinearLayout(this);frame.addView(tabs);
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
        String[] labels={"Resumen","Evolución","Partidos","Jugadores"};
        final int[] active={0};
        final boolean[] loadingActs={false};
        final java.util.HashSet<String> attemptedActs=new java.util.HashSet<>();
        final java.util.LinkedHashMap<String,String> actErrors=new java.util.LinkedHashMap<>();
        final Runnable[] redrawRef=new Runnable[1];
        Runnable redraw=()->{
            content.removeAllViews();
            for(int i=0;i<tabs.getChildCount();i++){
                TextView tab=(TextView)tabs.getChildAt(i);
                tab.setTextColor(i==active[0]?0xff75e6b2:0xffb0c6d1);
                tab.setBackgroundColor(i==active[0]?0xff28546a:0xff1a3447);
            }
            if(active[0]==0){
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
                if(active[0]==3){
                    int duration=CompetitionRules.matchMinutes(season,category,selectedCompetition);
                    LinearLayout durationCards=new LinearLayout(this);content.addView(durationCards);
                    standingMetric(durationCards,duration>0?duration+" min":"—","Partido completo");
                    standingMetric(durationCards,duration>0?(duration/2)+" min":"—","Cada tiempo");
                    content.addView(txt(duration>0?"Duración prevista según las reglas verificadas de esta competición.":"Duración pendiente de la circular oficial de esta categoría y temporada.",12,0xffb8d4cc));
                    int actCount=0;
                    java.util.Map<String,Integer> appearances=new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    java.util.Map<String,Integer> starts=new java.util.HashMap<>();
                    java.util.Map<String,Integer> benches=new java.util.HashMap<>();
                    java.util.Map<String,Integer> verifiedMinutes=new java.util.HashMap<>();
                    java.util.Map<String,Integer> verifiedMatches=new java.util.HashMap<>();
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
                                    if(dialog.isShowing()&&active[0]==3) {
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
                                    thisMatch.add(playerId);
                                    if(starterSection)matchStarters.add(playerId);else matchBenches.add(playerId);
                                }
                            }
                        }
                        if(!thisMatch.isEmpty()){
                            // Sanctions affect the identified player, not the whole team.
                            java.util.HashSet<String> sanctionedDorsals=new java.util.HashSet<>();
                            boolean unknownSanction=false;
                            for(String keyName:new String[]{"temporaryCards","definitiveCards"}){
                                String rawCards=cached.optString(keyName,"");
                                int cardSide=-1;
                                for(String cardLine:rawCards.split("\\r?\\n")){
                                    String ct=cardLine.trim();
                                    if(ct.matches("(?i)Equipo local:.*"))cardSide=0;
                                    else if(ct.matches("(?i)Equipo visitante:.*"))cardSide=1;
                                    else if(cardSide==(local?0:1)&&ct.matches("^\\d+\\. Min .*")){
                                        java.util.regex.Matcher dm=java.util.regex.Pattern.compile("Dorsal (\\d+)").matcher(ct);
                                        if(dm.find())sanctionedDorsals.add(dm.group(1));
                                        else unknownSanction=true;
                                    }
                                }
                            }
                            java.util.Map<String,Integer> perAct=verifiedActMinutes(lineup,cached.optString("changes",""),local,
                                CompetitionRules.matchMinutes(season,category,selectedCompetition));
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
                                    if(id==null)unknownSanction=true;
                                    else if(perAct.remove(id)!=null)excludedPlayers++;
                                }
                            }
                            // If a card has no identifiable player, no minute count can be certified.
                            if(unknownSanction)perAct=null;
                            String matchLabel=match.optString("home","")+" - "+match.optString("away","");
                            if(perAct==null){
                                String reason=unknownSanction?"Expulsión sin dorsal identificable":
                                    cached.optString("changes","").trim().isEmpty()?"Sin tabla de cambios legible":
                                    "Sustituciones no conciliadas con la alineación";
                                minuteActDiagnostics.add("⚠ "+matchLabel+" · "+reason);
                            }else if(excludedPlayers>0){
                                minuteActDiagnostics.add("◐ "+matchLabel+" · Minutos calculados para "+perAct.size()+" jugadores; "+excludedPlayers+" sancionado(s) pendientes");
                            }else{
                                minuteActDiagnostics.add("✓ "+matchLabel+" · "+CompetitionRules.matchMinutes(season,category,selectedCompetition)+" min analizados");
                            }
                            if(perAct!=null){
                                minuteActCount[0]++;
                                for(java.util.Map.Entry<String,Integer> e:perAct.entrySet()){
                                    verifiedMinutes.put(e.getKey(),verifiedMinutes.getOrDefault(e.getKey(),0)+e.getValue());
                                    verifiedMatches.put(e.getKey(),verifiedMatches.getOrDefault(e.getKey(),0)+1);
                                }
                            }
                            actCount++;
                            for(String id:thisMatch)appearances.put(id,appearances.getOrDefault(id,0)+1);
                            for(String id:matchStarters)starts.put(id,starts.getOrDefault(id,0)+1);
                            for(String id:matchBenches)benches.put(id,benches.getOrDefault(id,0)+1);
                        }
                    }
                    if(!appearances.isEmpty()){
                        standingSection(content,"Jugadores identificados · actas consultadas");
                        java.util.ArrayList<java.util.Map.Entry<String,Integer>> ranking=new java.util.ArrayList<>(appearances.entrySet());
                        java.util.Collections.sort(ranking,(a,b)->Integer.compare(b.getValue(),a.getValue()));
                        java.util.ArrayList<java.util.Map.Entry<String,Integer>> starterRanking=new java.util.ArrayList<>(ranking);
                        java.util.Collections.sort(starterRanking,(a,b)->{
                            int cmp=Integer.compare(starts.getOrDefault(b.getKey(),0),starts.getOrDefault(a.getKey(),0));
                            if(cmp!=0)return cmp;
                            cmp=Integer.compare(b.getValue(),a.getValue());
                            return cmp!=0?cmp:a.getKey().compareToIgnoreCase(b.getKey());
                        });
                        int chartSize=Math.min(10,starterRanking.size());
                        if(chartSize>0){
                            String[] chartNames=new String[chartSize];
                            int[] chartCounts=new int[chartSize];
                            int[] chartColors=new int[chartSize];
                            for(int k=0;k<chartSize;k++){
                                java.util.Map.Entry<String,Integer> entry=starterRanking.get(k);
                                String[] parts=entry.getKey().split("\\|",2);
                                String name=parts.length>1?parts[1]:entry.getKey();
                                chartNames[k]=String.valueOf(k+1);
                                chartCounts[k]=starts.getOrDefault(entry.getKey(),0);
                                chartColors[k]=k%2==0?0xff48dca2:0xff56aaf2;
                            }
                            standingSection(content,"Top 10 · más titularidades · gráfico 3D");
                            standingColumns3D(content,chartNames,chartCounts,chartColors);
                            LinearLayout legend=new LinearLayout(this);
                            legend.setOrientation(1);
                            legend.setPadding(dp(9),dp(8),dp(9),dp(9));
                            content.addView(legend);
                            for(int k=0;k<chartSize;k++){
                                java.util.Map.Entry<String,Integer> entry=starterRanking.get(k);
                                String[] parts=entry.getKey().split("\\|",2);
                                String name=parts.length>1?parts[1]:entry.getKey();
                                TextView line=txt((k+1)+".  "+name+"  ·  "+starts.getOrDefault(entry.getKey(),0)+" titularidades / "+entry.getValue()+" partidos",12,0xffd2e4ef);
                                line.setPadding(dp(3),dp(4),dp(3),dp(4));
                                legend.addView(line);
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
                        standingSection(content,"Plantilla · "+ranking.size()+" jugadores");
                        android.widget.EditText searchPlayer=new android.widget.EditText(this);
                        searchPlayer.setSingleLine(true);searchPlayer.setTextSize(13);
                        searchPlayer.setHint("Buscar nombre o licencia");
                        searchPlayer.setHintTextColor(0xffa9c4d2);
                        searchPlayer.setTextColor(0xffe2f5fa);
                        content.addView(searchPlayer);
                        android.widget.Spinner playerFilter=new android.widget.Spinner(this);
                        String[] playerFilters={"Todos","Titulares","Suplentes","Todos los partidos","Mayor participación"};
                        playerFilter.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,playerFilters));
                        content.addView(playerFilter);
                        LinearLayout rosterList=new LinearLayout(this);rosterList.setOrientation(1);content.addView(rosterList);
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
                            playerRow.addView(txt("›",23,0xff75e6b2));
                            playerRow.setOnClickListener(v->{
                                LinearLayout details=new LinearLayout(this);
                                details.setOrientation(1);
                                details.setPadding(dp(20),dp(12),dp(20),dp(8));
                                details.addView(txt(playerName,19,0xff75e6b2));
                                details.addView(txt("Licencia: "+parts[0],12,0xffb8d4cc));
                                details.addView(txt("Partidos con alineación: "+playedCount,14,0xffe2f5fa));
                                details.addView(txt("Titularidades: "+starterCount,14,0xffe2f5fa));
                                details.addView(txt("Convocatorias como suplente: "+benchCount,14,0xffe2f5fa));
                                int mins=verifiedMinutes.getOrDefault(item.getKey(),0);
                                int checked=verifiedMatches.getOrDefault(item.getKey(),0);
                                details.addView(txt("Minutos verificados: "+(checked>0?mins+" min en "+checked+" acta(s)":"Sin datos verificables"),14,0xff75e6b2));
                                details.addView(txt("Los minutos no incluyen partidos sin sustituciones verificables. Anotaciones pendientes de calcular.",12,0xffa9c4d2));
                                standingColumns3D(details,new String[]{"Partidos","Titular","Suplente"},
                                    new int[]{playedCount,starterCount,benchCount},
                                    new int[]{0xff48dca2,0xff56aaf2,0xff80d9cc});
                                new android.app.AlertDialog.Builder(this)
                                    .setView(details).setPositiveButton("Cerrar",null).show();
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
        for(int i=0;i<labels.length;i++){
            final int index=i;TextView tab=txt(labels[i],11,Color.WHITE);
            tab.setGravity(android.view.Gravity.CENTER);tab.setPadding(dp(2),dp(13),dp(2),dp(13));
            tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
            tab.setOnClickListener(v->{active[0]=index;redraw.run();});
        }
        redrawRef[0]=redraw;
        redraw.run();
        dialog.show();
        android.view.Window window=dialog.getWindow();
        if(window!=null)window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(20),
            getResources().getDisplayMetrics().heightPixels-dp(65));
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
