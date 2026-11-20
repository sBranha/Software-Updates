package com.newswave.live;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Local publisher shortcuts plus fresh Google News RSS links.
 * News headlines link to their original providers; no content is copied or republished. */
final class LocalNewspapers {
  static final class Paper {
    final String name, town, url;
    final double latitude, longitude;
    Paper(String n,String t,String u,double a,double o){
      name=n;town=t;url=u;latitude=a;longitude=o;
    }
  }
  static final class Headline {
    final String title, source, url;
    final long publishedMillis;
    Headline(String t,String s,String u){this(t,s,u,0);}
    Headline(String t,String s,String u,long published){
      title=t;source=s;url=u;publishedMillis=published;
    }
  }
  // Verified official publication sites. These are region examples, not an exhaustive US directory.
  private static final Paper[] PUBLISHERS = {
    new Paper("The Sand Mountain Reporter","Albertville, AL","https://www.sandmountainreporter.com/",34.2676,-86.2089),
    new Paper("Sand Mountain Leader","Albertville, AL","https://thesandmountainleader.com/",34.2676,-86.2089),
    new Paper("The Advertiser-Gleam","Guntersville, AL","https://www.advertisergleam.com/",34.3581,-86.2947),
    new Paper("The Arab Tribune","Arab, AL","https://www.thearabtribune.com/",34.3181,-86.4958),
    new Paper("The Gadsden Times","Gadsden, AL","https://www.gadsdentimes.com/",34.0143,-86.0066),
    new Paper("The Messenger","Gadsden, AL","https://www.gadsdenmessenger.com/",34.0143,-86.0066),
    new Paper("The Fort Payne Times-Journal","Fort Payne, AL","https://www.times-journal.com/",34.4443,-85.7197)
  };
  private static double miles(double a,double o,double b,double p){
    double dlat=Math.toRadians(b-a), dlon=Math.toRadians(p-o);
    double h=Math.pow(Math.sin(dlat/2),2)+Math.cos(Math.toRadians(a))*
        Math.cos(Math.toRadians(b))*Math.pow(Math.sin(dlon/2),2);
    return 3958.8*2*Math.asin(Math.min(1,Math.sqrt(h)));
  }
  static List<Paper> nearby(double lat,double lon){
    ArrayList<Paper> list=new ArrayList<>();
    if(Double.isNaN(lat)||Double.isNaN(lon))return list;
    for(Paper p:PUBLISHERS)if(miles(lat,lon,p.latitude,p.longitude)<=27)list.add(p);
    Collections.sort(list,Comparator.comparingDouble(p->miles(lat,lon,p.latitude,p.longitude)));
    return list;
  }
  static String localNewsSearch(String city,String region){
    return "https://news.google.com/search?q="+encode("\""+city+"\" \""+region+"\" news when:1d")+"&hl=en-US&gl=US&ceid=US%3Aen";
  }
  static String paperDiscovery(String city,String region){
    return "https://www.google.com/search?q="+encode("local newspapers "+city+" "+region+" official websites");
  }
  private static String encode(String s){
    try{return URLEncoder.encode(s,"UTF-8");}
    catch(Exception e){return s.replace(" ","+");}
  }
  /** Topic-focused, attributed RSS headlines. Never infer that a source says
   * "breaking" unless its actual title contains that term. Score stories are
   * news, not independently verified live game scores. */
  static List<Headline> topicHeadlines(String city,String region,String topic) throws Exception {
    if(city==null||city.trim().isEmpty()||region==null||region.trim().isEmpty())
      return Collections.emptyList();
    String query;
    switch(topic){
      case "breaking":
        query="("+city+" "+region+") (breaking news OR emergency OR crash OR wildfire)";
        break;
      case "schools":
        query="("+city+" "+region+") (school closed OR school closure OR school delay)";
        break;
      case "events":
        query="("+city+" "+region+") (upcoming festival OR community event OR local concert OR fair OR arts event)";
        break;
      case "basketball":
        query="("+city+" "+region+") (high school basketball OR varsity basketball scores)";
        break;
      case "baseball":
        query="("+city+" "+region+") (high school baseball OR high school softball)";
        break;
      case "soccer":
        query="("+city+" "+region+") (high school soccer OR varsity soccer)";
        break;
      case "football":
        query="("+city+" "+region+") (high school football score OR final OR Friday night football)";
        break;
      default:
        query="\""+city+"\" \""+region+"\" (news OR local)";
    }
    return rssSearch(query,15);
  }
  private static List<Headline> rssSearch(String phrase,int maxItems) throws Exception {
    String address="https://news.google.com/rss/search?q="+encode(phrase+" when:1d")
        +"&hl=en-US&gl=US&ceid=US%3Aen";
    return rssFeed(address,maxItems);
  }
  /** National edition of Google News RSS. Provider names remain attached to stories. */
  static List<Headline> nationalHeadlines() throws Exception {
    return rssFeed("https://news.google.com/rss?hl=en-US&gl=US&ceid=US%3Aen",20);
  }
  private static List<Headline> rssFeed(String address,int maxItems)throws Exception {
    HttpURLConnection connection=null;
    ArrayList<Headline> data=new ArrayList<>();
    try{
      connection=(HttpURLConnection)new URL(address).openConnection();
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(10000);
      connection.setRequestProperty("Accept","application/rss+xml, application/xml");
      connection.setRequestProperty("User-Agent","NewsWire Android/0.5.2");
      if(connection.getResponseCode()!=200)
        throw new IllegalStateException("RSS HTTP "+connection.getResponseCode());
      try(InputStream stream=connection.getInputStream()){
        XmlPullParser xml=Xml.newPullParser();
        xml.setInput(stream,"UTF-8");
        boolean inItem=false;
        String title="",link="",source="",published="";
        int event=xml.getEventType();
        while(event!=XmlPullParser.END_DOCUMENT){
          if(event==XmlPullParser.START_TAG){
            String name=xml.getName();
            if("item".equals(name)){inItem=true;title="";link="";source="";published="";}
            else if(inItem&&("title".equals(name)||"link".equals(name)||"source".equals(name)||"pubDate".equals(name))){
              String value=xml.nextText();
              if("title".equals(name))title=value;
              else if("link".equals(name))link=value;
              else if("pubDate".equals(name))published=value;
              else source=value;
            }
          }else if(event==XmlPullParser.END_TAG&&"item".equals(xml.getName())){
            if(!title.isEmpty()&&link.startsWith("https://")){
              long timestamp=NewsFreshness.parse(published);
              data.add(new Headline(title,source.isEmpty()?"News provider":source,link,timestamp));
            }
            inItem=false;
          }
          event=xml.next();
        }
      }
    }finally{if(connection!=null)connection.disconnect();}
    List<Headline> recent=fresh(data);
    return new ArrayList<>(recent.subList(0,Math.min(maxItems,recent.size())));
  }
  /** Local headlines use the same dated, age-filtered parser as all other feeds. */
  static List<Headline> headlines(String city,String region) throws Exception {
    return rssSearch("\""+city+"\" \""+region+"\" (news OR newspaper OR local)",12);
  }
  static List<Headline> fresh(List<Headline> input) {
    ArrayList<Headline> result=new ArrayList<>();
    long now=System.currentTimeMillis();
    for(Headline h:input)if(NewsFreshness.isRecent(h.publishedMillis,now))result.add(h);
    result.sort((a,b)->Long.compare(b.publishedMillis,a.publishedMillis));
    return result;
  }
  static String age(Headline h){return NewsFreshness.age(h.publishedMillis,System.currentTimeMillis());}
}
