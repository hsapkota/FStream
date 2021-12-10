package client.utils;


public class ThroughPutStructure{
    int lastN;
    int maxConc;
    ThroughputList[] thptList;
    public ThroughPutStructure(int storeLastN, int maxConcurrency){
        lastN = storeLastN;
        maxConc = maxConcurrency;
        thptList = new ThroughputList[maxConc+1];
        for(int i =0; i <= maxConc; i++){
            thptList[i] = new ThroughputList(lastN, i);
        }
    }
    public int getLowerThan(int upperBound){
        for(int i = upperBound; i > 0; i--){
            if(thptList[i].getAverage() > 0.0){
                return i;
            }
        }
        return 1;
    }
    public double getAverage(int conc){
        double tmpThpt = -1.0;
        if(conc <= maxConc){
            tmpThpt = thptList[conc].getAverage();
        }
        return tmpThpt;
    }
    public void addThroughput(int conc, double thpt){
        if(conc <= maxConc){
            if(thpt > 0.0){
                thptList[conc].addThroughput(thpt);
            }
        }
    }
    public void printAll(){
        String toP = "";
        for(int i = 0; i < maxConc; i++){
            if(thptList[i].getAverage() > 0.0){
                toP = toP + i +": "+((int)thptList[i].getAverage())+", ";
            }
        }
        System.out.println(toP);
    }
}
class ThroughputList{
    int lastN;
    int currIndex;
    int conc;
    double[] thptList;
    int[] idList;
    static int thptId = 0;
    int sinceIds = 1000;
    double avgThpt = -200.0;
    ThroughputList(){
        
    }
    ThroughputList(int storeLastN, int concurrency){
        lastN = storeLastN;
        currIndex = 0;
        conc = concurrency;
        thptList = new double[lastN];
        idList = new int[lastN];
    }
    int getConc(){
        return conc;
    }
    void addThroughput(double thpt){
        thptList[currIndex] = thpt;
        idList[currIndex] = thptId;
        thptId += 1;
        currIndex += 1;
        if (currIndex == lastN){
            currIndex = 0;
        }
        avgThpt = -200.0;
    }
    double getAverage(){
        if(avgThpt != -200.0){
            return avgThpt;
        }
        double sumAll = 0.0;
        int sumCount = 0;
        for(int i=0; i<lastN; i++){
            if((thptList[i] != 0.0) && ((thptId - idList[i]) < sinceIds)){
                sumAll += thptList[i];
                sumCount += 1;
            }
        }
        double toReturn = -1.0;
        if (sumCount != 0){
            toReturn = sumAll/sumCount;
        }
        avgThpt = toReturn;
        return toReturn;
    }
}
