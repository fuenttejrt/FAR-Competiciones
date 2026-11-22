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

/** Registro local de documentos oficiales; nunca activa reglas sin revisión. */
public final class CircularsActivity extends Activity {
    private static final int PICK_PDF=41;
    private LinearLayout body;
    private String season,category;
    private int green=0xff66e1a4;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        season=getIntent().getStringExtra("season");
        category=getIntent().getStringExtra("category");
        if(season==null)season="2026/27";
        if(category==null)category="M14";
        render();
    }
    private TextView label(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(14,12,14,12);return t;
    }
    private void action(String title,Runnable run){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);body.addView(b);b.setOnClickListener(v->run.run());
    }
    private String key(){return "circulars_"+season+"_"+category;}
    private JSONArray documents(){
        try{return new JSONArray(getPreferences(0).getString(key(),"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private void save(JSONArray docs){getPreferences(0).edit().putString(key(),docs.toString()).apply();}
    private void render(){
        ScrollView scroll=new ScrollView(this);
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(12,16,12,20);
        body.setBackgroundColor(0xff0f1926);scroll.addView(body);setContentView(scroll);
        TextView title=label("Circulares y reglamentos",21);title.setTextColor(green);body.addView(title);
        body.addView(label(season+" · "+category,15));
        body.addView(label("Archivo de circulares oficiales. Los documentos quedan asociados a esta temporada y categoría. Añadir un PDF no cambia las reglas automáticamente.",13));
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
        body.addView(label("Las reglas se activarán únicamente después de extraerlas y confirmar su interpretación. Esta primera versión registra y permite consultar los documentos.",12));
        action("‹ Volver",()->finish());
    }
    private void addLink(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("https://farugby.com/...");
        new android.app.AlertDialog.Builder(this).setTitle("Enlace oficial FAR")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                String link=input.getText().toString().trim();
                Uri uri=Uri.parse(link);
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||
                    !(uri.getHost().equals("farugby.com")||uri.getHost().endsWith(".farugby.com"))){
                    Toast.makeText(this,"Introduce un enlace HTTPS de FAR",Toast.LENGTH_LONG).show();return;
                }
                try{JSONObject doc=new JSONObject();doc.put("name",link);doc.put("uri",link);doc.put("type","link");
                    JSONArray docs=documents();docs.put(doc);save(docs);render();
                }catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
            }).show();
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
