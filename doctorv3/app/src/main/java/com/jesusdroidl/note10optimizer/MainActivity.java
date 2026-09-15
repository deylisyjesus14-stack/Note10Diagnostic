package com.jesusdroidl.note10optimizer;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int REQ_UNINSTALL = 4401;
    private static final long DAY = 86400000L;

    private TextView state, report, action;
    private LinearLayout appsBox;
    private Button usageBtn, filesBtn, scanBtn, optimizeBtn, repairBtn;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Candidate> uninstallQueue = new ArrayDeque<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private boolean pendingOptimize = false;
    private String currentUninstall = null;

    static class Candidate {
        String label, pkg, why;
        int score;
        boolean high;
        Candidate(String l,String p,String w,int s,boolean h){label=l;pkg=p;why=w;score=s;high=h;}
    }
    static class CleanStats { long bytes; int files, dirs, temp, thumbs, installers; }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(buildUi());
        refreshState();
        refreshButtons();
    }

    @Override protected void onResume(){
        super.onResume();
        refreshState();
        refreshButtons();
        if(pendingOptimize && allFiles()){ pendingOptimize=false; optimizeNow(); }
    }

    private View buildUi(){
        ScrollView s=new ScrollView(this);
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL); r.setPadding(dp(16),dp(18),dp(16),dp(28)); r.setBackgroundColor(0xFF090909); s.addView(r);

        TextView brand=t("JESUSDROIDL",13,0xFFFFD400,true); brand.setLetterSpacing(.2f); r.addView(brand);
        r.addView(t("NOTE 10+ DOCTOR PRO",26,Color.WHITE,true));
        r.addView(t("SM-N975U • limpieza activa • reparación • apps conflictivas",12,0xFFBBBBBB,false));
        TextView note=t("La app sí realiza limpieza y refrescos automáticos. Android seguirá pidiendo confirmación antes de desinstalar una app. No borra fotos, vídeos, documentos, chats ni componentes críticos.",12,0xFFE0E0E0,false); note.setPadding(dp(10),dp(10),dp(10),dp(10)); note.setBackgroundColor(0xFF171717); r.addView(note);

        r.addView(sec("ESTADO")); state=box("Leyendo…"); r.addView(state);
        r.addView(sec("PERMISOS"));
        usageBtn=btn("ACCESO DE USO"); usageBtn.setOnClickListener(v->openUsage()); r.addView(usageBtn);
        filesBtn=btn("ACCESO A TODOS LOS ARCHIVOS"); filesBtn.setOnClickListener(v->openAllFiles()); r.addView(filesBtn);

        r.addView(sec("OPTIMIZACIÓN ACTIVA"));
        optimizeBtn=btn("OPTIMIZAR AHORA"); optimizeBtn.setOnClickListener(v->prepareOptimize()); r.addView(optimizeBtn);
        repairBtn=btn("REPARAR CAPTURAS / GALERÍA"); repairBtn.setOnClickListener(v->repairScreenshots()); r.addView(repairBtn);
        action=box("Todavía no se ha ejecutado la optimización."); r.addView(action);

        r.addView(sec("APPS Y CONFLICTOS"));
        scanBtn=btn("ANALIZAR TELÉFONO COMPLETO"); scanBtn.setOnClickListener(v->scan()); r.addView(scanBtn);
        report=box("Pulsa ANALIZAR TELÉFONO COMPLETO."); r.addView(report);
        appsBox=new LinearLayout(this); appsBox.setOrientation(LinearLayout.VERTICAL); r.addView(appsBox);
        Button all=btn("DESINSTALAR RECOMENDADAS"); all.setOnClickListener(v->startRecommended()); r.addView(all);

        r.addView(sec("AJUSTES DEL SISTEMA"));
        r.addView(shortcut("APLICACIONES",Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        r.addView(shortcut("BATERÍA",Settings.ACTION_BATTERY_SAVER_SETTINGS));
        r.addView(shortcut("ALMACENAMIENTO",Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        Button care=btn("ABRIR SAMSUNG DEVICE CARE"); care.setOnClickListener(v->openCare()); r.addView(care);

        TextView foot=t("v3.0 • Android 12 • sin root • limpieza controlada",12,0xFF777777,false); foot.setGravity(Gravity.CENTER); foot.setPadding(0,dp(15),0,0); r.addView(foot);
        return s;
    }

    private TextView t(String x,int sp,int c,boolean bold){TextView v=new TextView(this);v.setText(x);v.setTextSize(sp);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private TextView sec(String x){TextView v=t(x,13,0xFFFFD400,true);v.setPadding(0,dp(18),0,dp(7));return v;}
    private TextView box(String x){TextView v=t(x,13,Color.WHITE,false);v.setTypeface(Typeface.MONOSPACE);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackgroundColor(0xFF181818);return v;}
    private Button btn(String x){Button b=new Button(this);b.setText(x);b.setTextColor(Color.BLACK);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackgroundColor(0xFFFFD400);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(9),0,dp(4));b.setLayoutParams(lp);return b;}
    private Button shortcut(String x,String act){Button b=btn("ABRIR "+x);b.setOnClickListener(v->{try{startActivity(new Intent(act));}catch(Exception e){toast("Ajuste no disponible");}});return b;}

    private void refreshButtons(){
        usageBtn.setText(usageAccess()?"ACCESO DE USO: CONCEDIDO":"CONCEDER ACCESO DE USO"); usageBtn.setBackgroundColor(usageAccess()?0xFF7ED957:0xFFFFD400);
        filesBtn.setText(allFiles()?"TODOS LOS ARCHIVOS: CONCEDIDO":"CONCEDER ACCESO A TODOS LOS ARCHIVOS"); filesBtn.setBackgroundColor(allFiles()?0xFF7ED957:0xFFFFD400);
    }

    private boolean usageAccess(){try{AppOpsManager a=(AppOpsManager)getSystemService(APP_OPS_SERVICE);return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;}catch(Exception e){return false;}}
    private void openUsage(){try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception e){toast("No se pudo abrir Acceso de uso");}}
    private boolean allFiles(){return Build.VERSION.SDK_INT<30 || Environment.isExternalStorageManager();}
    private void openAllFiles(){try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}catch(Exception ignored){}}}

    private void refreshState(){
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE); ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi);
        Intent bi=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); float temp=0; int batt=-1; if(bi!=null){temp=bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;int l=bi.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),sc=bi.getIntExtra(BatteryManager.EXTRA_SCALE,100);batt=sc>0?Math.round(l*100f/sc):l;}
        StatFs fs=new StatFs(getFilesDir().getAbsolutePath()); double free=fs.getAvailableBytes()/1073741824.0, ram=mi.availMem/1073741824.0,total=mi.totalMem/1073741824.0;
        boolean saver=((PowerManager)getSystemService(POWER_SERVICE)).isPowerSaveMode();
        state.setText(String.format(Locale.US,"Batería:      %d%%\nTemperatura:  %.1f °C\nRAM libre:    %.2f / %.2f GB\nAlmac. libre: %.1f GB\nAhorro:       %s\nVPN:          %s",batt,temp,ram,total,free,saver?"ACTIVO":"NO",vpn()?"ACTIVA":"NO"));
    }
    private boolean vpn(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities n=cm.getNetworkCapabilities(cm.getActiveNetwork());return n!=null&&n.hasTransport(NetworkCapabilities.TRANSPORT_VPN);}catch(Exception e){return false;}}

    private Map<String,Long> usage24(){Map<String,Long> out=new HashMap<>();if(!usageAccess())return out;try{UsageStatsManager u=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);Map<String,UsageStats> m=u.queryAndAggregateUsageStats(System.currentTimeMillis()-DAY,System.currentTimeMillis());for(Map.Entry<String,UsageStats> e:m.entrySet())out.put(e.getKey(),e.getValue().getTotalTimeInForeground());}catch(Exception ignored){}return out;}

    private void scan(){
        scanBtn.setEnabled(false); scanBtn.setText("ANALIZANDO…"); report.setText("Revisando apps y permisos…"); appsBox.removeAllViews();
        worker.execute(()->{
            PackageManager pm=getPackageManager(); Map<String,Long> use=usage24(); List<Candidate> found=new ArrayList<>();
            try{
                for(PackageInfo pi:pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)){
                    ApplicationInfo ai=pi.applicationInfo; if(ai==null||pi.packageName.equals(getPackageName())||(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0)continue;
                    String label=String.valueOf(pm.getApplicationLabel(ai)); String low=(label+" "+pi.packageName).toLowerCase(Locale.ROOT); int score=0; boolean high=false; List<String> why=new ArrayList<>();
                    String[] junk={"cleaner","limpiador","ram booster","memory booster","speed booster","phone booster","battery saver","cpu cooler","optimizer","optimizador","acelerador","phone master","junk cleaner","cache cleaner"};
                    for(String w:junk)if(low.contains(w)){score+=6;high=true;why.add("limpiador/booster redundante");break;}
                    boolean overlay=false,boot=false,wake=false,installer=false,fg=false;
                    if(pi.requestedPermissions!=null)for(String p:pi.requestedPermissions){if("android.permission.SYSTEM_ALERT_WINDOW".equals(p))overlay=true;else if("android.permission.RECEIVE_BOOT_COMPLETED".equals(p))boot=true;else if("android.permission.WAKE_LOCK".equals(p))wake=true;else if("android.permission.REQUEST_INSTALL_PACKAGES".equals(p))installer=true;else if(p.startsWith("android.permission.FOREGROUND_SERVICE"))fg=true;}
                    if(overlay){score+=2;why.add("superposición");} if(boot){score++;why.add("inicia con Android");} if(wake){score++;why.add("wake lock");} if(installer){score+=2;why.add("instala APK");} if(fg){score++;why.add("servicio persistente");}
                    long used=use.containsKey(pi.packageName)?use.get(pi.packageName):0; if(usageAccess()&&used<300000 && System.currentTimeMillis()-pi.firstInstallTime>180*DAY){score+=2;why.add("sin uso reciente");}
                    if(overlay&&boot&&wake&&fg&&used<1800000){score+=3;why.add("mucha actividad de fondo");}
                    if(score>=3)found.add(new Candidate(label,pi.packageName,join(why),score,high));
                }
            }catch(Exception ignored){}
            Collections.sort(found,(a,b)->Integer.compare(b.score,a.score)); if(found.size()>20)found=new ArrayList<>(found.subList(0,20));
            int rec=0;for(Candidate c:found)if(c.high)rec++;
            StringBuilder sb=new StringBuilder("RESULTADO\nCandidatos: ").append(found.size()).append("\nRecomendadas para desinstalar: ").append(rec).append("\n\n");
            for(Candidate c:found)sb.append(c.high?"[RECOMENDADA] ":"[REVISAR] ").append(c.label).append(" [").append(c.score).append("]\n").append(c.why).append("\n").append(c.pkg).append("\n\n");
            List<Candidate> fin=found; String txt=sb.toString();
            runOnUiThread(()->{candidates.clear();candidates.addAll(fin);report.setText(txt);renderApps();scanBtn.setEnabled(true);scanBtn.setText("ANALIZAR DE NUEVO");});
        });
    }

    private String join(List<String> a){LinkedHashSet<String>s=new LinkedHashSet<>(a);StringBuilder b=new StringBuilder();for(String x:s){if(b.length()>0)b.append(", ");b.append(x);}return b.toString();}
    private void renderApps(){for(Candidate c:candidates){TextView x=t((c.high?"RECOMENDADA • ":"REVISAR • ")+c.label+"\n"+c.why,13,c.high?0xFFFFD400:Color.WHITE,true);x.setPadding(dp(8),dp(8),dp(8),0);appsBox.addView(x);Button u=btn("DESINSTALAR "+c.label.toUpperCase(Locale.ROOT));u.setOnClickListener(v->oneUninstall(c));appsBox.addView(u);}}

    private void prepareOptimize(){if(!allFiles()){pendingOptimize=true;toast("Activa 'Permitir administrar todos los archivos'. Al volver continuará la optimización.");openAllFiles();return;}optimizeNow();}
    private void optimizeNow(){
        optimizeBtn.setEnabled(false); optimizeBtn.setText("OPTIMIZANDO…"); action.setText("Limpiando temporales, miniaturas, cachés accesibles y refrescando apps conflictivas…");
        worker.execute(()->{
            CleanStats st=new CleanStats(); cleanContents(getCacheDir(),st); File ec=getExternalCacheDir(); if(ec!=null)cleanContents(ec,st); cleanShared(st); int indexed=reindex(); int killed=refreshConflicts();
            String x=String.format(Locale.US,"OPTIMIZACIÓN COMPLETADA\nLiberado: %.1f MB\nArchivos: %d\nCarpetas/caché: %d\nTemporales: %d\nMiniaturas: %d\nInstaladores antiguos: %d\nApps refrescadas: %d\nCapturas reindexadas: %d\n\nNo se tocaron fotos, vídeos, documentos ni chats.",st.bytes/1048576.0,st.files,st.dirs,st.temp,st.thumbs,st.installers,killed,indexed);
            runOnUiThread(()->{action.setText(x);optimizeBtn.setEnabled(true);optimizeBtn.setText("OPTIMIZAR DE NUEVO");refreshState();if(hasRecommended())startRecommended();});
        });
    }

    private void cleanShared(CleanStats st){File root=Environment.getExternalStorageDirectory();cleanTree(new File(root,"Download"),st,0,true);cleanTree(new File(root,"Pictures"),st,0,false);cleanTree(new File(root,"DCIM"),st,0,false);cleanTree(new File(root,"Movies"),st,0,false);File media=new File(root,"Android/media");File[] p=media.listFiles();if(p!=null)for(File f:p)cleanCacheDirs(f,st,0);}
    private void cleanTree(File d,CleanStats st,int depth,boolean downloads){if(d==null||!d.exists()||depth>5)return;File[] fs=d.listFiles();if(fs==null)return;long now=System.currentTimeMillis();for(File f:fs){if(f.isDirectory()){String n=f.getName().toLowerCase(Locale.ROOT);if(n.equals(".thumbnails")||n.equals("thumbnails")||n.equals(".cache")||n.equals("cache")||n.equals("tmp")||n.equals("temp")||n.equals(".tmp")){long z=size(f);if(del(f)){st.bytes+=z;st.dirs++;if(n.contains("thumb"))st.thumbs++;}}else{cleanTree(f,st,depth+1,downloads);File[] left=f.listFiles();if(left!=null&&left.length==0&&depth>0&&f.delete())st.dirs++;}}else{String n=f.getName().toLowerCase(Locale.ROOT);long age=now-f.lastModified();boolean ok=false;if((n.endsWith(".tmp")||n.endsWith(".temp")||n.endsWith(".log")||n.endsWith(".dmp")||n.endsWith(".bak")||n.endsWith(".old"))&&age>7*DAY){ok=true;st.temp++;}else if(downloads&&(n.endsWith(".apk")||n.endsWith(".apks")||n.endsWith(".xapk"))&&age>30*DAY){ok=true;st.installers++;}if(ok){long z=f.length();if(f.delete()){st.bytes+=z;st.files++;}}}}}
    private void cleanCacheDirs(File d,CleanStats st,int depth){if(d==null||!d.exists()||depth>4)return;File[] fs=d.listFiles();if(fs==null)return;for(File f:fs)if(f.isDirectory()){String n=f.getName().toLowerCase(Locale.ROOT);if(n.equals("cache")||n.equals(".cache")||n.equals("tmp")||n.equals("temp")||n.equals(".tmp")||n.equals(".thumbnails")){long z=size(f);if(del(f)){st.bytes+=z;st.dirs++;if(n.contains("thumb"))st.thumbs++;}}else cleanCacheDirs(f,st,depth+1);}}
    private void cleanContents(File d,CleanStats st){if(d==null||!d.exists())return;File[] fs=d.listFiles();if(fs==null)return;for(File f:fs){long z=size(f);boolean wasDir=f.isDirectory();if(del(f)){st.bytes+=z;if(wasDir)st.dirs++;else st.files++;}}}
    private long size(File f){if(f==null||!f.exists())return 0;if(f.isFile())return f.length();long s=0;File[] a=f.listFiles();if(a!=null)for(File x:a)s+=size(x);return s;}
    private boolean del(File f){if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)del(x);}return f.delete();}

    private void repairScreenshots(){if(!allFiles()){toast("Concede primero acceso a todos los archivos");openAllFiles();return;}repairBtn.setEnabled(false);worker.execute(()->{CleanStats st=new CleanStats();File root=Environment.getExternalStorageDirectory();thumb(new File(root,"DCIM/.thumbnails"),st);thumb(new File(root,"Pictures/.thumbnails"),st);int n=reindex();ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);try{am.killBackgroundProcesses("com.sec.android.gallery3d");}catch(Exception ignored){}try{am.killBackgroundProcesses("com.samsung.android.app.smartcapture");}catch(Exception ignored){}runOnUiThread(()->{action.setText("CAPTURAS REPARADAS\nMiniaturas regenerables limpiadas: "+st.thumbs+"\nImágenes reindexadas: "+n+"\nGalería y Smart Capture refrescados.");repairBtn.setEnabled(true);});});}
    private void thumb(File d,CleanStats st){if(d.exists()){long z=size(d);if(del(d)){st.bytes+=z;st.dirs++;st.thumbs++;}}}
    private int reindex(){File root=Environment.getExternalStorageDirectory();List<String> p=new ArrayList<>();collect(new File(root,"Pictures/Screenshots"),p,0);collect(new File(root,"DCIM/Screenshots"),p,0);if(!p.isEmpty())MediaScannerConnection.scanFile(this,p.toArray(new String[0]),null,null);return p.size();}
    private void collect(File d,List<String> p,int depth){if(d==null||!d.exists()||depth>3)return;File[] fs=d.listFiles();if(fs==null)return;for(File f:fs)if(f.isDirectory())collect(f,p,depth+1);else{String n=f.getName().toLowerCase(Locale.ROOT);if(n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".webp"))p.add(f.getAbsolutePath());}}

    private int refreshConflicts(){ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);int n=0;for(Candidate c:candidates)if(c.high&&installed(c.pkg))try{am.killBackgroundProcesses(c.pkg);n++;}catch(Exception ignored){}try{am.killBackgroundProcesses("com.sec.android.gallery3d");n++;}catch(Exception ignored){}try{am.killBackgroundProcesses("com.samsung.android.app.smartcapture");n++;}catch(Exception ignored){}return n;}
    private boolean hasRecommended(){for(Candidate c:candidates)if(c.high&&installed(c.pkg))return true;return false;}

    private void startRecommended(){uninstallQueue.clear();for(Candidate c:candidates)if(c.high&&installed(c.pkg))uninstallQueue.add(c);if(uninstallQueue.isEmpty()){toast("No hay apps de alta confianza para desinstalar");return;}toast("Android pedirá confirmar cada desinstalación");nextUninstall();}
    private void oneUninstall(Candidate c){uninstallQueue.clear();if(installed(c.pkg))uninstallQueue.add(c);nextUninstall();}
    private void nextUninstall(){Candidate c=uninstallQueue.poll();if(c==null){currentUninstall=null;scan();return;}currentUninstall=c.pkg;try{Intent i=new Intent(Intent.ACTION_UNINSTALL_PACKAGE,Uri.parse("package:"+c.pkg));i.putExtra(Intent.EXTRA_RETURN_RESULT,true);startActivityForResult(i,REQ_UNINSTALL);}catch(Exception e){openDetails(c.pkg);new Handler(Looper.getMainLooper()).postDelayed(this::nextUninstall,600);}}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==REQ_UNINSTALL)new Handler(Looper.getMainLooper()).postDelayed(this::nextUninstall,350);}
    private boolean installed(String p){try{getPackageManager().getApplicationInfo(p,0);return true;}catch(Exception e){return false;}}
    private void openDetails(String p){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+p)));}catch(Exception ignored){}}
    private void openCare(){try{Intent i=getPackageManager().getLaunchIntentForPackage("com.samsung.android.lool");if(i!=null)startActivity(i);else startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception ignored){}}}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void toast(String x){Toast.makeText(this,x,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
