package com.newswave.live;

import java.util.Locale;

/** Verified DOT traffic websites; no fabricated incidents or unauthorized API scraping. */
final class TrafficSources {
    static final class Source {
        final String state,agency,mapUrl,reportsUrl;
        Source(String state,String agency,String mapUrl,String reportsUrl){
            this.state=state;this.agency=agency;this.mapUrl=mapUrl;this.reportsUrl=reportsUrl;
        }
    }
    private TrafficSources(){}
    static Source forState(String state){
        if(state==null)state="";
        switch(state.trim().toUpperCase(Locale.US)){
            case "AL":case "ALABAMA":
                return new Source("AL","ALGO Traffic · ALDOT",
                    "https://algotraffic.com/",
                    "https://algotraffic.com/traffic-reports");
            case "FL":case "FLORIDA":
                return new Source("FL","FL511 · FDOT",
                    "https://fl511.com/map?layers=incidents%2Cconstruction%2Cclosures%2CtrafficSpeeds",
                    "https://fl511.com/");
            case "GA":case "GEORGIA":
                return new Source("GA","511GA · GDOT",
                    "https://511ga.org/map","https://511ga.org/");
            case "TN":case "TENNESSEE":
                return new Source("TN","TDOT SmartWay",
                    "https://smartway.tn.gov/traffic",
                    "https://smartway.tn.gov/traffic/text");
            case "MS":case "MISSISSIPPI":
                return new Source("MS","MDOT Traffic",
                    "https://www.mdottraffic.com/?showMain=true",
                    "https://www.mdottraffic.com/?showMain=true");
            default:
                return new Source(state.isEmpty()?"":state,"Regional DOT source not configured",
                    "",""); 
        }
    }
}