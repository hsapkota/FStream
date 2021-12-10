package client.utils;

import client.AdaptiveGridFTPClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class ProfilingThread extends Thread {
    private AdaptiveGridFTPClient main;
    private static final Log LOG = LogFactory.getLog(ProfilingThread.class);
    private final static Logger debugLogger = LogManager.getLogger("reportsLogger");
    public ProfilingThread(AdaptiveGridFTPClient main){
        this.main = main;
    }

    @Override
    public void run() {
        try {
            main.checkNewData();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
