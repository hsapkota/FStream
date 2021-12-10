package client.transfer;

import client.AdaptiveGridFTPClient;
import client.ConfigurationParams;
import client.FileCluster;
import client.utils.Utils;
import client.utils.HillClimbing;
import client.utils.ThroughPutStructure;
import org.apache.commons.logging.Log;
import client.transfer.SpeedLimitRead;
import org.apache.commons.logging.LogFactory;
import transfer_protocol.module.ChannelModule;
import transfer_protocol.util.SessionParameters;
import java.util.*;


public class ProfilingOps {

    private static final Log LOG = LogFactory.getLog(ProfilingOps.class);
//    private static ChannelOperations channelOperations;
    private static ChannelOperations channelOperations = new ChannelOperations();
    public static final boolean printSysOut= true;
    public static int previousChannels = 1;
    public static int currentChannels = 1;
    public static HistoricalDataRetrive hdr = new HistoricalDataRetrive();
    public static int currentCC = 70;
    public static int totalTimeCC = 0;
    public static int sinceLast = 0;
    private static long lastTime = 0;
    private static int currDelta = 0;
    private static int upperBound = 30;
    private static int speedLimitTwice = 0;
    public static int lastRegressionUsed = 0;
    private static int checkForTimes = 2;
    private static int limitVal = 20;
    private static int lastTimeSet = 1;
    private static int lowBound = 1;
    private static int counterCheck = 1;
    private static int deviationFrom = 0;
    public static HillClimbing hc = new HillClimbing();
    public static boolean speedLimit = false;
    public static Map<Integer, List<Double>> thptMap = new HashMap<Integer, List<Double>>();
    public static ThroughPutStructure tps;
    public static void chunkProfiling(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------PROFILING START------------------");
        System.out.println("-------------PROFILING START------------------");

        double upperLimitInit = ConfigurationParams.upperLimitInit;
        double upperLimit = ConfigurationParams.upperLimit;
        double totalThroughput = getAverageThroughput();
        double low = upperLimitInit; //- upperLimitInit * 20 / 100;

        if (totalThroughput > low) {
            System.err.println("CONT. NO NEED PROFILING");
            AdaptiveGridFTPClient.counterOfProfilingChanger++;
        } else {
            double perChannelThroughput;
            perChannelThroughput = totalThroughput / AdaptiveGridFTPClient.channelInUse.size();
            LOG.info("upperLimit = " + upperLimit + " perChannelThroughput = " + perChannelThroughput);
            if (printSysOut)
                System.out.println("upperLimit = " + upperLimit + " perChannelThroughput = " + perChannelThroughput);

            upperLimit = upperLimitInit; //* ConfigurationParams.percentageRate / 80;
            if (printSysOut)
                System.err.println("upperLimit: " + upperLimit + " || upperLimitInit: " + upperLimitInit);

            int possibleConcCount = (int) ((int) upperLimit / perChannelThroughput);
            if (printSysOut)
                System.out.println("POssible ch count = " + possibleConcCount);
            if (possibleConcCount > AdaptiveGridFTPClient.channelInUse.size()) {

                if (possibleConcCount > AdaptiveGridFTPClient.channelInUse.size() * 2) {
                    possibleConcCount = AdaptiveGridFTPClient.channelInUse.size() * 2;
                }

                if (possibleConcCount > maxConcurrency) {
                    possibleConcCount = maxConcurrency;
                }

                if (printSysOut)
                    System.out.println("NEW POSSIBLE CHANNEL COUNT ===== " + possibleConcCount);
                Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, possibleConcCount,channelDistPolicy);
                if (printSysOut)
                    System.out.println("Allocation copmleted: ");
                setProfilingSettings();
            }
        }
        LOG.info("-------------PROFILING END------------------");
        System.out.println("-------------PROFILING END------------------");
    }

    public static void qosProfiling(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------QoS START------------------");
        System.out.println("-------------QoS START------------------");

        double upperLimit = ConfigurationParams.speedLimit;

        int curChannelCount = AdaptiveGridFTPClient.channelInUse.size();
        double totalThroughput = getAverageThroughput();
        double totalThroughput1 = AdaptiveGridFTPClient.avgThroughput.get(AdaptiveGridFTPClient.avgThroughput.size() - 1);

        double perChannelThroughput;
        perChannelThroughput = totalThroughput / AdaptiveGridFTPClient.channelInUse.size();
        System.out.println("PER CHANNEL THROUGHPUT = " + perChannelThroughput);

        int diff = Math.abs((int) (totalThroughput - upperLimit));
        int diff1 = Math.abs((int) (totalThroughput1 - upperLimit));
        int newChannelCount = curChannelCount;
        System.out.println("CURRENT CHANNEL COUNT = " + curChannelCount);

        if (totalThroughput > upperLimit) {
            if ((diff > ((int) upperLimit * 15 / 100)) && (diff1 > ((int) upperLimit * 15 / 100))) {
                newChannelCount = (int) (upperLimit / perChannelThroughput);
                AdaptiveGridFTPClient.counterOfProfilingChanger = 0;
                System.out.println("QoS profiled. New channel count = " + newChannelCount);
            }
            if (AdaptiveGridFTPClient.counterOfProfilingChanger > 3 || (diff > ((int) upperLimit * 5 / 100) && diff < ((int) upperLimit * 15 / 100))) {
                System.err.println("The speed was reliable, fine tuning is started.....");
                qOSChangePipeAndPar(-1);
                AdaptiveGridFTPClient.counterOfProfilingChanger = 0;
            } else {
                System.err.println("CONT. NO NEED PROFILING");
                AdaptiveGridFTPClient.counterOfProfilingChanger++;
            }
        } else {
            if ((diff > ((int) upperLimit * 20 / 100)) && (diff1 > ((int) upperLimit * 20 / 100))) {
                newChannelCount = (int) (upperLimit / perChannelThroughput);
                System.out.println("QoS profiled. New channel count = " + newChannelCount);
                AdaptiveGridFTPClient.counterOfProfilingChanger = 0;
            }
            //avoid dramatic changes
            if (newChannelCount > curChannelCount * 2) {
                newChannelCount = curChannelCount * 2;
            }
            if (newChannelCount > maxConcurrency) {
                newChannelCount = maxConcurrency;
            }

            if (AdaptiveGridFTPClient.counterOfProfilingChanger > 2 || (diff > ((int) upperLimit * 5 / 100) && diff < ((int) upperLimit * 10 / 100))) {
                qOSChangePipeAndPar(1);
                AdaptiveGridFTPClient.counterOfProfilingChanger = 0;
            }

        }
        if (newChannelCount != curChannelCount) {
            System.out.println("NEW CHANNEL COUNT = " + newChannelCount);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + newChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, newChannelCount, channelDistPolicy);
            setProfilingSettings();
        }
        LOG.info("-------------QoS ENDED------------------");
        System.out.println("-------------QoS ENDED------------------");
    }

    public static void hillClimbing(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------Hill Climbing START------------------");
        System.out.println("-------------Hill Climbing START------------------");
        long timeDuration = 4000;
        double upperLimit = ConfigurationParams.speedLimit * 1.1;

        int curChannelCount = AdaptiveGridFTPClient.channelInUse.size();
        if (ProfilingOps.lastTime == 0){
            hc.set_m_currentControlSetting(curChannelCount);
        }else {
            timeDuration = System.currentTimeMillis() - ProfilingOps.lastTime;
        }

        double totalThroughput = getAverageThroughput();
        hc.addData(totalThroughput, curChannelCount);
        // addDataHillClimbing();
        double totalThroughput1 = AdaptiveGridFTPClient.avgThroughput.get(AdaptiveGridFTPClient.avgThroughput.size() - 1);
        int newChannelCount = curChannelCount;
        if(currDelta == 1){
            newChannelCount = (int)Math.min(maxConcurrency, (int)Math.ceil(1.15*curChannelCount));
            currDelta = -1;
        }else if(currDelta == -1){
            newChannelCount = (int)Math.max((int)0.85*curChannelCount/1.15, 1);
            currDelta = 0;
            
        }else if (currDelta == 0){
            newChannelCount = hc.update(curChannelCount, totalThroughput, maxConcurrency, timeDuration/1000.);
            currDelta = 3;
        }else {
            newChannelCount = curChannelCount;
            currDelta = 1;
        }

        if (newChannelCount != curChannelCount) {
            System.out.println("NEW CHANNEL COUNT = " + newChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + newChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, newChannelCount, channelDistPolicy);
            setProfilingSettings();
            ProfilingOps.previousChannels = curChannelCount;
        }
        ProfilingOps.lastTime = System.currentTimeMillis();
        LOG.info("-------------Hill Climbing ENDED------------------");
        System.out.println("-------------Hill Climbing ENDED------------------");
    }

    public static void allCCs(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------All CCs START------------------");
        System.out.println("-------------All CCs START------------------");

        
        ProfilingOps.totalTimeCC = ProfilingOps.totalTimeCC + 1;
        
        int curChannelCount = AdaptiveGridFTPClient.channelInUse.size();
        int newChannelCount = curChannelCount;
        if (ProfilingOps.totalTimeCC > 1 && (ProfilingOps.currentCC + 5 <= maxConcurrency+20)){
            newChannelCount = ProfilingOps.currentCC + 5;
        }
        if (newChannelCount != curChannelCount) {
            System.out.println("NEW CHANNEL COUNT = " + newChannelCount);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + newChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, newChannelCount, channelDistPolicy);
            setProfilingSettings();
            ProfilingOps.previousChannels = curChannelCount;
            ProfilingOps.totalTimeCC = 0;
            ProfilingOps.currentCC = newChannelCount;
        }
        LOG.info("-------------All CCs ENDED------------------");
        System.out.println("-------------All CCs ENDED------------------");
    }/*
    public static int getNextCC(int curChannelCount, double totalThroughput, double lastThroughput, double upperLimit){
        if(totalThroughput > (1.05 * upperLimit)){
            return (int) Math.max(1, curChannelCount - 1);
        }
        if(upperLimit == -1){

        }else{
            if((totalThroughput < (1.05 * upperLimit)) && (totalThroughput > (0.95 * upperLimit))){
                return curChannelCount;
            }
            
        }
        return curChannelCount;
    }
    public static int getNextCC(int curChannelCount, double totalThroughput, double lastThroughput, double upperLimit){
        int lowerCC = 1;
        int upperCC = 1;
        double lowerThpt = 0.0;
        double upperThpt = 100000000.0;
        for (Integer key : thptMap.keySet()) {
            ArrayList<Double> listVal = thptMap.get(key);
            double totalThpt = 0.0;
            int count = 0;
            for (int i = (int)Math.max(listVal.size() - 10, 0); i < listVal.size(); i++){
                totalThpt += listVal.get(i);
                count += 1;
            }
            double thpt = 0.0;
            if (count > 0){
                thpt = totalThpt/count;
            }
            if(upperLimit < thpt){
                if(lowerThpt < thpt){
                    lowerThpt = thpt;
                    lowerCC = key;
                }
            }else{
                if(upperThpt > thpt){
                    upperThpt = thpt;
                    upperCC = key;
                }
            }
            
        }
    }//*/
    public static int shouldIncrease(double thpt1, double thpt2, int cc1, int cc2){
        double xCept = cc2 - cc1;
        if(xCept == 0){
            return 0;
        }
        if(thpt1 != 0.0){
            if(cc1 < cc2){
                if(((thpt2-thpt1)/thpt1) < 0.01){
                    return -1;
                }
            }
        }
        double slope = (thpt2 - thpt1)/xCept;
        if(slope <= 0.0){
            return -1;
        }
        return 1;
    }
    public static int nextCC(int currentCC, int maxConcurrency, double speedLimitForNow){
        int tmpCurrCC = currentCC;
        // if(tmpCurrCC < ProfilingOps.previousChannels){
        //     upperBound = ProfilingOps.previousChannels;
        //     lowBound = tps.getLowerThan(tmpCurrCC);
        // }
        double speedLmt = speedLimitForNow;
        if(!ProfilingOps.speedLimit){
            int transition = 1;
            switch(transition){
                case 1:
                    while(true){
                        double upperThpt = tps.getAverage(upperBound);
                        double lowerThpt = tps.getAverage(lowBound);
                        double thptCurr = tps.getAverage(tmpCurrCC);
                        // System.out.println("tmpCurrCC: " + tmpCurrCC + ", upperBound: "+upperBound+", lowBound: "+lowBound +", upperThpt: "+upperThpt+", lowerThpt: "+lowerThpt+", thptCurr: "+thptCurr);
                        if((upperBound == tmpCurrCC) || (lowBound == upperBound)){
                            //Change to stabilizing;
                            return tmpCurrCC;
                        }
                        int mid = (tmpCurrCC + upperBound)/2;
                        if((lowerThpt == -1.0) && (upperThpt == -1.0)){
                            if(mid - tmpCurrCC > limitVal){
                                return tmpCurrCC + limitVal;
                            }
                            lowBound = tmpCurrCC;
                            return mid;
                        }
                        if (thptCurr == -1.0){
                            return (int)Math.max(tmpCurrCC, upperBound);
                        }
                        // System.out.println("Returned berfore here");
                        int lowIncVal = shouldIncrease(lowerThpt, thptCurr, lowBound, tmpCurrCC);
                        // System.out.println("lowIncVal: " + lowIncVal);
                        if((lowIncVal == 1) && (upperThpt == -1.0)){
                            if(mid - tmpCurrCC > limitVal){
                                return tmpCurrCC + limitVal;
                            }
                            lowBound = tmpCurrCC;
                            return mid;
                        }
                        // System.out.println("Returned berfore here 2");
                        int highIncVal = shouldIncrease(thptCurr, upperThpt, tmpCurrCC, upperBound);
                        // System.out.println("highIncVal: " + highIncVal);

                        if((lowIncVal == 1) && (highIncVal != 1)){
                            return tmpCurrCC;
                        }
                        else if((lowIncVal == 1) && (highIncVal == 1)){
                            lowBound = tmpCurrCC;
                            return upperBound;
                        }
                        else if(lowIncVal == 0){
                            return lowBound;
                        }
                        else if(highIncVal == 0){
                            return tmpCurrCC;
                        }
                        else if((lowIncVal == -1) && (highIncVal == -1)){
                            upperBound = tmpCurrCC;
                            tmpCurrCC = (tmpCurrCC + lowBound)/2;

                            if(upperBound - tmpCurrCC > 7){
                                tmpCurrCC = upperBound - 7;
                            }else {
                                tmpCurrCC = mid;
                            }
                        }
                        else {
                            return tmpCurrCC + 1;
                        }
                    }
                default:
                    return currentCC;
            }
        }else if (speedLimitTwice >= checkForTimes){
            limitVal = 5;
            int transition = 1;
            switch(transition){
                case 1:
                    while(true){
                        double upperThpt = tps.getAverage(upperBound);
                        double lowerThpt = tps.getAverage(lowBound);
                        double thptCurr = tps.getAverage(tmpCurrCC);

                        if(upperBound == currentCC){
                            deviationFrom += 0;
                        }
                        if(deviationFrom > 10 && (Math.abs(thptCurr / speedLmt)<0.8)){
                            upperBound = maxConcurrency;
                            upperThpt = tps.getAverage(upperBound);
                            deviationFrom = 0;
                        }
                        System.out.println("tmpCurrCC: " + tmpCurrCC + ", upperBound: "+upperBound+", lowBound: "+lowBound +", upperThpt: "+upperThpt+", lowerThpt: "+lowerThpt+", thptCurr: "+thptCurr + ", currentCC: "+currentCC + ", deviationFrom: "+deviationFrom + ", speedLimit: " + speedLimitForNow);
                        speedLimitTwice = 1;
                        if(Math.abs(thptCurr / speedLmt) > 0.95){
                            System.out.println("Speed Limit check");
                            upperBound = currentCC;
                            if((thptCurr / speedLmt) < 1.05){
                                return currentCC;
                            }else{
                                double timesDec = (thptCurr / speedLmt);
                                int decreseBy = currentCC - 1;
                                if (timesDec > 0.1){
                                    decreseBy = (int) (currentCC/timesDec);
                                }
                                int tmpVal1 = (int)Math.max(1, decreseBy);
                                lowBound = (int) Math.min(lowBound, tmpVal1);
                                return tmpVal1;
                            }
                        }

                        if((upperBound == tmpCurrCC) || (lowBound == upperBound)){
                            //Change to stabilizing;
                            return tmpCurrCC;
                        }
                        
                        int mid = (tmpCurrCC + upperBound)/2;
                        if((lowerThpt == -1.0) && (upperThpt == -1.0)){
                            if(mid - tmpCurrCC > limitVal){
                                return tmpCurrCC + limitVal;
                            }
                            lowBound = tmpCurrCC;
                            return mid;
                        }
                        if (thptCurr == -1.0){
                            return (int)Math.min(tmpCurrCC, upperBound);
                        }
                        System.out.println("Returned berfore here");
                        int lowIncVal = shouldIncrease(lowerThpt, thptCurr, lowBound, tmpCurrCC);
                        System.out.println("lowIncVal: " + lowIncVal);
                        if((lowIncVal == 1) && (upperThpt == -1.0)){
                            if(mid - tmpCurrCC > limitVal){
                                return tmpCurrCC + limitVal;
                            }
                            lowBound = tmpCurrCC;
                            return mid;
                        }
                        System.out.println("Returned berfore here 2");
                        int highIncVal = shouldIncrease(thptCurr, upperThpt, tmpCurrCC, upperBound);
                        System.out.println("highIncVal: " + highIncVal);
                        if((lowIncVal == 0) && (lowBound == tmpCurrCC)){
                            if(upperThpt - speedLmt < speedLmt - lowerThpt){
                                return upperBound;
                            }
                        }
                        if((lowIncVal == 1) && (highIncVal != 1)){
                            if(upperBound == lowBound + 1){
                                if(upperThpt - speedLmt < speedLmt - lowerThpt){
                                    return upperBound;
                                }
                            }
                            return tmpCurrCC;
                        }
                        else if((lowIncVal == 1) && (highIncVal == 1)){
                            lowBound = tmpCurrCC;
                            return upperBound;
                        }
                        else if(lowIncVal == 0){
                            return lowBound;
                        }
                        else if(highIncVal == 0){
                            return tmpCurrCC;
                        }
                        else if((lowIncVal == -1) && (highIncVal == -1)){
                            upperBound = tmpCurrCC;
                            tmpCurrCC = (tmpCurrCC + lowBound)/2;

                            if(upperBound - tmpCurrCC > 7){
                                tmpCurrCC = upperBound - 7;
                            }else {
                                tmpCurrCC = mid;
                            }
                        }
                        else {
                            return tmpCurrCC + 1;
                        }
                    }
                default:
                    return currentCC;
            }
        }
        speedLimitTwice += 1;
        return currentCC;
    }
    public static void simpleHillClimbingModified(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------Modified hc------------------");
        System.out.println("-------------Modified hc------------------");
        long timeDuration = 4000;
        double upperLimit = ConfigurationParams.speedLimit * 1.1;
        int curChannelCount = AdaptiveGridFTPClient.channelInUse.size();
        double totalThroughput = getAverageThroughput();
        ProfilingOps.speedLimit = true;
        SpeedLimitRead slr = new SpeedLimitRead("./speedLimit");
        upperLimit = slr.getCurrentSpeedLimit();
        if(ProfilingOps.counterCheck < 2){
            ProfilingOps.counterCheck += 1;
            return;
        }
        if (ProfilingOps.lastTime == 0){
            tps = new ThroughPutStructure(4, maxConcurrency);
            upperBound = maxConcurrency;
            lowBound = 0;
            lastTimeSet = curChannelCount;
            double spLim = upperLimit;
            double perChannelThpt = totalThroughput/curChannelCount;

            // limitVal = 20;
            // checkForTimes = 2;
            upperBound = (int) Math.min(spLim / perChannelThpt, upperBound);
            limitVal = (int) upperBound / 3;
            limitVal = (int) Math.max(1, limitVal);
            checkForTimes = (int) Math.max(1, Math.floor(3 - upperBound / 10));
            limitVal = 2;
            System.out.println("UpperBound: " + upperBound + ", limitValue: " + limitVal + ", checkForTimes: " + checkForTimes);
            
        }else {
            timeDuration = System.currentTimeMillis() - ProfilingOps.lastTime;
        }

        // hc.addThroughput(curChannelCount, totalThroughput);
        addDataThptStructure();
        double totalThroughput1 = AdaptiveGridFTPClient.avgThroughput.get(AdaptiveGridFTPClient.avgThroughput.size() - 1);
        int newChannelCount = curChannelCount;

        if(currDelta % 5 == 9){
            newChannelCount = (int)Math.min(maxConcurrency, (int)Math.ceil(1.15*curChannelCount));
            currDelta = 0;
        }else if(currDelta % 5 == 9){
            newChannelCount = (int)Math.max((int)0.85*curChannelCount/1.15, 1);
            
        }else{
            newChannelCount = nextCC(lastTimeSet, maxConcurrency, upperLimit);
            
        }
        // currDelta += 1;
        // System.out.println("----====----");
        // System.out.println("CC is " + newChannelCount);
        // tps.printAll();
        // System.out.println("----====----");
        if((AdaptiveGridFTPClient.lastSpeedLimit == 0.) || (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit)){
            if (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit){
                double spLim = upperLimit;
                double perChannelThpt = totalThroughput/curChannelCount;

                upperBound = (int) Math.min(spLim / perChannelThpt, upperBound);
                limitVal = (int) upperBound / 3;
                limitVal = (int) Math.max(1, limitVal);
                checkForTimes = (int) Math.max(1, Math.floor(3 - upperBound / 10));
            }
            AdaptiveGridFTPClient.lastSpeedLimit = upperLimit;
            newChannelCount = (int)Math.max(1, newChannelCount);
            newChannelCount = (int)Math.min(maxConcurrency, newChannelCount);
            upperBound = maxConcurrency;
            lowBound = 1;
        }else{
            newChannelCount = (int)Math.max(1, newChannelCount);
            newChannelCount = (int)Math.min(maxConcurrency, newChannelCount);
            upperBound = (int)Math.min(maxConcurrency, upperBound);
            lowBound = (int)Math.min(maxConcurrency, lowBound);
        }
        if (newChannelCount != curChannelCount) {
            System.out.println("NEW CHANNEL COUNT = " + newChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + newChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, newChannelCount, channelDistPolicy);
            setProfilingSettings();
            ProfilingOps.previousChannels = lastTimeSet;
            lastTimeSet = newChannelCount;
        }else{
            System.out.println("NEW CHANNEL COUNT = " + curChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + curChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, curChannelCount, channelDistPolicy);
            setProfilingSettings();
        }
        ProfilingOps.lastTime = System.currentTimeMillis();
        LOG.info("-------------Modified hc ended------------------");
        System.out.println("-------------Modified hc ended------------------");
    }
    public static void simpleHillClimbing(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------Modified hc------------------");
        System.out.println("-------------Modified hc------------------");
        long timeDuration = 4000;
        double upperLimit = ConfigurationParams.speedLimit * 1.1;
        int curChannelCount = AdaptiveGridFTPClient.channelInUse.size();
        double totalThroughput = getAverageThroughput();
        ProfilingOps.speedLimit = true;
        SpeedLimitRead slr = new SpeedLimitRead("./speedLimit");
        upperLimit = slr.getCurrentSpeedLimit();
        if(ProfilingOps.counterCheck < 2){
            ProfilingOps.counterCheck += 1;
            return;
        }
        if (ProfilingOps.lastTime == 0){
            tps = new ThroughPutStructure(4, maxConcurrency);
            upperBound = maxConcurrency;
            lowBound = 0;
            lastTimeSet = curChannelCount;
            if (speedLimit){
                double spLim = upperLimit;
                double perChannelThpt = totalThroughput/curChannelCount;

                // limitVal = 20;
                // checkForTimes = 2;
                upperBound = (int) Math.min(spLim / perChannelThpt, upperBound);
                limitVal = (int) upperBound / 3;
                limitVal = (int) Math.max(1, limitVal);
                checkForTimes = (int) Math.max(1, Math.floor(3 - upperBound / 10));
                limitVal = 2;
                System.out.println("UpperBound: " + upperBound + ", limitValue: " + limitVal + ", checkForTimes: " + checkForTimes);
            }else{
                limitVal = 20;
                checkForTimes = 2;
            }
        }else {
            timeDuration = System.currentTimeMillis() - ProfilingOps.lastTime;
        }

        // hc.addThroughput(curChannelCount, totalThroughput);
        addDataThptStructure();
        double totalThroughput1 = AdaptiveGridFTPClient.avgThroughput.get(AdaptiveGridFTPClient.avgThroughput.size() - 1);
        int newChannelCount = curChannelCount;

        if(currDelta % 5 == 9){
            newChannelCount = (int)Math.min(maxConcurrency, (int)Math.ceil(1.15*curChannelCount));
            currDelta = 0;
        }else if(currDelta % 5 == 9){
            newChannelCount = (int)Math.max((int)0.85*curChannelCount/1.15, 1);
            
        }else{
            newChannelCount = nextCC(lastTimeSet, maxConcurrency, upperLimit);
            
        }
        // currDelta += 1;
        // System.out.println("----====----");
        // System.out.println("CC is " + newChannelCount);
        // tps.printAll();
        // System.out.println("----====----");
        if((AdaptiveGridFTPClient.lastSpeedLimit == 0.) || (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit)){
            if (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit){
                double spLim = upperLimit;
                double perChannelThpt = totalThroughput/curChannelCount;

                upperBound = (int) Math.min(spLim / perChannelThpt, upperBound);
                limitVal = (int) upperBound / 3;
                limitVal = (int) Math.max(1, limitVal);
                checkForTimes = (int) Math.max(1, Math.floor(3 - upperBound / 10));
            }
            AdaptiveGridFTPClient.lastSpeedLimit = upperLimit;
            newChannelCount = (int)Math.max(1, newChannelCount);
            newChannelCount = (int)Math.min(maxConcurrency, newChannelCount);
            upperBound = maxConcurrency;
            lowBound = 1;
        }else{
            newChannelCount = (int)Math.max(1, newChannelCount);
            newChannelCount = (int)Math.min(maxConcurrency, newChannelCount);
            upperBound = (int)Math.min(maxConcurrency, upperBound);
            lowBound = (int)Math.min(maxConcurrency, lowBound);
        }
        if (newChannelCount != curChannelCount) {
            System.out.println("NEW CHANNEL COUNT = " + newChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + newChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, newChannelCount, channelDistPolicy);
            setProfilingSettings();
            ProfilingOps.previousChannels = lastTimeSet;
            lastTimeSet = newChannelCount;
        }else{
            System.out.println("NEW CHANNEL COUNT = " + curChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
            AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + curChannelCount);
            Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, curChannelCount, channelDistPolicy);
            setProfilingSettings();
        }
        ProfilingOps.lastTime = System.currentTimeMillis();
        LOG.info("-------------Modified hc ended------------------");
        System.out.println("-------------Modified hc ended------------------");
    }

    public static void simpleRegressionUsed(int maxConcurrency, ConfigurationParams.ChannelDistributionPolicy channelDistPolicy) {
        LOG.info("-------------Modified regression hc------------------");
        System.out.println("-------------Modified regression hc------------------");
        long timeDuration = 4000;
        double upperLimit = ConfigurationParams.speedLimit * 1.1;
        int curChannelCount = AdaptiveGridFTPClient.channelInUse.size();
        double totalThroughput = getAverageThroughput();
        ProfilingOps.speedLimit = true;
        boolean regressionUsed = false;

        if (ProfilingOps.lastTime == 0){
            tps = new ThroughPutStructure(10, maxConcurrency);
            upperBound = maxConcurrency;
            lowBound = 0;
            lastTimeSet = curChannelCount;
            if (speedLimit){
                double spLim = ConfigurationParams.speedLimit;
                double perChannelThpt = totalThroughput/curChannelCount;

                // limitVal = 20;
                // checkForTimes = 2;
                int upperBoundtmp = (int) Math.min(spLim / perChannelThpt, upperBound);
                limitVal = (int) upperBoundtmp / 3;
                limitVal = (int) Math.max(1, limitVal);
                checkForTimes = (int) Math.max(1, Math.floor(3 - upperBoundtmp / 10));
                upperBound = upperBoundtmp;
                System.out.println("UpperBound: " + upperBound + ", limitValue: " + limitVal + ", checkForTimes: " + checkForTimes);
            }else{
                limitVal = 20;
                checkForTimes = 2;
            }
        }else {
            timeDuration = System.currentTimeMillis() - ProfilingOps.lastTime;
        }
        SpeedLimitRead slr = new SpeedLimitRead("./speedLimit");
        upperLimit = slr.getCurrentSpeedLimit();
        // System.out.println("++++++++++++++++UpLimit: " + upperLimit);
        if(upperLimit <= 0){
            upperLimit = ConfigurationParams.speedLimit;
        }

        // hc.addThroughput(curChannelCount, totalThroughput);
        addDataThptStructure();
        double totalThroughput1 = AdaptiveGridFTPClient.avgThroughput.get(AdaptiveGridFTPClient.avgThroughput.size() - 1);
        int newChannelCount = curChannelCount;

        ProfilingOps.lastRegressionUsed += 1;
        if (ProfilingOps.lastRegressionUsed > 2){
            if((AdaptiveGridFTPClient.lastSpeedLimit == 0.) || (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit)){
                if((AdaptiveGridFTPClient.lastSpeedLimit != 0.) && (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit)){
                    
                    newChannelCount = ProfilingOps.hdr.getCCFromThpt(upperLimit, maxConcurrency-1);
                    
                    if(AdaptiveGridFTPClient.lastSpeedLimit < upperLimit){
                        lowBound = curChannelCount;
                        upperBound = maxConcurrency;
                    }else if (AdaptiveGridFTPClient.lastSpeedLimit > upperLimit){
                        upperBound = curChannelCount;
                        lowBound = 1;
                    }
                    deviationFrom = 1;

                    System.out.println("ProfilingOps Output:" + newChannelCount);
                    if((newChannelCount > 0) && (newChannelCount <= maxConcurrency)){
                        if (AdaptiveGridFTPClient.lastSpeedLimit != upperLimit) {
                            ProfilingOps.lastRegressionUsed = 0;
                        }
                        AdaptiveGridFTPClient.lastSpeedLimit = upperLimit;
                        regressionUsed = true;
                    }else{
                        newChannelCount = curChannelCount;
                    }
                }else if (AdaptiveGridFTPClient.lastSpeedLimit == 0.){
                    AdaptiveGridFTPClient.lastSpeedLimit = upperLimit;
                }
                // tps = new ThroughPutStructure(2, maxConcurrency);
                
                lastTimeSet = curChannelCount;
                if (speedLimit){
                    double spLim = upperLimit;
                    double perChannelThpt = totalThroughput/curChannelCount;

                    // limitVal = 20;
                    // checkForTimes = 2;
                    int upperBoundtmp = (int) Math.min(spLim / perChannelThpt, upperBound);
                    limitVal = (int) upperBoundtmp / 3;
                    limitVal = (int) Math.max(1, limitVal);
                    checkForTimes = (int) Math.max(1, Math.floor(3 - upperBoundtmp / 10));
                    System.out.println("UpperBound: " + upperBound + ", limitValue: " + limitVal + ", checkForTimes: " + checkForTimes);
                }else{
                    limitVal = 20;
                    checkForTimes = 2;
                }
                
                System.out.println("UpperBound: " + upperBound + ", limitValue: " + limitVal + ", checkForTimes: " + checkForTimes + ", lowBound: "+lowBound+ ", lastLimit: " + AdaptiveGridFTPClient.lastSpeedLimit + ", currLimit: " + upperLimit);
            }
            if(!regressionUsed){
                if(currDelta % 5 == 9){
                    newChannelCount = (int)Math.min(maxConcurrency, (int)Math.ceil(1.15*curChannelCount));
                    currDelta = 0;
                }else if(currDelta % 5 == 9){
                    newChannelCount = (int)Math.max((int)0.85*curChannelCount/1.15, 1);
                    
                }else{
                    newChannelCount = nextCC(lastTimeSet, maxConcurrency, upperLimit);
                    
                }
                newChannelCount = (int)Math.max(1, newChannelCount);
                newChannelCount = (int)Math.min(maxConcurrency, newChannelCount);
                upperBound = (int)Math.min(maxConcurrency, upperBound);
                lowBound = (int)Math.min(maxConcurrency, lowBound);
            }
            // else{
            //     deviationFrom = 13;
            //     upperBound = (int)Math.min(maxConcurrency, newChannelCount + 5);
            //     lowBound = (int)Math.min(1, newChannelCount - 5);
            //     lowBound = 1;

            // }
            
            if (newChannelCount != curChannelCount) {
                System.out.println("NEW CHANNEL COUNT = " + newChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
                AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + newChannelCount);
                Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, newChannelCount, channelDistPolicy);
                setProfilingSettings();
                ProfilingOps.previousChannels = lastTimeSet;
                lastTimeSet = newChannelCount;
            }else{
                System.out.println("NEW CHANNEL COUNT = " + curChannelCount + " curChannelCount = " + curChannelCount + ", currDelta = " + currDelta);
                AdaptiveGridFTPClient.debugLogger.info("NEW CHANNEL COUNT = " + curChannelCount);
                Utils.allocateChannelsToChunks(AdaptiveGridFTPClient.chunks, curChannelCount, channelDistPolicy);
                setProfilingSettings();
            }
            ProfilingOps.lastTime = System.currentTimeMillis();
        }
        LOG.info("-------------Modified hc ended------------------");
        System.out.println("-------------Modified hc ended------------------");
    }
    private static void setProfilingSettings() {
        for (FileCluster f : AdaptiveGridFTPClient.chunks) {
            if (printSysOut)
                System.out.println("HISTORY ------------ Chunk = " + f.getDensity().toString() + " new channel count = " + f.getTunableParameters().getConcurrency());
            AdaptiveGridFTPClient.historicalProfiling.put(f.getDensity().toString(), f.getTunableParameters().getConcurrency());
            if (printSysOut)
                System.out.println(f.getDensity() + " conc = " + f.getTunableParameters().getConcurrency());
            SessionParameters sp = AdaptiveGridFTPClient.sessionParametersMap.get(f.getDensity().toString());
            sp.setConcurrency(f.getTunableParameters().getConcurrency());
            AdaptiveGridFTPClient.sessionParametersMap.put(f.getDensity().toString(), sp);
        }
    }
    private static void addDataHillClimbing(){
        for (int i = AdaptiveGridFTPClient.avgThroughput.size() - 2; i < AdaptiveGridFTPClient.avgThroughput.size(); i++) {
            System.out.println("i = " + i + ", Channel = " + AdaptiveGridFTPClient.channelCounts.get(i) + ", Thpt = "+ AdaptiveGridFTPClient.avgThroughput.get(i));
            hc.addData(AdaptiveGridFTPClient.avgThroughput.get(i)/100.0, AdaptiveGridFTPClient.channelCounts.get(i));
        }
    }
    private static void addDataThptStructure(){
        for (int i = AdaptiveGridFTPClient.avgThroughput.size() - 2; i < AdaptiveGridFTPClient.avgThroughput.size(); i++) {
            if(ProfilingOps.lastTimeSet == AdaptiveGridFTPClient.channelCounts.get(i)){
                System.out.println("Adding throughput log i = " + i + ", Channel = " + AdaptiveGridFTPClient.channelCounts.get(i) + ", Thpt = "+ AdaptiveGridFTPClient.avgThroughput.get(i));
                tps.addThroughput(AdaptiveGridFTPClient.channelCounts.get(i), AdaptiveGridFTPClient.avgThroughput.get(i));
            }
            /*
            else {
                System.out.println("What we set in HC: " + ProfilingOps.previousChannels + 
                " What we observed in the wild:" + AdaptiveGridFTPClient.channelCounts.get(i) + " is not the same" + " and lastTimeSet is " + lastTimeSet);
            }
            */
        }
    }
    private static double getAverageThroughput() {
        double tp = 0;
        for (int i = AdaptiveGridFTPClient.avgThroughput.size() - 1; i > AdaptiveGridFTPClient.avgThroughput.size() - 4; i--) {
            double tmp = AdaptiveGridFTPClient.avgThroughput.get(i);
            tp += tmp;
        }
        return tp / 3;
    }

    private static void qOSChangePipeAndPar(int isUp) {
        for (FileCluster f : AdaptiveGridFTPClient.chunks) {
            boolean parChanged = false;
            for (ChannelModule.ChannelPair c : f.getRecords().channels) {
                if (f.getDensity().toString().equals("SMALL") && c.getPipelining() >= 1) {
                    System.out.println("Channel: " + c.getId() + " PIPELINING changed from > " + c.getPipelining() + " to " + (c.getPipelining() - (isUp)));
                    AdaptiveGridFTPClient.debugLogger.info("Channel: " + c.getId() + " PIPELINING changed from > " + c.getPipelining() + " to " + (c.getPipelining() - (isUp)));
                    f.getTunableParameters().setPipelining(c.getPipelining() - (isUp));
                    c.setPipelining(c.getPipelining() - (isUp));
                }

//                if (f.getDensity().toString().equals("LARGE") && c.parallelism >= 1 && !parChanged) {
//                    int par = c.parallelism - (isUp);
//                    System.out.println("Channel: " + c.getId() + "parallelism changed from" + c.parallelism + "to > " + (c.parallelism - (isUp)));
//                    AdaptiveGridFTPClient.debugLogger.info(("Channel: " + c.getId() + "parallelism changed from" + c.parallelism + "to > " + (c.parallelism - (isUp))));
//                    f.getTunableParameters().setParallelism(par);
//                    System.err.println("RESET CHANNEL > > > > " + f.getTunableParameters().getParallelism());
//                    AdaptiveGridFTPClient.debugLogger.info(("RESET CHANNEL > > > > " + f.getTunableParameters().getParallelism()));
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    try {
//                        channelOperations.parallelismChange(c, f);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    parChanged = true;
//                }

            }
        }
    }

}

