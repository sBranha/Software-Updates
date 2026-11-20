package com.newswave.live;
final class LocationMath {
    static double miles(double lat,double lon,double lat2,double lon2){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(lat2)||!Double.isFinite(lon2)
           ||Math.abs(lat)>90||Math.abs(lat2)>90||Math.abs(lon)>180||Math.abs(lon2)>180)return Double.POSITIVE_INFINITY;
        double a=Math.toRadians(lat2-lat),b=Math.toRadians(lon2-lon);
        double h=Math.sin(a/2)*Math.sin(a/2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(lat2))*Math.sin(b/2)*Math.sin(b/2);
        return 3958.8*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,h))));
    }
}
