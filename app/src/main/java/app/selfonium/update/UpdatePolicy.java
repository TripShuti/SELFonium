package app.selfonium.update;

import java.io.IOException;
import java.util.Set;

/** Platform-independent policy; the installed signing identity is the trust anchor. */
public final class UpdatePolicy {
    public static final long DAY=86_400_000L,MANUAL_INTERVAL=60_000L,MAX_APK=200L*1024*1024,RESERVE=8L*1024*1024;
    public static final String PACKAGE="app.selfonium";
    private UpdatePolicy(){}
    public enum Reason {NETWORK,HTTP,NOT_FOUND,RATE_LIMIT,INVALID_RELEASE,NO_ASSET,INTEGRITY,PACKAGE,VERSION,SIGNATURE,SPACE,STORAGE,CANCELED,THROTTLED,INSTALL}
    public static final class Failure extends IOException {
        public final Reason reason;public final int status;public final long retryAt;
        public Failure(Reason reason){this(reason,0,0);}
        public Failure(Reason reason,int status,long retryAt){super(reason.name());this.reason=reason;this.status=status;this.retryAt=retryAt;}
    }
    public static boolean due(long now,long attempted,long retryAt,boolean automatic){
        if(now<retryAt)return false;
        if(attempted==0)return true;
        if(now<attempted)return !automatic;
        return now-attempted>=(automatic?DAY:MANUAL_INTERVAL);
    }
    public static boolean newer(long offered,long installed){return offered>0&&offered>installed;}
    public static boolean compatible(Set<String> installed,Set<String> installedHistory,Set<String> offered,Set<String> offeredHistory,boolean rotation){
        if(installed.isEmpty()||offered.isEmpty())return false;
        if(installed.equals(offered))return true;
        if(!rotation||installed.size()!=1||offered.size()!=1)return false;
        String old=installed.iterator().next(),next=offered.iterator().next();
        return offeredHistory.contains(old)&&offeredHistory.contains(next)&&!installedHistory.contains(next);
    }
    public static void archive(String name,long code,String version,boolean debug,int minSdk,int sdk,
            ReleaseInfo release,long installed,Set<String> current,Set<String> currentHistory,Set<String> signers,Set<String> history,boolean rotation)throws Failure{
        if(!PACKAGE.equals(name))throw new Failure(Reason.PACKAGE);
        if(code!=release.code||!newer(code,installed)||!release.version.equals(version)||debug||minSdk>sdk)throw new Failure(Reason.VERSION);
        if(!signers.contains(release.signer)||!compatible(current,currentHistory,signers,history,rotation))throw new Failure(Reason.SIGNATURE);
    }
}
