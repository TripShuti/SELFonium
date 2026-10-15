package app.selfonium.net;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Cancel the production OkHttp call while it waits for response headers. */
public final class HttpCancelTest {
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        server.createContext("/slow",exchange->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);exchange.sendResponseHeaders(200,-1);}catch(Exception ignored){}finally{exchange.close();}});server.start();
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/slow";Http.Cancel cancel=new Http.Cancel();
        try{
            Future<Boolean> result=worker.submit(()->{try(Http.Response response=Http.open("GET",url,Map.of(),null,cancel)){return false;}catch(IOException expected){return cancel.cancelled();}});
            if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("Request not received");cancel.cancel();if(!result.get(5,TimeUnit.SECONDS))throw new AssertionError("Call cancellation failed");
            Http.Cancel before=new Http.Cancel();before.cancel();try(Http.Response response=Http.open("GET",url,Map.of(),null,before)){throw new AssertionError("Pre-cancelled request executed");}catch(IOException expected){}
            System.out.println("PASS: HTTP cancellation before request and during response-header wait");
        }finally{release.countDown();server.stop(0);worker.shutdownNow();}
    }
}
