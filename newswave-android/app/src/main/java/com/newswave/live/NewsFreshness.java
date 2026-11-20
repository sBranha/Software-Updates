package com.newswave.live;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
final class NewsFreshness {
  static final long DAY=24L*60*60*1000;
  static long parse(String value){
    try{return ZonedDateTime.parse(value.trim(),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}
    catch(Exception e){return 0;}
  }
  static boolean isRecent(long published,long now){
    return published>0 && published<=now && now-published<=DAY;
  }
  static String age(long published,long now){
    long minutes=Math.max(0,(now-published)/60000);
    return minutes<1?"Just published":minutes<60?minutes+" min ago":(minutes/60)+" hr ago";
  }
}
