package com.newswave.live;

public final class ReviewFixesTest {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(NewsAlertPolicy.important("Wildfires and crashes cause delays"),"plural incident words retained");
        check(NewsAlertPolicy.important("Crash closes highway"),"crashes must not be filtered out");
        check(NewsAlertPolicy.important("Wildfire prompts evacuations"),"wildfire query must match filter");
        check(!NewsAlertPolicy.important("Breaking ground on new school"),"construction is not breaking news");
        check(!NewsAlertPolicy.important("Council approves new park"),"general news is not urgent");
        long now=1_800_000_000_000L,published=now-60_000L,last=now-30_000L;
        check(!NewsAlertPolicy.deliverable(published,now,last,90_000L,false),"cooldown holds story");
        check(NewsAlertPolicy.deliverable(published,now+90_000L,last,90_000L,false),
            "held story must remain deliverable after cooldown");
        check(!NewsAlertPolicy.deliverable(published,now+90_000L,last,90_000L,true),"seen story stays deduplicated");
        check(DrivePollPolicy.due(true,false,190_000L,100_000L,1000,5),
            "stationary timer must trigger without a new movement event");
        check(!DrivePollPolicy.due(true,true,190_000L,100_000L,1000,5),"no overlapping polls");
        check(!DrivePollPolicy.due(true,false,190_000L,100_000L,121_000,5),"stale GPS is not current");
        String xml="<AIRPORT_STATUS_INFORMATION><Update_Time>Sat Nov 21 13:58:35 2026 GMT</Update_Time>"
            +"<Delay_type><Ground_Stop_List><Program><ARPT>YOW</ARPT><Reason>low visibility</Reason>"
            +"<End_Time>9:30 am EST</End_Time></Program></Ground_Stop_List></Delay_type>"
            +"<Delay_type><Ground_Delay_List><Ground_Delay><ARPT>SFO</ARPT></Ground_Delay>"
            +"</Ground_Delay_List></Delay_type></AIRPORT_STATUS_INFORMATION>";
        long feedTime=java.time.Instant.parse("2026-11-21T13:58:35Z").toEpochMilli();
        java.util.List<FaaStatusParser.GroundStop> stops=FaaStatusParser.parse(xml,feedTime+60_000);
        check(stops.size()==1&&stops.get(0).airport.equals("YOW"),"only official ground stops selected");
        boolean rejected=false;
        try{FaaStatusParser.parse(xml,feedTime+3_600_001);}catch(Exception expected){rejected=true;}
        check(rejected,"stale FAA feed must not generate current alerts");
        rejected=false;
        try{FaaStatusParser.parse("<!DOCTYPE x>"+xml,feedTime);}catch(Exception expected){rejected=true;}
        check(rejected,"external entities/DOCTYPE must be rejected");
        RefreshBudget.begin(0);rejected=false;
        try{RefreshBudget.timeout(1000);}catch(java.io.IOException expected){rejected=true;}
        finally{RefreshBudget.clear();}
        check(rejected&&RefreshBudget.timeout(1234)==1234,"deadline expires and is thread-scoped");
        System.out.println("Review regression checks PASS: parked polling, freshness, cooldown, classification and FAA XML");
    }
}
