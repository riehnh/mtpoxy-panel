package ru.mtproxy.picker;
import java.net.HttpURLConnection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** A cancelled run can never publish into a newer screen or run. */
final class CancellableOperation {
 private final AtomicBoolean cancelled=new AtomicBoolean();
 private final ConcurrentLinkedQueue<Future<?>> futures=new ConcurrentLinkedQueue<>();
 private final ConcurrentLinkedQueue<HttpURLConnection> connections=new ConcurrentLinkedQueue<>();
 boolean isCancelled(){return cancelled.get();}
 void track(Future<?> future){futures.add(future);if(isCancelled())future.cancel(true);}
 void track(HttpURLConnection connection){connections.add(connection);if(isCancelled())disconnectLater();}
 void cancel(){if(!cancelled.compareAndSet(false,true))return;for(Future<?> future:futures)future.cancel(true);disconnectLater();}
 private void disconnectLater(){Thread closer=new Thread(()->{HttpURLConnection c;while((c=connections.poll())!=null)try{c.disconnect();}catch(Exception ignored){}},"proxy-cancel");closer.setDaemon(true);closer.start();}
}
