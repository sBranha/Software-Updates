package com.newswire.vehicleporttester;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Six 10-second parked-only observation windows. Distinguishes a descriptive
 * traffic change from proof of Ford data access or vehicle control.
 */
public final class GuidedActionPlan {
    private GuidedActionPlan(){}
    public static final int WINDOW_SECONDS = 10;
    public static final String[] KEYS = {
        "baseline", "maps_navigation", "audio_media", "ford_sync_home",
        "climate_adjustment", "restore_and_idle"
    };
    public static final String[] LABELS = {
        "Baseline • do nothing", "Navigation / Maps", "Media / audio",
        "Factory SYNC home", "Climate temperature or fan", "Restore and settle"
    };
    public static final String[] PROMPTS = {
        "BASELINE — don't change any settings. Leave the Ford screen as it is.",
        "MAPS — while PARKED, open Maps on Android Auto or change the map view.",
        "AUDIO — while PARKED, change the media source or skip one music track.",
        "FORD HOME — while PARKED, switch to the factory Ford SYNC home screen.",
        "CLIMATE — while PARKED, adjust the temperature or fan one step.",
        "RESTORE — return settings to normal and leave the display alone."
    };
    public static int atSecond(int s){
        return Math.max(0,Math.min(KEYS.length-1,s/WINDOW_SECONDS));
    }
    public static String keyAt(int s){return KEYS[atSecond(s)];}
    public static String promptAt(int s){return PROMPTS[atSecond(s)];}

    public static JSONObject compare(JSONArray perSecond, JSONArray confirmed) {
        JSONObject result=new JSONObject();
        try {
            JSONArray phases=new JSONArray();
            int baselineBytes=0,baselineReads=0,baselineSmall=0,baselineLarge=0;
            int baselineOthers=0,baselineTls=0;
            for(int phase=0;phase<KEYS.length;phase++){
                int start=phase*WINDOW_SECONDS,end=start+WINDOW_SECONDS;
                int bytes=0,reads=0,small=0,large=0,other=0,tls=0;
                for(int j=start;j<end;j++){
                    JSONObject row=perSecond.optJSONObject(j);
                    if(row==null)continue;
                    bytes+=row.optInt("bytes",0);
                    reads+=row.optInt("reads",0);
                    small+=row.optInt("reads_39_bytes",0);
                    large+=row.optInt("reads_67_bytes",0);
                    other+=row.optInt("other_sizes",0);
                    tls+=row.optInt("tls_like_headers",0);
                }
                if(phase==0){
                    baselineBytes=bytes;baselineReads=reads;
                    baselineSmall=small;baselineLarge=large;
                    baselineOthers=other;baselineTls=tls;
                }
                boolean confirmedByUser=false;
                if(confirmed!=null){
                    for(int k=0;k<confirmed.length();k++){
                        JSONObject marker=confirmed.optJSONObject(k);
                        if(marker!=null&&KEYS[phase].equals(marker.optString("stage_key")))
                            confirmedByUser=true;
                    }
                }
                JSONObject row=new JSONObject()
                    .put("stage_key",KEYS[phase]).put("name",LABELS[phase])
                    .put("suggested_user_action",PROMPTS[phase])
                    .put("window_start_second",start).put("window_end_second_exclusive",end)
                    .put("bytes",bytes).put("read_events",reads)
                    .put("reads_39_bytes",small).put("reads_67_bytes",large)
                    .put("other_size_reads",other).put("tls_like_headers",tls)
                    .put("bytes_per_second",bytes/(double)WINDOW_SECONDS)
                    .put("action_confirmed_by_user",confirmedByUser)
                    .put("action_sensed_by_app",false)
                    .put("vehicle_control_access_verified",false);
                if(phase==0){
                    row.put("traffic_change_vs_baseline","baseline");
                }else{
                    row.put("bytes_change_vs_baseline",bytes-baselineBytes);
                    row.put("reads_change_vs_baseline",reads-baselineReads);
                    // Tolerate one or two startup/transport reads per window.
                    // A small timing jitter must not be called a Ford response.
                    boolean materiallyDifferentFrameMix=
                        Math.abs(small-baselineSmall)>Math.max(2,baselineSmall*0.2)
                        ||Math.abs(large-baselineLarge)>Math.max(2,baselineLarge*0.2)
                        ||Math.abs(other-baselineOthers)>Math.max(2,baselineOthers*0.2);
                    row.put("frame_size_distribution_changed",
                        materiallyDifferentFrameMix);
                    row.put("rate_change_percent",baselineBytes==0?JSONObject.NULL:
                        (bytes-baselineBytes)*100.0/baselineBytes);
                    row.put("traffic_change_vs_baseline",baselineBytes==0
                        ?(bytes>0?"traffic_began":"no_baseline_traffic")
                        :(Math.abs(bytes-baselineBytes)<=
                            Math.max(150,baselineBytes*0.15)
                            &&!materiallyDifferentFrameMix
                            ?"no_clear_change_in_length_or_rate"
                            :"observable_metadata_change_only"));
                }
                phases.put(row);
            }
            result.put("mode","guided_parked_60_second_comparison");
            result.put("window_seconds",WINDOW_SECONDS);
            result.put("baseline_stage_key","baseline");
            result.put("baseline_bytes",baselineBytes);
            result.put("baseline_reads",baselineReads);
            result.put("baseline_frame_sizes",new JSONObject()
                .put("39",baselineSmall).put("67",baselineLarge).put("other",baselineOthers));
            result.put("baseline_tls_like_headers",baselineTls);
            result.put("phases",phases);
            result.put("interpretation","Stage labels are prompts, not instrumented Ford events. "
                +"Even when a stage is user-confirmed, rate or packet-size changes cannot "
                +"identify their source or prove read/write access to a module. "
                +"Encrypted Android Auto traffic may contain ordinary connection keepalives.");
            result.put("commands_sent_to_vehicle",0);
        }catch(Exception e){try{result.put("error",e.getClass().getSimpleName());}catch(Exception ignored){}}
        return result;
    }
}
