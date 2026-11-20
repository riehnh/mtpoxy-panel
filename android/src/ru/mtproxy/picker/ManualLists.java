package ru.mtproxy.picker;
import java.util.*;
final class ManualLists {
 static boolean dead(ManualEntry e){return e.ms==-1;}
 static boolean matches(ManualEntry e,String query,int filter){boolean status=filter==0||filter==1&&e.ms>=0||filter==2&&dead(e)||filter==3&&e.ms==-2;String text=e.host+":"+e.port+" "+(e.web?"web":"mtproto");return status&&text.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
 static ArrayList<ManualEntry> sorted(List<ManualEntry> input,int mode){ArrayList<ManualEntry> result=new ArrayList<>(input);result.sort((a,b)->{if(mode==0){int order=Long.compare(a.ms<0?Long.MAX_VALUE:a.ms,b.ms<0?Long.MAX_VALUE:b.ms);if(order!=0)return order;}return a.host.compareToIgnoreCase(b.host);});return result;}
}
