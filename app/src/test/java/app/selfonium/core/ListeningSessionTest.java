package app.selfonium.core;

public final class ListeningSessionTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Listening check "+checks);}
    public static void main(String[] args){
        ListeningSession session=new ListeningSession("server:track",0);session.advance(100_000,1_000_000,false);check(session.state().started()==0&&session.state().listened()==0);
        session.advance(110_000,1_010_000,true);session.advance(140_000,1_040_000,false);check(session.state().listened()==30_000);check(session.state().started()==1_010_000);
        session.advance(240_000,1_140_000,false);check(session.state().listened()==30_000);session.advance(250_000,1_150_000,true);session.advance(309_999,1_209_999,true);check(!session.eligible(180_000));session.advance(310_000,1_210_000,true);check(session.eligible(180_000));check(session.state().listened()==90_000);
        session.submitted();session.advance(500_000,1_400_000,true);check(!session.eligible(180_000));
        ListeningSession repeat=new ListeningSession("server:track",500_000);check(!repeat.state().session().equals(session.state().session()));check(repeat.state().listened()==0);
        check(ListeningSession.threshold(180_000)==90_000);check(ListeningSession.threshold(900_000)==240_000);check(ListeningSession.threshold(0)==Long.MAX_VALUE);check(ListeningSession.threshold(-1)==Long.MAX_VALUE);check(ListeningSession.threshold(Long.MAX_VALUE)==240_000);
        ListeningSession restored=new ListeningSession(new ListeningSession.State("saved","server:track",123_000,40_000,false),10_000_000);restored.advance(20_000_000,30_000_000,false);check(restored.state().listened()==40_000);restored.advance(20_001_000,30_001_000,true);restored.advance(20_011_000,30_011_000,false);check(restored.state().listened()==50_000);check(restored.state().started()==123_000);check(restored.state().session().equals("saved"));
        ListeningSession submitted=new ListeningSession(new ListeningSession.State("done","server:track",1,240_000,true),0);check(!submitted.eligible(400_000));
        ListeningSession unknown=new ListeningSession("unknown",0);unknown.advance(0,1,true);unknown.advance(1_000_000,1000000,true);check(!unknown.eligible(0));check(unknown.eligible(300_000));
        ListeningSession rollback=new ListeningSession("clock",100);rollback.advance(100,10,true);rollback.advance(90,9,true);rollback.advance(110,11,false);check(rollback.state().listened()==10);
        Source a=new Source("one","First",Source.Type.SUBSONIC,"https://music.example","user","secret");Source password=new Source("one","Renamed",Source.Type.SUBSONIC,a.baseUrl,"user","replacement");Source account=new Source("one","First",Source.Type.SUBSONIC,a.baseUrl,"other","secret");Source server=new Source("one","First",Source.Type.SUBSONIC,"https://other.example","user","secret");
        check(ListeningSession.account(a).equals(ListeningSession.account(password)));check(!ListeningSession.account(a).equals(ListeningSession.account(account)));check(!ListeningSession.account(a).equals(ListeningSession.account(server)));check(ListeningSession.account(a).matches("[a-f0-9]{64}"));
        check(ListeningSession.retryDelay(0)==30_000);check(ListeningSession.retryDelay(1)==60_000);check(ListeningSession.retryDelay(100)==900_000);
        System.out.println("PASS: "+checks+" listening checks (actual time, pauses, unknown duration, repeat, restart, identity and retry bounds)");
    }
}
