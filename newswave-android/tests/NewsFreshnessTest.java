package com.newswave.live;
public class NewsFreshnessTest {
 public static void main(String[] args){
  long now=NewsFreshness.parse("Fri, 20 Nov 2026 14:00:00 GMT");
  check(now>0,"RFC date");
  check(NewsFreshness.parse("Fri, 20 Nov 2026 09:00:00 -0500")==now,"timezone");
  check(NewsFreshness.isRecent(now-1000,now),"current");
  check(NewsFreshness.isRecent(now-NewsFreshness.DAY,now),"24h boundary");
  check(!NewsFreshness.isRecent(now-NewsFreshness.DAY-1,now),"over 24h");
  check(!NewsFreshness.isRecent(now-90*NewsFreshness.DAY,now),"three months");
  check(!NewsFreshness.isRecent(0,now),"missing date");
  check(!NewsFreshness.isRecent(now+1,now),"future date");
  check(NewsFreshness.parse("garbage")==0,"malformed date");
  System.out.println("News freshness behavior PASS");
 }
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
