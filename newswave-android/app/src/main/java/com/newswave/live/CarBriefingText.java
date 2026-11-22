package com.newswave.live;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Bounded, attributed speech. An unavailable feed is never an all-clear. */
final class CarBriefingText {
    static final String ROOT="newswire_audio",LOCAL="local_briefing",NATIONAL="national_briefing";
    static final int MAX_CHARS=3600;
    static final class Story {
        final String title,source;
        final long published;
        Story(String title,String source,long published){this.title=title;this.source=source;this.published=published;}
    }
    static final class Weather {
        final String temperature,condition,wind;
        final boolean conditionsAvailable,warningsAvailable;
        final long observationAt;
        final List<String> warnings;
        Weather(String temperature,String condition,String wind,boolean conditionsAvailable,
                boolean warningsAvailable,long observationAt,List<String> warnings) {
            this.temperature=temperature;this.condition=condition;this.wind=wind;
            this.conditionsAvailable=conditionsAvailable;this.warningsAvailable=warningsAvailable;
            this.observationAt=observationAt;this.warnings=warnings;
        }
    }
    static boolean playable(String id){return LOCAL.equals(id)||NATIONAL.equals(id);}
    static String title(String id){return NATIONAL.equals(id)?"National News":"Play My News";}
    static String search(String query){
        return query!=null&&query.toLowerCase(Locale.US).contains("national")?NATIONAL:LOCAL;
    }
    static String clock(long time){return new SimpleDateFormat("h:mm a",Locale.US).format(new Date(time));}
    static String clean(String text,int limit) {
        if(text==null)return "";
        String value=text.replaceAll("<[^>]*>"," ").replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        return value.length()<=limit?value:value.substring(0,limit).trim()+"…";
    }
    static String format(String id,String area,Weather weather,List<Story> stories,long checkedAt) {
        if(!playable(id))throw new IllegalArgumentException("Unknown briefing");
        boolean national=NATIONAL.equals(id);
        StringBuilder out=new StringBuilder(national?"NewsWire national news briefing. ":"NewsWire local briefing. ");
        out.append("Checked at ").append(clock(checkedAt)).append(". ");
        if(!national){
            String place=clean(area,120);
            if(!place.isEmpty())out.append("Around ").append(place).append(". ");
            if(weather!=null&&weather.conditionsAvailable){
                out.append(weather.observationAt>0?"Station weather observation: ":"Estimated conditions: ")
                    .append(clean(weather.temperature,35).replace("°F"," degrees Fahrenheit"))
                    .append(", ").append(clean(weather.condition,160)).append(". ");
                String wind=clean(weather.wind,45);
                if(!wind.isEmpty()&&!wind.equals("—"))out.append("Wind ").append(wind).append(". ");
                if(weather.observationAt>0)out.append("Observed at ").append(clock(weather.observationAt)).append(". ");
            }else out.append("Weather observations are unavailable. ");
            if(weather==null||!weather.warningsAvailable)
                out.append("Official NWS warning lookup unavailable. Check other emergency alerts. ");
            else if(weather.warnings==null||weather.warnings.isEmpty())
                out.append("NWS reported no active alerts at this check. ");
            else {
                out.append("NWS alerts at this check: ");
                for(int i=0;i<Math.min(3,weather.warnings.size());i++)
                    out.append(clean(weather.warnings.get(i),160)).append(". ");
                out.append("Follow official warning instructions. ");
            }
        }
        int count=0;
        if(stories!=null)for(Story story:stories){
            if(story==null||!NewsFreshness.isRecent(story.published,checkedAt))continue;
            String title=clean(story.title,250),source=clean(story.source,85);
            if(title.isEmpty()||source.isEmpty())continue;
            String suffix=" - "+source;
            if(title.endsWith(suffix))title=title.substring(0,title.length()-suffix.length());
            String item=(national?"National":"Local")+" headline from "+source+". "+title+
                ". Published "+NewsFreshness.age(story.published,checkedAt)+". ";
            if(out.length()+item.length()>MAX_CHARS-180)break;
            out.append(item);
            if(++count>=(national?5:3))break;
        }
        if(count==0)out.append("No recent ").append(national?"national":"local").append(" headlines are available. ");
        return out.append("That is your briefing. Conditions and news can change.").toString();
    }
}
