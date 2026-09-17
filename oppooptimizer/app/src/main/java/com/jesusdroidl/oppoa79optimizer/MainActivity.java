package com.jesusdroidl.oppoa79optimizer;

import android.app.*;import android.os.*;import android.content.*;import android.content.pm.*;import android.graphics.Color;import android.net.Uri;import android.provider.Settings;import android.view.*;import android.widget.*;import java.util.*;

public class MainActivity extends Activity {
  LinearLayout list; TextView status; final LinkedHashMap<String,String> targets=new LinkedHashMap<>();
  @Override public void onCreate(Bundle b){super.onCreate(b); seed(); build();}
  void seed(){
    targets.put("com.mobileposse.client.moment","Magazine / recomendaciones del operador");
    targets.put("com.easybrain.arrow.puzzle3d","Arrow Puzzle 3D");
    targets.put("com.rbt.android","Contestone");
    targets.put("com.telcel.imk","Claro música");
    targets.put("com.clarodrive.android","Claro drive");
    targets.put("com.dla.android","Claro video");
    targets.put("com.americamovil.claroshop","Claro shop");
    targets.put("com.coloros.musiclink","Music Party");
    targets.put("com.google.android.videos","Google TV");
    targets.put("com.handmark.expressweather","1Weather");
    targets.put("com.oplus.member","Mi OPPO");
    targets.put("com.jesusdroidl.oppoa79diagnostic","JESUSDROIDL OPPO Diagnóstico (ya no necesario)");
  }
  TextView text(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.WHITE);v.setPadding(16,12,16,12);return v;}
  Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
  void build(){ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,35,28,35);root.setBackgroundColor(Color.rgb(17,17,17));sc.addView(root);
    TextView h=text("JESUSDROIDL\nOPPO A79 5G OPTIMIZER",26);h.setTextColor(Color.rgb(244,196,48));root.addView(h);
    root.addView(text("CPH2557 • Android 15\nLimpieza controlada: nunca toca componentes críticos del sistema.",15));
    status=text("Analizando paquetes recomendados…",14);root.addView(status);
    Button scan=btn("ANALIZAR BLOATWARE");scan.setOnClickListener(v->scan());root.addView(scan);
    list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
    Button battery=btn("OPTIMIZAR CONSUMO / BATERÍA");battery.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}});root.addView(battery);
    Button storage=btn("ABRIR LIMPIEZA DE ALMACENAMIENTO");storage.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}});root.addView(storage);
    root.addView(text("IMPORTANTE: Android/ColorOS exige confirmación del usuario para desinstalar aplicaciones. Esta versión no usa root y no ejecuta comandos ocultos. Puedes retirar únicamente los paquetes detectados como prescindibles.",13));setContentView(sc);scan();
  }
  boolean installed(String p){try{getPackageManager().getPackageInfo(p,0);return true;}catch(Exception e){return false;}}
  void scan(){list.removeAllViews();int n=0;for(Map.Entry<String,String> e:targets.entrySet()){if(!installed(e.getKey()))continue;n++;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView t=text(e.getValue()+"\n"+e.getKey(),14);row.addView(t);Button x=btn("DESINSTALAR");String pkg=e.getKey();x.setOnClickListener(v->uninstall(pkg));row.addView(x);list.addView(row);}status.setText(n==0?"No se detectó bloatware de la lista segura.":"Detectados: "+n+" paquetes prescindibles. Revisa y elimina los que no uses.");}
  void uninstall(String p){try{Intent i=new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+p));startActivity(i);}catch(Exception e){Toast.makeText(this,"ColorOS no permite retirar este paquete desde una app normal.",Toast.LENGTH_LONG).show();}}
  @Override protected void onResume(){super.onResume();if(list!=null)scan();}
}
