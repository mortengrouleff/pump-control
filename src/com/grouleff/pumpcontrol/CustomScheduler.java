package com.grouleff.pumpcontrol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mgr on 12/2/15.
 */
public class CustomScheduler {

    private RXTXLink dongle;

    private final String circSensorPath, returnSensorPath;

    public CustomScheduler(String circSensorPath, String returnSensorPath) {
        this.circSensorPath = circSensorPath;
        this.returnSensorPath = returnSensorPath;
    }

    private double readTemperature(String path) {
        File f = new File(path);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            byte[] buffer = new byte[400];
            int len = fis.read(buffer);
            String s = new String(buffer, 0, len);
            Pattern p = Pattern.compile(".*t=(\\d+).*", Pattern.MULTILINE | Pattern.DOTALL);
            Matcher m = p.matcher(s);
            if (m.matches()) {
                String t = m.group(1);
                return Double.parseDouble(t) / 1000d;
            } else {
                System.out.println("Temperature parsing problem: " + s);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return Double.NaN;
    }

    public void run() {
        try {
            dongle = MI301DongleProxy.getLink(); // throws if it fails...
            resetDongle();
            setIrBroadcastDongle();
//            sendStopCommmand();
//            sendStartCommmand();

            long lastUse = System.currentTimeMillis();
            while (true) {
                doScheduling();
                long now = System.currentTimeMillis();
                long spent = now - lastUse;
                if (spent > (3600 * 1000L)) {
                    sendStopCommmand();
                    Thread.sleep(1000);
                    System.exit(0); // Restart once in a while, just in case..
                }
            }
        } catch (java.net.SocketException e) {
            //fine.
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            // fine.
        } finally {
            if (dongle != null) {
                MI301DongleProxy.releaseLink(dongle);
            }
        }
    }

    private boolean isRunning = false;
    private long lastStop = System.currentTimeMillis();
    private long lastSleepAt = System.currentTimeMillis();

    private void doScheduling() throws InterruptedException, IOException {
        double circTemp = readTemperature(circSensorPath);
        double returnTemp = readTemperature(returnSensorPath);
        boolean inPauseInterval = getInPauseInterval();
        long sleepInterval = 10*1000;
        long hasRunFor = (System.currentTimeMillis() - lastStop)  / 1000;
        final boolean wasActive = isRunning;
        if (returnTemp > 40 && returnTemp > circTemp + 5 && !isRunning && hasRunFor > 60) {
            // Valve failure in heater. Start pump to trigger valve reset...
            isRunning = true;
        } else if (circTemp < 22) {
            // Cold water backwards through pump, start pump, pause or no pause.
            isRunning = true;
        } else if ((inPauseInterval && hasRunFor > 30)
                   || circTemp > 44
                   || (isRunning && hasRunFor > 180)) {
            isRunning = false;
        } else if (!inPauseInterval && circTemp < 39) {
            isRunning = true;
        }

        if (isRunning != wasActive) {
            lastStop = System.currentTimeMillis();
            hasRunFor = 0;
        }

        if (isRunning) {
            sendStartCommmand();
        } else {
            sendStopCommmand();
        }

        if (isRunning) {
            System.out.println("pauseMode=" + inPauseInterval
                               + " circTemp=" + circTemp
                               + " returnTemp=" + returnTemp
                               + " runTime=" + hasRunFor);
        } else {
            System.out.println("pauseMode=" + inPauseInterval
                               + " circTemp=" + circTemp
                               + " returnTemp=" + returnTemp
                               + " stoppedTime=" + hasRunFor);
        }

        lastSleepAt += sleepInterval;
        long sleepTime = lastSleepAt - System.currentTimeMillis();
        Thread.sleep(sleepTime);
    }

    private boolean getInPauseInterval() {
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (dow == Calendar.SATURDAY) {
            return hour < 9 || hour >= 23;
        } else if (dow == Calendar.FRIDAY) {
            return hour < 6 || hour >= 23;
        } else if (dow == Calendar.SUNDAY) {
            return hour < 9 || hour >= 22;
        }
        return hour < 6 || hour >= 22;
    }


    private void sendPumpByteCommmand(int c) throws IOException {
        Packet p = rslpBegin(2, 1); // Set
        p.addByte((byte)3);
        p.addByte((byte)(128 +1));
        p.addByte((byte)c);
        p.addByte((byte)0);
        p.addByte((byte)0);
        p.updateLengthAndCheckSum();
        p.writeTo(dongle.getOutputStream(), false);
        Packet.readFrom(dongle.getInputStream(), 500); // Wait for ack.
    }

    private void sendMinimumCommmand() throws IOException {
        sendPumpByteCommmand(25);
    }

    private void sendConstantCurveCommmand() throws IOException {
        sendPumpByteCommmand(22);
    }

    private void sendStopCommmand() throws IOException {
        sendPumpByteCommmand(5); // /Operation/CMD_STOP
    }

    private void sendStartCommmand() throws IOException {
        sendPumpByteCommmand(6); // /Operation/CMD_START
    }

    private void resetDongle() throws IOException {
        Packet reset = rslpBegin(2, 0);
        reset.addByte((byte)3);
        reset.addByte((byte)7); // reset dongle.
        reset.addByte((byte)0);
        reset.addByte((byte)0);
        reset.updateLengthAndCheckSum();
        reset.writeTo(dongle.getOutputStream(), false);
        Packet.readFrom(dongle.getInputStream(), 500); // Wait for ack.
    }

    private void setIrBroadcastDongle() throws IOException {
        Packet reset = rslpBegin(4, 0);
        reset.addByte((byte)3);
        reset.addByte((byte)16); // set address
        reset.addByte((byte)1);  // IR
        reset.addByte((byte)(255)); // Broadcast
        reset.addByte((byte)0);
        reset.addByte((byte)0);
        reset.updateLengthAndCheckSum();
        reset.writeTo(dongle.getOutputStream(), false);
        Packet.readFrom(dongle.getInputStream(), 500); // Wait for ack.
    }

    private Packet rslpBegin(int payloadLength, int target) {
        Packet rslp = new Packet();
        rslp.addByte((byte)0x27);
        rslp.addByte((byte)(payloadLength + 2));
        rslp.addByte((byte)target); // 0=to dongle, 1=IR.
        rslp.addByte((byte)1); // seqno.
        return rslp;
    }

    public void runForever() {
        run();
    }

}
