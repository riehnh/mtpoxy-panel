package ru.mtproxy.picker;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class SourceListsCheck {
 public static void main(String[] args)throws Exception{
  String hex="abcdef0123456789abcdef0123456789";
  byte[] bytes=new byte[16];for(int i=0;i<16;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(2*i,2*i+2),16);
  String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  String first="tg://proxy?server=EXAMPLE.com.&port=443&secret="+hex.toUpperCase(Locale.ROOT);
  String second="https://t.me/proxy?secret="+hex+"&port=443&server=example.com";
  String third="tg://proxy?server=example.com&port=443&secret="+encoded;
  ArrayList<MainActivity.Proxy> free=new ArrayList<>();MainActivity.parse(first+"\r\n"+second+"\n"+third,free);
  if(free.size()!=1)throw new AssertionError("Free aliases must merge, got "+free.size());
  MainActivity.parse(second+"\n"+third,free);if(free.size()!=1)throw new AssertionError("Cross-source duplicates");
  List<ManualEntry> manual=ManualEntry.parse(first+"\n"+second+"\n"+third);
  if(manual.size()!=1)throw new AssertionError("Manual aliases must merge, got "+manual.size());
  MainActivity.parse("tg://proxy?server=example.com&port=444&secret="+hex,free);
  MainActivity.parse("tg://proxy?server=example.com&port=443&secret=0123456789abcdef0123456789abcdef",free);
  if(free.size()!=3)throw new AssertionError("Different port or secret must be preserved");
  if(ManualEntry.parse(first+"\ntg://webproxy?server=example.com&secret="+hex).size()!=2)throw new AssertionError("WEB and MTProto are different proxies");
  for(String path:args){String raw=new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);free.clear();MainActivity.parse(raw,free);if(free.isEmpty())throw new AssertionError("No proxies parsed: "+path);int count=free.size();MainActivity.parse(raw,free);if(free.size()!=count)throw new AssertionError("Repeated source grew: "+path);System.out.println(path+": "+count+" unique proxies");}
  System.out.println("PASS: multiline input, cross-source duplicates, URI aliases, host/secret normalization and distinct credentials");
 }
}
