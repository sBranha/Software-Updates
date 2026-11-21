package com.newswave.live;

import java.util.Locale;

/** Headline classification is a filter, never independent verification of news. */
final class NewsAlertPolicy {
    static boolean important(String title) {
        if(title==null)return false;
        String text=title.toLowerCase(Locale.US).replaceAll("\\bbreaking\\s+ground\\b","");
        return text.matches("(?s).*\\b(breaking|emergency|evacuat\\w*|urgent|tornado warning|"
            +"shooting|missing child|crash(?:es)?|collisions?|wildfires?|ground[- ]stops?|groundstop)\\b.*");
    }
    static boolean qualifies(String topic,String title) {
        if("breaking".equals(topic)||"national".equals(topic))return important(title);
        if("schools".equals(topic))return title!=null&&title.toLowerCase(Locale.US)
            .matches("(?s).*(school.*(clos\\w*|delay\\w*)|(clos\\w*|delay\\w*).*school).*");
        return true;
    }
    static boolean deliverable(long published,long now,long lastDelivered,long cooldown,
                               boolean seen) {
        return !seen&&published>0&&published<=now+300_000L
            &&now-published<=12L*60*60*1000
            &&(lastDelivered==0||now-lastDelivered>=cooldown);
    }
}
