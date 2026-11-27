package es.farcompeticiones;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Catálogo oficial por temporada y documentos locales; nunca activa reglas sin revisión. */
public final class CircularsActivity extends Activity {
    private static final int PICK_PDF=41;
    private LinearLayout body;
    private String season,category;
    private int green=0xff66e1a4;
    private JSONObject officialCatalogue=new JSONObject();
    private int screen=0;
    private boolean catalogLoading=false;
    private String catalogError="";
    private static final String CATALOG_URL="https://raw.githubusercontent.com/fuenttejrt/FAR-Competiciones/main/catalogo/documentos.json";
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        season=getIntent().getStringExtra("season");
        category=getIntent().getStringExtra("category");
        if(season==null)season="2026/27";
        if(category==null)category="M14";
        try{officialCatalogue=new JSONObject(getPreferences(0).getString("official_catalog_v1","{}"));}catch(Exception ignored){}
        renderSeasons();
        refreshCatalogue(false);
    }
    private TextView label(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(14,12,14,12);return t;
    }
    private void action(String title,Runnable run){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);body.addView(b);b.setOnClickListener(v->run.run());
    }
    private JSONArray seasons(){
        java.util.TreeSet<String> years=new java.util.TreeSet<>();
        years.add("2025/26");years.add("2026/27");
        try{
            JSONArray local=new JSONArray(getPreferences(0).getString("circular_seasons","[]"));
            for(int i=0;i<local.length();i++)years.add(local.getString(i));
        }catch(Exception ignored){}
        JSONArray remote=officialCatalogue.optJSONArray("seasons");
        if(remote!=null)for(int i=0;i<remote.length();i++){
            JSONObject y=remote.optJSONObject(i);if(y!=null)years.add(y.optString("name"));
        }
        JSONArray result=new JSONArray();for(String y:years)if(!y.isEmpty())result.put(y);return result;
    }
    private void renderSeasons(){
        screen=0;
        setup("Circulares y reglamentos");
        body.addView(label("Selecciona una temporada para organizar sus circulares por categoría.",14));
        catalogueActions();
        JSONArray years=seasons();
        for(int i=years.length()-1;i>=0;i--){
            final String y=years.optString(i);
            action("📁 "+y,()->{season=y;renderCategories();});
        }
        action("＋ Crear temporada",()->{
            EditText input=new EditText(this);input.setSingleLine(true);input.setHint("2027/28");
            new android.app.AlertDialog.Builder(this).setTitle("Nueva temporada").setView(input)
                .setNegativeButton("Cancelar",null).setPositiveButton("Crear",(d,w)->{
                    String y=input.getText().toString().trim();
                    if(!y.matches("[0-9]{4}/[0-9]{2}")){Toast.makeText(this,"Formato: 2027/28",Toast.LENGTH_LONG).show();return;}
                    int start=Integer.parseInt(y.substring(0,4)),end=Integer.parseInt(y.substring(5));
                    if((start+1)%100!=end){Toast.makeText(this,"Los años deben ser consecutivos",Toast.LENGTH_LONG).show();return;}
                    JSONArray existing=seasons();
                    for(int j=0;j<existing.length();j++)if(y.equals(existing.optString(j))){season=y;renderCategories();return;}
                    existing.put(y);getPreferences(0).edit().putString("circular_seasons",existing.toString()).apply();
                    season=y;renderCategories();
                }).show();
        });
        action("‹ Volver",()->finish());
    }
    private void renderCategories(){
        screen=1;
        setup("Circulares · "+season);
        body.addView(label("Selecciona el ámbito. Las circulares generales se guardan por separado de las específicas.",13));
        catalogueActions();
        for(String c:new String[]{"Generales","M6","M8","M10","M12","M14","M16","M18","M20","M22","M23","Sénior masculino","Sénior femenino","Otras categorías"}){
            action("📁 "+c,()->{category=c;render();});
        }
        action("‹ Temporadas",()->renderSeasons());
    }
    private void setup(String heading){
        ScrollView scroll=new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(12,16,12,20);
        body.setBackgroundColor(0xff0f1926);scroll.addView(body);setContentView(scroll);
        TextView title=label(heading,21);title.setTextColor(green);body.addView(title);
    }
    private String key(){return "circulars_"+season+"_"+category;}
    private JSONArray documents(){
        try{return new JSONArray(getPreferences(0).getString(key(),"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private void save(JSONArray docs){getPreferences(0).edit().putString(key(),docs.toString()).apply();}
    private void render(){
        screen=2;
        setup("Circulares y reglamentos");
        body.addView(label(season+" · "+category,15));
        body.addView(label("Archivo de circulares oficiales. Los documentos quedan asociados a esta temporada y categoría. Añadir un PDF no cambia las reglas automáticamente.",13));
        catalogueActions();
        renderOfficialDocuments();
        body.addView(label("Documentos añadidos por ti",16));
        action("＋ Añadir circular PDF",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,PICK_PDF);
        });
        action("＋ Añadir enlace oficial",()->addLink());
        JSONArray docs=documents();
        if(docs.length()==0)body.addView(label("Todavía no hay documentos registrados.",14));
        for(int n=0;n<docs.length();n++){
            JSONObject doc=docs.optJSONObject(n);if(doc==null)continue;
            final int index=n;
            String name=doc.optString("name","Circular");
            TextView item=label("📄 "+name+"\nPendiente de revisión · No aplicada",14);
            item.setBackgroundColor(0xff1b2b3b);body.addView(item);
            action("Abrir documento",()->{
                try{
                    Uri uri=Uri.parse(doc.optString("uri"));
                    Intent view=new Intent(Intent.ACTION_VIEW,uri);
                    if("pdf".equals(doc.optString("type")))view.setDataAndType(uri,"application/pdf");
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(view);
                }catch(Exception e){Toast.makeText(this,"No se puede abrir el documento",Toast.LENGTH_LONG).show();}
            });
            action("Quitar del registro",()->{
                JSONArray remaining=new JSONArray();
                for(int j=0;j<docs.length();j++)if(j!=index)remaining.put(docs.optJSONObject(j));
                save(remaining);render();
            });
        }
        body.addView(label("Las reglas se activarán únicamente después de extraerlas y confirmar su interpretación. Descargar un documento no activa sus reglas en las competiciones.",12));
        action("‹ Categorías",()->renderCategories());
    }
    private void addLink(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("https://farugby.com/... o https://ferugby.es/...");
        new android.app.AlertDialog.Builder(this).setTitle("Enlace oficial FAR o FER")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                String link=input.getText().toString().trim();
                Uri uri=Uri.parse(link);
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||
                    !(uri.getHost().equals("farugby.com")||uri.getHost().equals("www.farugby.com")||uri.getHost().equals("farugby.matchready.es")||uri.getHost().equals("ferugby.es")||uri.getHost().equals("www.ferugby.es"))){
                    Toast.makeText(this,"Introduce un enlace HTTPS oficial de FAR o FER",Toast.LENGTH_LONG).show();return;
                }
                try{JSONObject doc=new JSONObject();doc.put("name",link);doc.put("uri",link);doc.put("type","link");
                    JSONArray docs=documents();docs.put(doc);save(docs);render();
                }catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
            }).show();
    }

    private void redraw(){
        if(isFinishing()||isDestroyed())return;
        if(screen==0)renderSeasons();else if(screen==1)renderCategories();else render();
    }
    private void catalogueActions(){
        String status=catalogLoading?"Actualizando catálogo oficial…":catalogError.isEmpty()?
            (officialCatalogue.optJSONArray("seasons")==null?"Catálogo oficial pendiente de descarga":"Catálogo oficial disponible · FAR y FER"):
            catalogError;
        body.addView(label(status,12));
        action(catalogLoading?"Actualizando…":"↻ Actualizar catálogo oficial",()->refreshCatalogue(true));
    }
    private void refreshCatalogue(boolean requested){
        if(catalogLoading)return;
        catalogLoading=true;catalogError="";redraw();
        new Thread(()->{
            JSONObject result=null;String failure="";
            java.net.HttpURLConnection connection=null;
            try{
                connection=(java.net.HttpURLConnection)new java.net.URL(CATALOG_URL).openConnection();
                connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
                connection.setRequestProperty("Accept","application/json");
                if(connection.getResponseCode()!=200)throw new java.io.IOException("Catálogo pendiente");
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                try(java.io.InputStream in=connection.getInputStream()){
                    byte[] buffer=new byte[8192];int read;
                    while((read=in.read(buffer))!=-1){
                        if(bytes.size()+read>5000000)throw new java.io.IOException("Catálogo excesivo");
                        bytes.write(buffer,0,read);
                    }
                }
                JSONObject parsed=new JSONObject(bytes.toString("UTF-8"));
                if(parsed.optInt("schema")!=1||parsed.optJSONArray("seasons")==null)throw new java.io.IOException("Catálogo inválido");
                result=parsed;
            }catch(Exception e){
                failure=officialCatalogue.optJSONArray("seasons")==null?"No se pudo descargar el catálogo. Pulsa Actualizar para reintentar.":"Sin conexión con el catálogo. Se muestra la última copia guardada.";
            }finally{if(connection!=null)connection.disconnect();}
            final JSONObject downloaded=result;final String error=failure;
            runOnUiThread(()->{
                catalogLoading=false;
                if(isFinishing()||isDestroyed())return;
                if(downloaded!=null){
                    officialCatalogue=downloaded;
                    getPreferences(0).edit().putString("official_catalog_v1",downloaded.toString()).apply();
                }
                catalogError=error;redraw();
                if(requested&&downloaded!=null)Toast.makeText(this,"Catálogo actualizado",Toast.LENGTH_SHORT).show();
            });
        },"official-documents").start();
    }
    private JSONObject officialSeason(){
        JSONArray list=officialCatalogue.optJSONArray("seasons");
        if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject item=list.optJSONObject(i);
            if(item!=null&&season.equals(item.optString("name")))return item;
        }
        return new JSONObject();
    }
    private boolean inFolder(JSONObject doc){
        JSONArray categories=doc.optJSONArray("categories");
        if(categories==null)return "Otras categorías".equals(category);
        for(int i=0;i<categories.length();i++){
            String c=categories.optString(i);
            if(category.equals(c)||("Sénior masculino".equals(category)&&"Sénior".equals(c)))return true;
        }
        return false;
    }
    private void openOfficial(String value){
        try{
            Uri uri=Uri.parse(value);String host=uri.getHost();
            if(!"https".equals(uri.getScheme())||host==null||
                !(host.equals("farugby.com")||host.equals("www.farugby.com")||host.equals("farugby.matchready.es")||
                  host.equals("ferugby.es")||host.equals("www.ferugby.es")||host.endsWith(".supabase.co")))
                throw new IllegalArgumentException();
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        }catch(Exception e){Toast.makeText(this,"No se puede abrir el documento",Toast.LENGTH_LONG).show();}
    }
    private void renderOfficialDocuments(){
        body.addView(label("Documentos oficiales · FAR y FER",16));
        JSONObject folder=officialSeason();
        JSONArray docs=folder.optJSONArray("documents");
        int shown=0;
        if(docs!=null)for(int i=0;i<docs.length();i++){
            JSONObject doc=docs.optJSONObject(i);if(doc==null||!inFolder(doc))continue;
            shown++;
            String state="Documento archivado · Reglas pendientes de revisión";
            if("vigencia_por_revisar".equals(doc.optString("applicability")))state="Reglamento general · Vigencia en esta temporada por confirmar";
            TextView item=label("📄 "+doc.optString("name")+"\n"+doc.optString("source")+" · Versión "+doc.optInt("version",1)+"\n"+state,14);
            item.setBackgroundColor(0xff1b2b3b);body.addView(item);
            action("Abrir PDF archivado",()->openOfficial(doc.optString("url")));
            action("Ver fuente oficial",()->openOfficial(doc.optString("official_url")));
        }
        if(shown==0)body.addView(label("Todavía no hay documentos oficiales en esta carpeta. Se mostrarán cuando estén disponibles en el catálogo.",13));
        if(!"Generales".equals(category))action("📁 Ver documentos generales de "+season,()->{category="Generales";render();});
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_PDF||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String name="Circular PDF";
            try(Cursor cursor=getContentResolver().query(uri,null,null,null,null)){
                if(cursor!=null&&cursor.moveToFirst()){
                    int col=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(col>=0)name=cursor.getString(col);
                }
            }
            JSONObject doc=new JSONObject();doc.put("name",name);doc.put("uri",uri.toString());doc.put("type","pdf");
            JSONArray docs=documents();docs.put(doc);save(docs);render();
        }catch(Exception e){Toast.makeText(this,"No se pudo registrar el PDF",Toast.LENGTH_LONG).show();}
    }
}
