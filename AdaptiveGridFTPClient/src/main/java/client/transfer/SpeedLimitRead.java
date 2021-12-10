package client.transfer;

import client.AdaptiveGridFTPClient;
import java.io.*; 
import java.nio.file.*;

public class SpeedLimitRead{
    String speedLimitFilename = "";
    double startTime;


    public SpeedLimitRead(String filename){
        speedLimitFilename = filename;
        startTime = AdaptiveGridFTPClient.startTransferTime;
    }
    public double getCurrentSpeedLimit(){
        double currentTime = (System.currentTimeMillis() - startTime)/1000.0;
        double timeDur = -1;
        double speedLimit = -1;
        double previousLim = -1;

        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();


        try{
            BufferedReader br = new BufferedReader(new FileReader(this.speedLimitFilename));
            String line;
            while ((line = br.readLine()) != null) {
               String[] msgSplit = line.split(" ");
               System.out.println("Line is: " + line+" and the splitLen is " + msgSplit.length + " currTime: "+currentTime);
               if(msgSplit.length >= 2){
                    timeDur = Double.parseDouble(msgSplit[0]);
                    speedLimit = Double.parseDouble(msgSplit[1]);
                    if (currentTime < timeDur){
                        break;
                    }
                    else if (currentTime >= timeDur){
                        previousLim = speedLimit;
                    }
               }
            }
        } catch(IOException e){
            e.printStackTrace();
        }
        System.out.println("[+] Current spped limit set is " + previousLim);
        return previousLim;
    }
}