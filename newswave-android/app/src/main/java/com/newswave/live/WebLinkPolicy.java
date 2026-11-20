package com.newswave.live;
/** Shared link validation, usable in local JVM tests without an Android device. */
final class WebLinkPolicy {
    static boolean allowed(String url){
        if(url==null)return false;
        try{
            java.net.URI u=new java.net.URI(url);
            return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&!u.getHost().isEmpty()&&u.getUserInfo()==null;
        }catch(java.net.URISyntaxException bad){return false;}
    }
}
