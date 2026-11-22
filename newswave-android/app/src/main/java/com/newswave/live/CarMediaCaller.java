package com.newswave.live;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Process;
import androidx.media.MediaSessionManager;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Authenticate browsers before exposing the media session or saved-area metadata. */
final class CarMediaCaller {
    private static final String AUTO="com.google.android.projection.gearhead";
    // Production certificate SHA-256 hashes from Google's Apache-2.0 UAMP sample:
    // https://github.com/android/uamp/blob/main/common/src/main/res/xml/allowed_media_browser_callers.xml
    // Debug certificates are intentionally omitted. Signing history supports key rotation.
    private static final String[] AUTO_CERTIFICATES={
        "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
        "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295"
    };
    @SuppressWarnings("deprecation")
    static boolean allowed(Context context,String name,int uid) {
        if(name==null)return false;
        PackageManager pm=context.getPackageManager();
        String[] names=pm.getPackagesForUid(uid);
        if(names==null||!Arrays.asList(names).contains(name))return false;
        if(uid==Process.myUid()||uid==Process.SYSTEM_UID)return true;
        if(MediaSessionManager.getSessionManager(context).isTrustedForMediaControl(
                new MediaSessionManager.RemoteUserInfo(name,-1,uid)))return true;
        if(!AUTO.equals(name))return false;
        try {
            Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28){
                PackageInfo info=pm.getPackageInfo(name,PackageManager.GET_SIGNING_CERTIFICATES);
                if(info.signingInfo==null)return false;
                signatures=info.signingInfo.hasMultipleSigners()?info.signingInfo.getApkContentsSigners():
                    info.signingInfo.getSigningCertificateHistory();
            }else signatures=pm.getPackageInfo(name,PackageManager.GET_SIGNATURES).signatures;
            if(signatures==null||signatures.length==0)return false;
            // All current signers must be approved; any historical approved signer suffices
            // for the usual single-signer rotation chain verified by Android.
            boolean multiple=Build.VERSION.SDK_INT>=28&&
                pm.getPackageInfo(name,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.hasMultipleSigners();
            boolean found=false;
            for(Signature signature:signatures){
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
                StringBuilder hex=new StringBuilder();
                for(byte value:hash)hex.append(String.format(Locale.US,"%02x",value&255));
                boolean match=Arrays.asList(AUTO_CERTIFICATES).contains(hex.toString());
                if(multiple&&!match)return false;
                found|=match;
            }
            return found;
        }catch(Exception unavailable){return false;}
    }
}