/*
class HillClimbing{
    Queue<Integer> q = new LinkedList<>();
    HashMap<Integer, double[]> hashMap = new HashMap<Integer, double[]>();
    int queueSize = 10;
    double speedLimit = 12000;
    int id = 0;
    HillClimbing(int queueSize, double speedLimit){
        this.queueSize = queueSize;
        this.speedLimit = speedLimit;
    }
    void addData(int param, double throughput){
        if (this.q.size() == this.queueSize){
            this.q.remove();
        }
        this.q.add(this.id);
        double[] insertList = new double[2];
        insertList[0] = param *1.0;
        insertList[1] = throughput;

        this.hashMap.put(this.id, new double[] {param*1.0, throughput});
        this.id++;
    }
    void setSpeedLimit(double speedLimit){
        this.speedLimit = speedLimit;
    }
    void getNextParam(int param){

    }
    double[] getLastParam(){
        if (this.id == 0){
            return new double[] {0., 0.};
        }
        return this.hashMap.get(this.id - 1);
    }
    int defineObjective(int param, double throughput){
        double steps = throughput - this.speedLimit;
        double previousSteps = steps / this.speedLimit;


        double[] previous = this.getLastParam();
        if(previous[0] == 0.0){
            return 1;
        }
        double previousScore = previous[1] - this.speedLimit;
        double previousPer = previousScore / this.speedLimit;


        int direction = 1;
        if ((previous[1] > param) && (throughput > previous[1])){
            direction = -1;
        }else if ((previous[1] < param) && (throughput < previous[1])){
            direction = -1;
        }

        if (previous[0] < param){
            if(direction == -1){
                return previous[0];
            }

        }else if ((int)previous[0] == param){
            
        }else{
            
        }

        this.addData(param, throughput);
        return 10;
    }
}

//*/