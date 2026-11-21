package ru.mtproxy.picker;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;
public final class FreeFlowCheck {
 public static void main(String[] args)throws Exception{
  MainActivity.Proxy slow=new MainActivity.Proxy("slow.example",443,"a"),fast=new MainActivity.Proxy("fast.example",443,"b"),dead=new MainActivity.Proxy("dead.example",443,"c");slow.ms=120;fast.ms=10;dead.ms=-1;
  List<MainActivity.Proxy> original=Arrays.asList(slow,dead,fast);
  ArrayList<MainActivity.Proxy> ranked=MainActivity.candidateList(original,null);
  if(ranked.size()!=2||ranked.get(0)!=fast||original.get(0)!=slow)throw new AssertionError("Best selection / original order");
  ranked=MainActivity.candidateList(original,dead);
  if(ranked.size()!=3||ranked.get(0)!=dead||ranked.get(1)!=fast)throw new AssertionError("Wanted unavailable proxy must be checked first");
  ranked=MainActivity.candidateList(original,fast);
  if(ranked.size()!=2||ranked.get(0)!=fast)throw new AssertionError("No duplicate selected proxy");
  if(!MainActivity.candidateList(Collections.singletonList(dead),null).isEmpty())throw new AssertionError("No available servers");
  if(ProbeEngine.measure("127.0.0.1",443,200)!=-1||ProbeEngine.measure("::1",443,200)!=-1||ProbeEngine.measure("0.0.0.0",443,200)!=-1)throw new AssertionError("Private addresses");
  java.lang.reflect.Field field=ProbeEngine.class.getDeclaredField("CACHE");field.setAccessible(true);
  @SuppressWarnings("unchecked") Map<String,Future<InetAddress[]>> cache=(Map<String,Future<InetAddress[]>>)field.get(null);
  cache.put("slow.test",new FutureTask<InetAddress[]>(()->new InetAddress[0]));
  long start=System.nanoTime();if(ProbeEngine.measure("slow.test",443,30)!=-1||TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>500)throw new AssertionError("DNS deadline");
  ProbeEngine.refreshDns();if(!cache.isEmpty())throw new AssertionError("Fresh DNS on each check");
  MainActivity.Proxy unknown=new MainActivity.Proxy("unknown.example",443,"d");ArrayList<MainActivity.Proxy> scrambled=new ArrayList<>(Arrays.asList(dead,slow,unknown,fast));MainActivity.sortFree(scrambled);if(scrambled.get(0)!=fast||scrambled.get(1)!=slow||scrambled.get(2)!=unknown||scrambled.get(3)!=dead)throw new AssertionError("Ascending list with dead last");
  System.out.println("PASS: best / wanted / unavailable selection, no duplicate, DNS deadline and private-address refusal");
 }
}
