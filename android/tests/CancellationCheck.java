package ru.mtproxy.picker;
import java.util.concurrent.*;
import java.net.*;
public final class CancellationCheck {
 public static void main(String[] args)throws Exception{
  CancellableOperation op=new CancellableOperation();CountDownLatch started=new CountDownLatch(1),interrupted=new CountDownLatch(1),closed=new CountDownLatch(1);
  FutureTask<Void> future=new FutureTask<>(()->{started.countDown();try{Thread.sleep(10000);}catch(InterruptedException e){interrupted.countDown();}return null;});op.track(future);Thread worker=new Thread(future);worker.start();if(!started.await(2,TimeUnit.SECONDS))throw new AssertionError("worker");
  op.track(new HttpURLConnection(new URL("http://127.0.0.1")){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed.countDown();}});
  op.cancel();op.cancel();if(!op.isCancelled()||!future.isCancelled()||!interrupted.await(2,TimeUnit.SECONDS)||!closed.await(2,TimeUnit.SECONDS))throw new AssertionError("Cancellation / interrupt / disconnect");
  FutureTask<Void> late=new FutureTask<>(()->null);op.track(late);if(!late.isCancelled())throw new AssertionError("Late task registration");
  CountDownLatch lateClosed=new CountDownLatch(1);op.track(new HttpURLConnection(new URL("http://127.0.0.1")){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){lateClosed.countDown();}});if(!lateClosed.await(2,TimeUnit.SECONDS))throw new AssertionError("Late connection");
  if(new CancellableOperation().isCancelled())throw new AssertionError("New run independent");System.out.println("PASS: cancellation, worker interrupt, connection close, repeated stop and late registration");
 }
}
