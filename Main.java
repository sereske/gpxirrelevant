
package ru.sereske.gpsirrelevant;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Main {

    private static final String FILE = "D:\\20190430.gpx";
    //private static final String OUTPUT_FILE = "D:\\20190430_fixed4.gpx";

    public static void main(String[] args) {
        parseStax();
    }

    public static void parseDom() {
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(FILE);
            Node gpx = document.getDocumentElement();
            System.out.println(gpx.getNodeName());
            System.out.println("----------------------");
            Node trk = gpx.getChildNodes().item(1);
            System.out.println(trk.getNodeName());
            System.out.println("----------------------");
            NodeList list = trk.getChildNodes();
            for (int i = 2; i < list.getLength(); i++) {
                Node trkpt = list.item(i);
                if (trkpt.getNodeType() != Node.TEXT_NODE) {
                    //System.out.println(node.getNodeName());
                    NodeList children = trkpt.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);
                        System.out.println(child.getNodeName() + " " + child.getNodeValue());
                    }
                    System.out.println("***********************");
                }
            }

        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void parseStax() {
        try {
            XMLStreamReader xmlr = XMLInputFactory.newInstance().createXMLStreamReader(FILE, new FileInputStream(FILE));

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            DateTimeFormatter timeFormatterMs = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            //DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss.SSS");

            String name = "";
            String number = "";

            TrackPoint point = null;
            double lat = 0.0;
            double lon = 0.0;
            double ele = 0.0;
            LocalDateTime ldt = LocalDateTime.MIN;
            TrackPoint prev = null;
            double pace = 0;
            double velocity = 0;

            List<TrackPoint> points = new ArrayList<>();

            while (xmlr.hasNext()) {
                xmlr.next();
                if (xmlr.isStartElement() && xmlr.getLocalName().contentEquals("trkpt")) {
                    prev = point;
                    lat = Double.parseDouble(xmlr.getAttributeValue(0).toString());
                    lon = Double.parseDouble(xmlr.getAttributeValue(1).toString());
                    point = null;
                    //System.out.println(xmlr.getLocalName());
                } else if (xmlr.isEndElement() && xmlr.getLocalName().contentEquals("trkpt")) {
                    point = new TrackPoint(lat, lon, ele, ldt);
                    //System.out.println(point + " " + prev);
                    //System.out.println("/" + xmlr.getLocalName());
                    if (prev == null) {
                        points.add(point);
                    }
                    if (point != null && prev != null) {

                        pace = ChronoUnit.MILLIS.between(prev.ldt, point.ldt) / 1000 * 60 /
                                (distance(point.lat, prev.lat, point.lon, prev.lon, point.ele, prev.ele) * 1000);
                        double distance = distance(point.lat, prev.lat, point.lon, prev.lon, point.ele, prev.ele);
                        double seconds = ChronoUnit.MILLIS.between(prev.ldt, point.ldt) / 1000;
                        //velocity = distance(point.lat, prev.lat, point.lon, prev.lon, point.ele, prev.ele) * 1000 /
                        //		(ChronoUnit.MILLIS.between(prev.ldt, point.ldt) / 1000);

                        velocity = distance * 60 / (seconds * 1000);
                        pace = seconds * 1000 / (distance * 60);

                        if (pace < 5) {
                            System.out.println("distance: " + distance +
                                    " meters, time: " + seconds + " seconds, " +
                                    " pace: " + pace + " min/km");

                        } else {
                            points.add(point);
                        }
                    }

                } else if (xmlr.hasText() && xmlr.getText().trim().length() > 0) {
                    //System.out.println("   " + xmlr.getText());
                    //ele = Double.parseDouble(xmlr.getText());
                } else if (xmlr.isStartElement() && xmlr.getLocalName().contentEquals("ele")) {
                    xmlr.next();
                    ele = Double.parseDouble(xmlr.getText());
                } else if (xmlr.isStartElement() && xmlr.getLocalName().contentEquals("time")) {
                    xmlr.next();
                    String str = xmlr.getText();
                    String[] dateAndTime = str.split("T");
                    String strDate = dateAndTime[0];
                    String strTime = dateAndTime[1].substring(0, dateAndTime[1].length() - 1);
                    LocalDate date = LocalDate.parse(strDate, dateFormatter);
                    LocalTime time = LocalTime.MIN;
                    if (strTime.matches("^[0-9]{2}:[0-9]{2}:[0-9]{2}$")) {
                        time = LocalTime.parse(strTime, timeFormatter);
                    } else if (strTime.matches("^[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}$")) {
                        time = LocalTime.parse(strTime, timeFormatterMs);
                    }
                    ldt = LocalDateTime.of(date, time);
                } else if (xmlr.isStartElement() && xmlr.getLocalName().contentEquals("number")) {
                    xmlr.next();
                    number = xmlr.getText();
                } else if (xmlr.isStartElement() && xmlr.getLocalName().contentEquals("name")) {
                    xmlr.next();
                    name = xmlr.getText();
                }
            }
            try {
                XMLOutputFactory output = XMLOutputFactory.newInstance();
                XMLStreamWriter writer = output.createXMLStreamWriter(new FileWriter(FILE.substring(0, FILE.length() - 4) + "_fixed.gpx"));

                writer.writeStartDocument("1.0");
                writer.writeStartElement("trk");

                writer.writeStartElement("name");
                writer.writeCData(name);
                writer.writeEndElement();

                writer.writeStartElement("number");
                writer.writeCharacters(number);
                writer.writeEndElement();

                for (TrackPoint pt : points) {
                    writer.writeStartElement("trkpt");
                    writer.writeAttribute("lat", String.valueOf(pt.lat));
                    writer.writeAttribute("lon", String.valueOf(pt.lon));

                    writer.writeStartElement("ele");
                    writer.writeCharacters(String.valueOf(pt.ele));
                    writer.writeEndElement();

                    writer.writeStartElement("time");
                    writer.writeCharacters(pt.ldt.format(dateFormatter) + "T" +
                            pt.ldt.format(timeFormatter) + "Z");
                    writer.writeEndElement();

                    writer.writeEndElement();
                }
                writer.writeEndElement();
                writer.writeEndDocument();
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        }

    }

    static class TrackPoint {
        double lat;
        double lon;
        double ele;
        LocalDateTime ldt;

        TrackPoint(double lat, double lon, double ele, LocalDateTime ldt) {
            this.lat = lat;
            this.lon = lon;
            this.ele = ele;
            this.ldt = ldt;
        }

        @Override
        public String toString() {
            return "TrackPoint [lat=" + lat + ", lon=" + lon + ", ele=" + ele + ", ldt=" + ldt + "]";
        }
    }

    public static double distance(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }
}
