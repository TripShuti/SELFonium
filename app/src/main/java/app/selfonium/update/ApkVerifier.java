package app.selfonium.update;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static app.selfonium.update.UpdatePolicy.*;

/** PackageManager verifies APK signing details; Android's installer remains the final authority. */
public final class ApkVerifier {
    private ApkVerifier(){}
    public static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    public static PackageInfo installed(Context context)throws PackageManager.NameNotFoundException{return context.getPackageManager().getPackageInfo(context.getPackageName(),flags());}
    private static int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private static Set<String> fingerprints(Signature[] signatures)throws Failure{
        Set<String> result=new HashSet<>();if(signatures==null)return result;
        try{for(Signature signature:signatures)result.add(UpdateDownload.hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return result;}catch(NoSuchAlgorithmException e){throw new Failure(Reason.SIGNATURE);}
    }
    private static Set<String> current(PackageInfo info)throws Failure{
        if(Build.VERSION.SDK_INT>=28)return info.signingInfo==null?Set.of():fingerprints(info.signingInfo.getApkContentsSigners());
        return fingerprints(info.signatures);
    }
    private static Set<String> history(PackageInfo info)throws Failure{
        if(Build.VERSION.SDK_INT>=28&&info.signingInfo!=null&&!info.signingInfo.hasMultipleSigners())return fingerprints(info.signingInfo.getSigningCertificateHistory());
        return current(info);
    }
    public static void verify(Context context,File file,ReleaseInfo release,UpdateHttp.Cancel cancel)throws IOException{
        cancel.check();if(!file.isFile()||file.length()!=release.size||!release.sha256.equals(UpdateDownload.digest(file,cancel)))throw new Failure(Reason.INTEGRITY);
        try{
            PackageInfo installed=installed(context),archive=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags());
            if(archive==null||archive.applicationInfo==null)throw new Failure(Reason.SIGNATURE);
            archive(archive.packageName,code(archive),archive.versionName,(archive.applicationInfo.flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0,archive.applicationInfo.minSdkVersion,Build.VERSION.SDK_INT,
                    release,code(installed),current(installed),history(installed),current(archive),history(archive),Build.VERSION.SDK_INT>=28);
            cancel.check();
        }catch(PackageManager.NameNotFoundException|RuntimeException e){throw new Failure(Reason.SIGNATURE);}
    }
}
