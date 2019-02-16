package com.grouleff.pumpcontrol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MI301DongleProxy {
    public static final int SO_TIMEOUT = 100;
    private static String temperatureSensorPath;

    public static void main(String[] args) throws Exception {
        start(args);
    }

    private static List<RXTXLink> allLinks = Collections.synchronizedList(new ArrayList<RXTXLink>());

    private static void start(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: CMD /dev/ttyUSB0 /sys/bus/w1/devices/28-0000072ab93b/w1_slave /sys/bus/w1/devices/28-0000072a54b1/w1_slave");
        }

        RXTXLink link = new RXTXLink(args[0]);
        allLinks.add(link);
        //new Listener(port).listenForever();
        new CustomScheduler(args[1], args[2]).runForever();
    }

    public static RXTXLink getLink() {
        return allLinks.remove(0);
    }
    
    public static void releaseLink(RXTXLink link) {
        allLinks.add(link);
    }
}
