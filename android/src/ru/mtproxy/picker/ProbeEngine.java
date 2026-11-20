package ru.mtproxy.picker;
import java.net.*;
import java.util.concurrent.*;
/** Bounded TCP reachability check. DNS and connect share one deadline. */
final class ProbeEngine {
 private static final ThreadPoolExecutor DNS=new ThreadPoolExecutor(8,8,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(128),r->{Thread t=new Thread(r,"proxy-dns");t.setDaemon(true);return t;});
 private static final ConcurrentHashMap<String,Future<InetAddress[]>> CACHE=new ConcurrentHashMap<>();
 static void refreshDns(){CACHE.clear();}
 static long measure(String host,int port,int timeout){
  long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
  try{
   if(host==null||port<1||port>65535||timeout<1)return -1;
   InetAddress[] addresses;
   if(host.matches("[0-9.]+")||host.contains(":"))addresses=new InetAddress[]{InetAddress.getByName(host)};
   else{
    Future<InetAddress[]> future=CACHE.get(host);
    if(future==null){if(CACHE.size()>512)CACHE.clear();FutureTask<InetAddress[]> task=new FutureTask<>(()->InetAddress.getAllByName(host));future=CACHE.putIfAbsent(host,task);if(future==null){future=task;try{DNS.execute(task);}catch(RejectedExecutionException e){CACHE.remove(host,task);return -1;}}}
    addresses=future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
   }
   for(InetAddress a:addresses)if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return -1;
   for(int i=0;i<addresses.length&&i<2;i++){
    long remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());if(remaining<1)return -1;
    long started=System.nanoTime();try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(addresses[i],port),(int)Math.min(Integer.MAX_VALUE,remaining));return Math.max(1,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}catch(java.io.IOException ignored){}
   }
  }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}
  return -1;
 }
}
