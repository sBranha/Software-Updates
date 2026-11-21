package com.newswave.live;

import java.io.StringReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Reads only active ground-stop programs from the official FAA NAS XML feed. */
final class FaaStatusParser {
    static final class GroundStop {
        final String airport,reason,end,key;
        GroundStop(String airport,String reason,String end) {
            this.airport=airport;this.reason=reason;this.end=end;
            key=airport+"|"+reason+"|"+end;
        }
        String headline(){return "FAA ground stop: "+airport+(reason.isEmpty()?"":" · "+reason);}
    }
    static List<GroundStop> parse(String xml,long now) throws Exception {
        if(xml==null||xml.length()>1024*1024||xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY"))
            throw new IllegalArgumentException("Invalid FAA XML");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder=factory.newDocumentBuilder();
        builder.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
        Document doc=builder.parse(new InputSource(new StringReader(xml)));
        if(!"AIRPORT_STATUS_INFORMATION".equals(doc.getDocumentElement().getTagName()))
            throw new IllegalArgumentException("Unexpected FAA document");
        String updated=text(doc.getDocumentElement(),"Update_Time");
        long time=ZonedDateTime.parse(updated,
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy z",Locale.US))
            .toInstant().toEpochMilli();
        if(time>now+300_000L||now-time>60L*60*1000)
            throw new IllegalArgumentException("FAA status timestamp unavailable or stale");
        List<GroundStop> stops=new ArrayList<>();
        NodeList lists=doc.getElementsByTagName("Ground_Stop_List");
        for(int i=0;i<lists.getLength();i++){
            NodeList programs=((Element)lists.item(i)).getElementsByTagName("Program");
            for(int j=0;j<programs.getLength();j++){
                Element program=(Element)programs.item(j);
                String airport=text(program,"ARPT");
                if(airport.isEmpty())continue;
                stops.add(new GroundStop(airport,text(program,"Reason"),text(program,"End_Time")));
            }
        }
        return stops;
    }
    private static String text(Element parent,String tag) {
        NodeList nodes=parent.getElementsByTagName(tag);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
}
