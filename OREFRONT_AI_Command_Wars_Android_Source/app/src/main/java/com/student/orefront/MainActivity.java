package com.student.orefront;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.*;
import java.util.concurrent.*;

/** Native Android Canvas UI. No local Mac server, assets, or third-party game engine required. */
public final class MainActivity extends Activity implements LanRoom.Listener {
    private enum Page { HOME, LOBBY, BATTLE }
    private Page page=Page.HOME;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService offlineClock=Executors.newSingleThreadScheduledExecutor();
    private WorldView view;
    private volatile GameCore state;
    private LanRoom.Server server;
    private LanRoom.Client client;
    private boolean offline=false,connected=false;
    private String room="",hostIP="",info="",intel="",username="Commander";
    private String token=UUID.randomUUID().toString();
    private int myId=0,selectedPlayer=0,selectedUnit=-1,selectedBuilding=-1;
    private String placement="";

    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        username=getPreferences(0).getString("name","Commander");
        token=getPreferences(0).getString("token",UUID.randomUUID().toString());
        getPreferences(0).edit().putString("token",token).apply();
        view=new WorldView(this);setContentView(view);
        offlineClock.scheduleAtFixedRate(()->{
            if(!offline||state==null||!state.phase.equals("PLAY"))return;
            ui.post(()->{if(offline&&state!=null&&state.phase.equals("PLAY")){state.tick();if(view!=null)view.invalidate();}});
        },500,500,TimeUnit.MILLISECONDS);
    }
    @Override protected void onDestroy(){leaveRoom();offlineClock.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(page==Page.HOME)super.onBackPressed();else new AlertDialog.Builder(this).setTitle("Leave OREFRONT?").setMessage("This will disconnect your phone from the match.").setNegativeButton("Cancel",null).setPositiveButton("Leave",(d,w)->leaveRoom()).show();}
    private void toast(String s){ui.post(()->{info=s;Toast.makeText(this,s,Toast.LENGTH_SHORT).show();if(view!=null)view.invalidate();});}
    private void refresh(GameCore w,int id){ui.post(()->{
        state=w;myId=id;connected=true;
        if(w.phase.equals("LOBBY"))page=Page.LOBBY;
        else page=Page.BATTLE;
        view.invalidate();
    });}
    @Override public void onState(GameCore w,int id){refresh(w,id);}
    @Override public void onMessage(String msg){ui.post(()->{if(msg.startsWith("INTEL"))intel=msg;else info=msg;view.invalidate();});}
    @Override public void onDisconnected(String msg){ui.post(()->{connected=false;info=msg;view.invalidate();new AlertDialog.Builder(this).setTitle("LAN connection lost").setMessage(msg+"\nYour faction stays in the host's match. Return to Join Room and reconnect using the same name and device.").setPositiveButton("OK",null).show();});}
    private void leaveRoom(){
        if(client!=null){client.command("LEAVE");client.close();client=null;}
        if(server!=null){server.close();server=null;}
        offline=false;connected=false;page=Page.HOME;room="";hostIP="";state=null;placement="";selectedUnit=-1;selectedBuilding=-1;info="";
        if(view!=null)view.invalidate();
    }
    private void chooseName(Runnable next){EditText input=new EditText(this);input.setSingleLine(true);input.setText(username);input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Commander username").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{
            username=GameCore.clean(input.getText().toString());getPreferences(0).edit().putString("name",username).apply();next.run();
        }).show();
    }
    private void startAI(){new AlertDialog.Builder(this).setTitle("Offline AI Difficulty")
        .setItems(new String[]{"Easy • 2 AI opponents","Medium • 3 AI opponents","Hard • 5 AI opponents"},(d,which)->{
            leaveRoom();offline=true;connected=true;state=new GameCore();state.addOfflineAI(which==0?2:which==1?3:5,which);
            page=Page.BATTLE;myId=0;info="AI uses resource planning and shortest-path movement.";view.invalidate();
        }).show();}
    private void host(){leaveRoom();new Thread(()->{
        try{LanRoom.Server s=new LanRoom.Server(username,token,this);
            ui.post(()->{server=s;room=s.code();hostIP=s.ip();connected=true;page=Page.LOBBY;info="Room hosted on this phone";state=s.snapshot();view.invalidate();});
        }catch(Exception ex){toast("Cannot create room: "+ex.getMessage());}
    },"orefront-host-start").start();}
    private void manualJoin(){
        LinearLayout content=new LinearLayout(this);content.setPadding(32,18,32,6);content.setOrientation(LinearLayout.VERTICAL);
        TextView hint=new TextView(this);hint.setText("Connect to the same hotspot or Wi-Fi. Enter the 6-digit room code. If searching fails, enter the Host IP.");content.addView(hint);
        EditText code=new EditText(this);code.setHint("6-digit room code");code.setInputType(InputType.TYPE_CLASS_NUMBER);content.addView(code);
        EditText ip=new EditText(this);ip.setHint("Host IP (optional)");ip.setSingleLine(true);content.addView(ip);
        new AlertDialog.Builder(this).setTitle("JOIN LAN ROOM").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Join",(d,w)->{
            String c=code.getText().toString().trim(),addr=ip.getText().toString().trim();
            if(!c.matches("[0-9]{6}")){toast("Enter 6 numeric digits");return;}
            join(c,addr);
        }).show();
    }
    private void join(String c,String address){leaveRoom();info="Searching for host...";view.invalidate();
        new Thread(()->{String ip=address;try{
            if(ip.isEmpty()){
                for(LanRoom.RoomInfo r:LanRoom.search(2100))if(r.code.equals(c)){ip=r.host;break;}
                if(ip.isEmpty())throw new IllegalStateException("Room not found. Enter the Host IP printed on host phone.");
            }
            final String destination=ip;
            LanRoom.Client joined=new LanRoom.Client(destination,c,username,token,this);
            ui.post(()->{client=joined;room=c;hostIP=destination;connected=true;page=(state!=null&&!state.phase.equals("LOBBY"))?Page.BATTLE:Page.LOBBY;info="Connected to "+destination;view.invalidate();});
        }catch(Exception ex){toast("Join failed: "+ex.getMessage());}
        },"orefront-join").start();
    }
    private void searchRooms(){info="Searching for rooms...";view.invalidate();
        new Thread(()->{
            List<LanRoom.RoomInfo> rooms=LanRoom.search(2200);
            ui.post(()->{
                if(rooms.isEmpty()){
                    new AlertDialog.Builder(this).setTitle("No rooms discovered").setMessage("Make sure phones share the same hotspot. Some networks block broadcast discovery. Use the room code and host IP instead.")
                        .setPositiveButton("Join manually",(d,w)->chooseName(this::manualJoin)).setNegativeButton("Close",null).show();
                }else{
                    String[] labels=new String[rooms.size()];for(int i=0;i<rooms.size();i++)labels[i]=rooms.get(i).toString();
                    new AlertDialog.Builder(this).setTitle("Available LAN Rooms").setItems(labels,(d,which)->chooseName(()->join(rooms.get(which).code,rooms.get(which).host)))
                        .setNegativeButton("Cancel",null).show();
                }
                info="";view.invalidate();
            });
        },"orefront-search").start();
    }
    private boolean command(String order){
        if(offline){if(state==null)return false;String[] c=order.split("\\|");boolean ok=false;
            try{switch(c[0]){
                case "BUILD":ok=state.build(0,c[1],Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                case "UPGRADE":ok=state.upgrade(0,Integer.parseInt(c[1]));break;
                case "TRAIN":ok=state.recruit(0,c[1]);break;
                case "MOVE":ok=state.move(0,Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                default:break;
            }}catch(Exception ignored){}
            if(!ok)toast("Order rejected. Check resources / position / buildings.");view.invalidate();return ok;
        }
        if(server!=null)return server.command(order);
        return client!=null&&client.command(order);
    }
    private void configure(String field,String value){command("CFG|"+field+"|"+value);}
    private boolean amHost(){return server!=null;}
    private static final class Hit {final RectF box;final Runnable fn;Hit(RectF r,Runnable f){box=r;fn=f;}}
    private final class WorldView extends View {
        private final Paint paint=new Paint(3);
        private final ArrayList<Hit> hits=new ArrayList<>();
        private final int BG=0xFF091522,PANEL=0xFF122637,EDGE=0xFF35586C,WHITE=0xFFE7F7FF,MUTED=0xFF8DAABD,ACCENT=0xFF49E0CD;
        WorldView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void rect(Canvas c,float x,float y,float w,float h,int color,float radius){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawRoundRect(new RectF(x,y,x+w,y+h),radius,radius,paint);}
        private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){paint.setColor(color);paint.setStrokeWidth(width);c.drawLine(x,y,xx,yy,paint);}
        private void text(Canvas c,String label,float x,float baseline,int size,int color,boolean bold){
            paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(size);
            c.drawText(label,x,baseline,paint);
        }
        private void button(Canvas c,String label,float x,float y,float w,float h,boolean active,Runnable onClick){
            rect(c,x,y,w,h,active?0xFF17695F:PANEL,11);paint.setStyle(Paint.Style.STROKE);paint.setColor(active?ACCENT:EDGE);paint.setStrokeWidth(1.5f);c.drawRoundRect(new RectF(x,y,x+w,y+h),11,11,paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(17);paint.setTypeface(Typeface.DEFAULT_BOLD);float len=paint.measureText(label);
            text(c,label,x+(w-len)/2,y+h/2+6,17,WHITE,true);hits.add(new Hit(new RectF(x,y,x+w,y+h),onClick));
        }
        private void header(Canvas c,String subtitle){rect(c,0,0,1280,75,0xFF0B2232,0);
            text(c,"OREFRONT",34,44,32,ACCENT,true);text(c,"AI COMMAND WARS",270,42,21,WHITE,true);text(c,subtitle,842,42,17,MUTED,false);
            line(c,0,74,1280,74,EDGE,2);
        }
        @Override protected void onDraw(Canvas raw){super.onDraw(raw);Canvas c=raw;c.save();c.scale(getWidth()/1280f,getHeight()/720f);
            c.drawColor(BG);hits.clear();
            if(page==Page.HOME)drawHome(c);
            else if(page==Page.LOBBY)drawLobby(c);
            else drawBattle(c);
            if(info!=null&&!info.isEmpty())text(c,info.substring(0,Math.min(115,info.length())),31,708,17,0xFFFFD384,false);
            c.restore();
        }
        private void drawHome(Canvas c){header(c,"LAN • 6 HUMAN PLAYERS MAX");
            rect(c,54,115,740,490,0xFF102A3C,20);
            text(c,"COMMAND YOUR FACTION",85,209,38,WHITE,true);
            text(c,"BUILD • EXPAND • RECRUIT • CONQUER",85,255,24,ACCENT,true);
            text(c,"Genuine phone-hosted LAN strategy",86,311,23,MUTED,false);
            text(c,"2–6 human commanders on same Wi-Fi / hotspot",86,350,19,MUTED,false);
            text(c,"No MacBook server. No internet required for LAN.",86,383,19,MUTED,false);
            drawMiniMap(c);
            button(c,"PLAY VS AI",850,155,353,70,true,()->startAI());
            button(c,"CREATE LAN ROOM",850,247,353,70,false,()->chooseName(()->host()));
            button(c,"SEARCH LAN ROOMS",850,339,353,70,false,()->searchRooms());
            button(c,"JOIN BY CODE + IP",850,431,353,70,false,()->chooseName(()->manualJoin()));
            button(c,"HOW TO PLAY",850,526,353,60,false,()->new AlertDialog.Builder(MainActivity.this).setTitle("OREFRONT QUICK GUIDE")
                .setMessage("Tap an empty tile near your base, then build MINE, REFINERY, GENERATOR, BARRACKS, FACTORY or AIRFIELD. Recruit SOLDIER, TANK, AIRCRAFT and HERO. Tap your unit and then tap its destination to move. Capture yellow sectors; destroy hostile command bases. In LAN, each phone controls exactly its own colored faction. The host validates all orders.")
                .setPositiveButton("OK",null).show());
        }
        private void drawMiniMap(Canvas c){
            for(int i=0;i<6;i++){int x=120+(i%3)*170,y=440+(i/3)*70;rect(c,x,y,122,45,0xFF1B4557,8);paint.setColor(Color.parseColor(GameCore.COLORS[i]));c.drawCircle(x+19,y+22,10,paint);
                text(c,"P"+(i+1),x+42,y+29,21,WHITE,true);
            }
        }
        private void drawLobby(Canvas c){
            if(state==null){header(c,"Connecting...");text(c,"Opening local LAN lobby...",65,170,27,WHITE,true);return;}
            header(c,(amHost()?"HOST":"CLIENT")+" • "+(connected?"CONNECTED":"DISCONNECTED"));
            text(c,"ROOM "+state.roomCode,55,117,32,ACCENT,true);
            text(c,"HOST IP: "+(amHost()?hostIP:hostIP.isEmpty()?"discovering":hostIP),385,115,18,MUTED,false);
            button(c,"LEAVE ROOM",1065,82,164,48,false,()->leaveRoom());
            rect(c,34,149,535,482,PANEL,16);text(c,"MATCH SETTINGS",55,188,24,WHITE,true);
            if(amHost()){
                configButton(c,0,"MAX PLAYERS",state.maxPlayers+"",()->configure("MAX",String.valueOf(state.maxPlayers>=6?2:state.maxPlayers+1)));
                configButton(c,1,"MATCH TYPE",state.mode,()->configure("MODE",GameCore.MODES[(Arrays.asList(GameCore.MODES).indexOf(state.mode)+1)%GameCore.MODES.length]));
                configButton(c,2,"MAP",GameCore.MAPS[state.map],()->configure("MAP",""+((state.map+1)%3)));
                configButton(c,3,"DURATION",state.matchMinutes+" MIN",()->configure("TIME",""+(state.matchMinutes==5?10:state.matchMinutes==10?15:state.matchMinutes==15?20:5)));
                configButton(c,4,"RESOURCES",new String[]{"STANDARD","BOOSTED","HIGH"}[state.startResources],()->configure("RESOURCES",""+((state.startResources+1)%3)));
                configButton(c,5,"AI DIFFICULTY",new String[]{"EASY","MEDIUM","HARD"}[state.aiDifficulty],()->configure("AI",""+((state.aiDifficulty+1)%3)));
                configButton(c,6,"OPTIONAL AI SLOTS",""+state.botCount,()->configure("BOTS",""+((state.botCount+1)%6)));
            }else{
                String[] settings={"Players: "+state.maxPlayers,"Mode: "+state.mode,"Map: "+GameCore.MAPS[state.map],"Duration: "+state.matchMinutes+" min","Resources: "+new String[]{"Standard","Boosted","High"}[state.startResources],"AI: "+new String[]{"Easy","Medium","Hard"}[state.aiDifficulty],"Optional bots: "+state.botCount};
                for(int i=0;i<settings.length;i++)text(c,settings[i],65,239+i*49,23,MUTED,false);
            }
            rect(c,594,149,645,482,PANEL,16);text(c,"COMMANDERS  "+state.connectedHumans()+" / "+state.maxPlayers,617,188,24,WHITE,true);
            for(int i=0;i<6;i++){
                final int slot=i;
                float y=208+i*59;
                rect(c,611,y,609,52,selectedPlayer==i?0xFF1C4954:0xFF162F3D,9);
                GameCore.Player p=state.player(i);
                if(p!=null){paint.setColor(Color.parseColor(GameCore.COLORS[i]));c.drawCircle(637,y+26,12,paint);
                    text(c,"P"+(i+1)+" "+p.name,661,y+23,20,WHITE,true);
                    text(c,GameCore.FACTIONS[p.faction]+"  • TEAM "+(p.team+1),661,y+43,14,MUTED,false);
                    String status=p.bot?"AI":!p.connected?"OFFLINE":p.ready?"READY":"WAITING";
                    text(c,status,1090,y+31,17,p.ready||p.bot?ACCENT:0xFFFFBD75,true);
                }else text(c,"SLOT "+(i+1)+" • AVAILABLE",663,y+31,17,MUTED,false);
                hits.add(new Hit(new RectF(611,y,1220,y+52),()->{selectedPlayer=slot;invalidate();}));
            }
            if(amHost()){
                button(c,"TEAM +",626,587,166,35,false,()->{
                    GameCore.Player p=state.player(selectedPlayer);
                    if(p!=null)command("TEAM|"+p.id+"|"+((p.team+1)%6));
                    else toast("Choose an occupied slot. Team editing requires CUSTOM mode.");
                });
                button(c,"FACTION +",807,587,174,35,false,()->{
                    GameCore.Player p=state.player(selectedPlayer);if(p!=null)command("FACTION|"+p.id+"|"+((p.faction+1)%6));
                });
            }
            GameCore.Player me=state.player(myId);
            button(c,me!=null&&me.ready?"NOT READY":"I'M READY",60,649,235,49,me!=null&&me.ready,()->command("READY|"+((state.player(myId)!=null&&state.player(myId).ready)?"0":"1")));
            if(amHost())button(c,"START MATCH",318,649,245,49,true,()->command("START"));
            else text(c,"Host will start when everyone is ready",318,680,19,MUTED,false);
            text(c,"Tap a commander to choose team/faction. Custom teams need CUSTOM mode.",607,655,15,MUTED,false);
            text(c,"No more than six HUMAN players. Host is always Player 1.",608,682,15,MUTED,false);
        }
        private void configButton(Canvas c,int i,String label,String value,Runnable action){int y=206+i*56;
            text(c,label,53,y+16,14,MUTED,true);button(c,value,258,y-5,284,45,false,action);
        }
        private void drawBattle(Canvas c){if(state==null){drawHome(c);return;}
            GameCore w=state;
            GameCore.Player me=w.player(myId);
            header(c,(offline?"OFFLINE AI":"LAN "+w.roomCode)+" • "+(connected?"CONNECTED":"OFFLINE"));
            int remaining=Math.max(0,w.matchMinutes*60-w.elapsedTicks/2);
            text(c,"TIME "+(remaining/60)+":"+String.format(java.util.Locale.US,"%02d",remaining%60),28,101,19,ACCENT,true);
            text(c,"MODE "+w.mode+" | MAP "+GameCore.MAPS[w.map],229,101,17,MUTED,false);
            drawMap(c,w);
            rect(c,853,106,402,526,PANEL,14);
            if(me!=null){text(c,"COMMANDER "+(myId+1)+"  •  "+GameCore.FACTIONS[me.faction],876,141,19,WHITE,true);
                text(c,"Gold "+me.gold+"   Oil "+me.oil,876,169,19,0xFFFFD47F,true);
                text(c,"Iron "+me.iron+"   Energy "+me.energy,876,195,19,0xFF7ACEE7,true);
                text(c,"Base HP: "+me.baseHP+"  |  Team "+(me.team+1),876,221,18,MUTED,false);
            }
            text(c,"BUILD / RECRUIT / COMMAND",876,250,18,ACCENT,true);
            String[] labels={"MINE","REFINERY","GENERATOR","BARRACKS","FACTORY","AIRFIELD","SOLDIER","TANK","AIRCRAFT","HERO","UPGRADE","AI CONTROL"};
            for(int i=0;i<labels.length;i++){
                String label=labels[i];final String choice=label;
                float x=871+(i%2)*187,y=268+(i/2)*58;
                boolean active=label.equals(placement);
                button(c,label,x,y,173,50,active,()->actionChoice(choice));
            }
            String selection=placement.isEmpty()?"Tap a friendly unit, then tap destination":"PLACE "+placement+": choose a free nearby tile";
            text(c,selection,36,665,18,ACCENT,true);
            text(c,"ORDER: "+w.lastEvent,36,692,16,MUTED,false);
            String currentIntel=(offline||amHost())?w.intelligence(myId):intel;
            if(!currentIntel.isEmpty())text(c,currentIntel.substring(0,Math.min(48,currentIntel.length())),863,655,14,0xFFFFD384,false);
            if(me!=null)text(c,"SECTORS "+w.sectors.stream().filter(s->s.owner==myId).count()+" / "+w.sectors.size(),863,687,15,MUTED,false);
            if(!w.phase.equals("PLAY")){
                rect(c,205,239,810,207,0xEF0B2B3C,16);
                text(c,"MATCH FINISHED",331,304,38,ACCENT,true);
                text(c,w.result,331,358,26,WHITE,true);
                button(c,"MAIN MENU",460,390,365,48,true,()->leaveRoom());
            }
            button(c,"EXIT",1131,22,118,42,false,()->new AlertDialog.Builder(MainActivity.this)
                .setTitle("Leave match?").setMessage("This disconnects your phone. Disconnected bases remain until destroyed or match end.")
                .setNegativeButton("Cancel",null).setPositiveButton("Leave",(d,v)->leaveRoom()).show());
        }
        private void drawMap(Canvas c,GameCore w){
            final float ox=29,oy=110,size=44;
            rect(c,ox-3,oy-3,18*size+6,12*size+6,0xFF365F70,6);
            for(int y=0;y<GameCore.H;y++)for(int x=0;x<GameCore.W;x++){
                boolean stone=w.rock[x][y];int bg=stone?0xFF30475A:(x+y+w.map)%2==0?0xFF17394A:0xFF1C4251;
                float px=ox+x*size,py=oy+y*size;rect(c,px,py,size-1,size-1,bg,2);
                if(stone){line(c,px+10,py+11,px+33,py+32,0xFF5C7988,3);}
            }
            for(GameCore.Sector s:w.sectors){int x=(int)(ox+s.x*size),y=(int)(oy+s.y*size);
                rect(c,x+9,y+9,26,26,s.owner<0?0xFFF8D369:Color.parseColor(GameCore.COLORS[s.owner]),5);
                text(c,"S",x+16,y+29,19,BG,true);
            }
            for(GameCore.Player p:w.players.values()){
                if(p.baseHP<=0)continue;
                float x=ox+p.baseX*size,y=oy+p.baseY*size;
                rect(c,x+3,y+3,size-7,size-7,Color.parseColor(GameCore.COLORS[p.id]),5);
                text(c,"HQ",x+9,y+27,16,BG,true);
                rect(c,x+5,y+38,34,4,0xFF692522,2);rect(c,x+5,y+38,Math.max(0,34*p.baseHP/260f),4,0xFF71EBAB,2);
            }
            for(GameCore.Building b:w.buildings){float x=ox+b.x*size,y=oy+b.y*size;
                rect(c,x+5,y+5,size-11,size-11,0xFF19242F,5);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(selectedBuilding==b.id?4:2);paint.setColor(Color.parseColor(GameCore.COLORS[b.owner]));
                c.drawRect(x+5,y+5,x+size-6,y+size-6,paint);paint.setStyle(Paint.Style.FILL);
                text(c,b.type.substring(0,Math.min(2,b.type.length())),x+11,y+28,17,WHITE,true);
                if(b.level>1)text(c,""+b.level,x+32,y+12,11,0xFFFFD47F,true);
            }
            for(GameCore.Unit u:w.units){float x=ox+(u.x+.5f)*size,y=oy+(u.y+.5f)*size;
                paint.setColor(Color.parseColor(GameCore.COLORS[u.owner]));c.drawCircle(x,y,u.type.equals("TANK")?13:10,paint);
                if(selectedUnit==u.id){paint.setColor(0xFFFFFF64);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);c.drawCircle(x,y,19,paint);paint.setStyle(Paint.Style.FILL);}
                text(c,u.type.equals("SOLDIER")?"S":u.type.equals("TANK")?"T":u.type.equals("AIRCRAFT")?"A":"H",x-6,y+6,17,0xFF061322,true);
            }
        }
        private void actionChoice(String choice){if(state==null||!state.phase.equals("PLAY"))return;
            if(Arrays.asList(GameCore.BUILDINGS).contains(choice)){
                placement=placement.equals(choice)?"":choice;info="Choose a nearby unoccupied tile to build "+choice;
            }else if(Arrays.asList(GameCore.TROOPS).contains(choice)){
                command("TRAIN|"+choice);placement="";
            }else if(choice.equals("UPGRADE")){
                if(selectedBuilding<0)toast("Tap your building first");else command("UPGRADE|"+selectedBuilding);
            }else if(choice.equals("AI CONTROL")){
                if(amHost())takeoverDialog();else toast("Only the host assigns AI takeover");
            }
            invalidate();
        }
        private void takeoverDialog(){if(server==null||state==null)return;
            ArrayList<GameCore.Player> disconnected=new ArrayList<>();
            for(GameCore.Player p:state.players.values())if(!p.bot&&!p.connected)disconnected.add(p);
            if(disconnected.isEmpty()){toast("No disconnected players");return;}
            String[] names=new String[disconnected.size()];for(int i=0;i<names.length;i++)names[i]=disconnected.get(i).name+(disconnected.get(i).ai?" [AI ACTIVE]":" [UNCONTROLLED]");
            new AlertDialog.Builder(MainActivity.this).setTitle("AI control for disconnected faction")
                .setItems(names,(d,which)->{
                    GameCore.Player p=disconnected.get(which);command("AI_TAKEOVER|"+p.id+"|"+(p.ai?"0":"1"));
                }).setNegativeButton("Cancel",null).show();
        }
        private void mapTapped(float x,float y){if(state==null||!state.phase.equals("PLAY"))return;
            int col=(int)((x-29)/44),row=(int)((y-110)/44);
            if(!GameCore.in(col,row))return;
            if(!placement.isEmpty()){
                command("BUILD|"+placement+"|"+col+"|"+row);placement="";invalidate();return;
            }
            for(GameCore.Unit u:state.units)if(u.owner==myId&&u.x==col&&u.y==row){selectedUnit=u.id;selectedBuilding=-1;info="Unit "+u.type+" selected. Tap a destination.";invalidate();return;}
            for(GameCore.Building b:state.buildings)if(b.owner==myId&&b.x==col&&b.y==row){selectedBuilding=b.id;selectedUnit=-1;info=b.type+" level "+b.level+" selected. Tap UPGRADE.";invalidate();return;}
            if(selectedUnit!=-1){command("MOVE|"+selectedUnit+"|"+col+"|"+row);info="Movement order issued";}
            else {info="Tap your unit to select it, or choose a building";}
            invalidate();
        }
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=event.getX()*1280f/getWidth(),y=event.getY()*720f/getHeight();
            for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.box.contains(x,y)){h.fn.run();invalidate();return true;}}
            if(page==Page.BATTLE&&x>=29&&x<29+18*44&&y>=110&&y<110+12*44)mapTapped(x,y);
            return true;
        }
    }
}
