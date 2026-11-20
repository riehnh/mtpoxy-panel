package ru.mtproxy.picker;
import java.net.*;
import java.util.*;
/** Share-link parser shared by the manual editor and local JVM tests. */
final class ManualEntry {
 final String link,host,secret; final int port; final boolean web; long ms=-2;
 ManualEntry(String link,String host,int port,String secret,boolean web){this.link=link;this.host=host;this.port=port;this.secret=secret;this.web=web;}
 static List<ManualEntry> parse(String text){LinkedHashMap<String,ManualEntry> entries=new LinkedHashMap<>();java.util.regex.Matcher matches=java.util.regex.Pattern.compile("(?:tg://(?:webproxy|proxy)|https://t\\.me/(?:webproxy|proxy))\\?[^\\s<>\\\"']+").matcher(text.replace("&amp;","&"));while(matches.find()&&entries.size()<3000){try{URI uri=new URI(matches.group());boolean web="webproxy".equals(uri.getHost())||"/webproxy".equals(uri.getPath());String host=MainActivity.query(uri,"server"),secret=MainActivity.query(uri,"secret");if(host==null||!host.matches("[A-Za-z0-9.:-]{1,253}")||!MainActivity.validSecret(secret))continue;host=host.toLowerCase(Locale.ROOT);if(host.endsWith("."))host=host.substring(0,host.length()-1);int port=web?443:Integer.parseInt(MainActivity.query(uri,"port"));if(port<1||port>65535)continue;String link="tg://"+(web?"webproxy":"proxy")+"?server="+URLEncoder.encode(host,"UTF-8")+(web?"":"&port="+port)+"&secret="+URLEncoder.encode(secret,"UTF-8");entries.putIfAbsent(link,new ManualEntry(link,host,port,secret,web));}catch(Exception ignored){}}return new ArrayList<>(entries.values());}
}
