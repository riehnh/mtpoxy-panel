package ru.mtproxy.picker;

import java.util.Base64;
import java.util.Locale;

/** Same endpoint and decoded secret, independent of link spelling. */
final class ProxyIdentity {
 static String key(String host,int port,String secret,boolean web){
  host=host.toLowerCase(Locale.ROOT);
  if(host.endsWith("."))host=host.substring(0,host.length()-1);
  String normalized;
  if(secret.matches("(?i)[a-f0-9]+")&&secret.length()%2==0)normalized=secret.toLowerCase(Locale.ROOT);
  else{
   byte[] bytes=Base64.getUrlDecoder().decode(secret);
   StringBuilder hex=new StringBuilder(bytes.length*2);
   for(byte b:bytes){hex.append(Character.forDigit((b>>>4)&15,16));hex.append(Character.forDigit(b&15,16));}
   normalized=hex.toString();
  }
  return (web?"web:":"mt:")+host+":"+port+":"+normalized;
 }
}
