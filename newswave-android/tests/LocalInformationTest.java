package com.newswave.live;
public final class LocalInformationTest {
    private static void check(boolean okay,String label){if(!okay)throw new AssertionError(label);}
    public static void main(String[] args){
        check(WebLinkPolicy.allowed("https://www.waff.com/livestream/"),"publisher HTTPS");
        check(WebLinkPolicy.allowed("https://www.gadsdentimes.com/story?q=news&x=1"),"article query");
        for(String url:new String[]{null,"","http://example.com","javascript:alert(1)","intent://example.com","file:///etc/passwd","https://user:pass@example.com","https:///missing-host","https://example.com/a b"})
            check(!WebLinkPolicy.allowed(url),"reject unsupported link: "+url);
        check(LocationMath.miles(34.2676,-86.2089,34.2676,-86.2089)==0,"same location");
        double toGadsden=LocationMath.miles(34.2676,-86.2089,34.0143,-86.0066);
        check(toGadsden>20&&toGadsden<22,"nearby distance");
        check(Math.abs(toGadsden-LocationMath.miles(34.0143,-86.0066,34.2676,-86.2089))<.00001,"distance symmetry");
        check(LocationMath.miles(34.2676,-86.2089,25.7617,-80.1918)>600,"exclude Florida from Alabama");
        check(LocationMath.miles(0,179.9,0,-179.9)<15,"dateline");
        check(Double.isInfinite(LocationMath.miles(Double.NaN,0,0,0)),"missing GPS");
        check(Double.isInfinite(LocationMath.miles(91,0,0,0)),"invalid latitude");
        System.out.println("Local information: URL policy and location selection PASS");
    }
}
