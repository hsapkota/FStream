package client.transfer;

import client.AdaptiveGridFTPClient;
import java.io.*;
import java.util.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;


public class HistoricalDataRetrive{
    private String folderDir = "./training_logs/";
    private HashMap<Integer, List<Double>> thpts = new HashMap<Integer, List<Double>>();
    public SimpleRegression simpleRegression = new SimpleRegression(true);
    public GaussianCurveFitter fitter = GaussianCurveFitter.create();
    public WeightedObservedPoints obs = new WeightedObservedPoints();

    public HistoricalDataRetrive(){
        ArrayList<String> fileLists = this.getFiles();
        for (String path : fileLists) {
            this.readFile(path);		
        }
        int totalThptsSize = 0;
        for (int cc : thpts.keySet()) {
            List<Double> doubleThpts = thpts.get(cc);

            totalThptsSize += doubleThpts.size();
       }
       double[][] thptCC = new double[totalThptsSize][2];
       totalThptsSize = 0;
       //System.out.println("\n\n\n\n[+] Function called: " + thpts.size() + "\n\n");
       for (int cc : thpts.keySet()) {
           List<Double> doubleThpts = thpts.get(cc);
           //System.out.println("[+] CC: " + cc + " and size: " + doubleThpts.size()+"\n\n");
           for(Double thpt: doubleThpts){
                thptCC[totalThptsSize][1] = cc * 1.0;
                thptCC[totalThptsSize][0] = thpt;
                totalThptsSize += 1;
            }
        }
        // simpleRegression.addData(thptCC);
        //this.curveFitter(thptCC);
        /*
        for(int i = 1; i < 31; i++){
            System.out.println("For cc = " + i + " predicted thpt is " + this.getThptFromCC(i));

        }


        for(int i = 1; i < 61; i++){
            System.out.println("For thpt = " + (i*500.) + " predicted cc is " + this.getCCFromThpt(i*500.));
            
        }//*/
    }
    public double getThptFromCC(int cc){

        double thpt = 0.0;

        try{
            String command = "python3 ./src/main/python/regression.py ";
            String param = " --cc " + cc + " --method curve";
            Process p = Runtime.getRuntime().exec(command + param );
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String ret = in.readLine().trim();
            thpt = Double.parseDouble(ret);
            System.out.println("For cc = " + cc + " the thpt we got is " + thpt);
        }catch(Exception e){
            e.printStackTrace();
        }

        /*
        double m = simpleRegression.getSlope();
        double c = simpleRegression.getIntercept();
        if(m > 0){
            return (cc - c)/m;
        }
        //*/
        return thpt;
    }
    public int getCCFromThpt(double thpt, int max_cc){
        
        int cc = 0;

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "./src/main/python/regression.py",
                "--thpt", "" + (int) thpt,
                "--method", "cube", "--max_cc", ""+max_cc);
            String formatedString = pb.command().toString()
                .replace(",", "")  //remove the commas
                .replace("[", "")  //remove the right bracket
                .replace("]", "")  //remove the left bracket
                .trim();           //remove trailing spaces from partially initialized arrays
            System.out.println("input:" + formatedString);
            Process p = pb.start();
      
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output, line;
            if ((output = in.readLine()) == null) {
              in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
              while(( output = in.readLine()) !=  null){
                System.out.println("Output:" + output);
              }
            }
            while ((line = in.readLine()) != null){ // Ignore intermediate log messages
              output = line;
            }
            System.out.println("Output:: " + output);
            String values = output.trim();
            cc = Integer.parseInt(values);
          } catch(Exception e) {
            System.out.println(e);
            e.printStackTrace();
          }


        /*
        try{
            String command = "python3 ./src/main/python/regression.py ";
            String param = " --cc " + cc + " --method oppoiste_curve";
            Process p = Runtime.getRuntime().exec(command + param );
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String ret = in.readLine();
            while(ret == null){
                Thread.sleep(10);
                ret = in.readLine();
            }
                cc = Integer.parseInt(ret);
                System.out.println("For max thpt = " + thpt + " the cc we got is " + cc);
            
        }catch(Exception e){
            e.printStackTrace();
        }

        double m = simpleRegression.getSlope();
        double c = simpleRegression.getIntercept();
        if(m > 0){
            return (cc - c)/m;
        }
        //*/
        return cc;
    }
    public ArrayList<String> getFiles(){
        ArrayList<String> fileNameList = new ArrayList<String>();
        File f = new File(this.folderDir);
        String[] fileList = f.list();
        if(fileList != null){
            for(int i = 0; i < fileList.length; i++){
                if(!(new File(fileList[i])).isDirectory()){
                    if(fileList[i].contains("inst-throughput")){
                        fileNameList.add(folderDir + "/" + fileList[i]);
                    }
                }
            }
        }
        return fileNameList;
    }
    public void readFile(String fileName){
        try{
            Scanner scanner = new Scanner(new File(fileName));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] splitedData= line.split("	");
                int currTime = Integer.parseInt(splitedData[0]);
                int cc = Integer.parseInt(splitedData[1]);
                double thpt = Double.parseDouble(splitedData[2]);
                if ((currTime > 25) && (thpt > 0.0)){
                    if(!thpts.containsKey(cc)){
                        thpts.put(cc, new ArrayList<Double>());
                    }
                    List<Double> arrayThpts = thpts.get(cc);
                    arrayThpts.add(thpt);
                    thpts.put(cc, arrayThpts);
                }
            }
        }catch(FileNotFoundException e){
            e.printStackTrace();
        }
    }
    public void addNewData(ArrayList<Double> avgThroughput, int cc){
        int j = 0;
        int sizeWithoutZero = 0;
        for (int i = avgThroughput.size() - 1; i > avgThroughput.size() - 4; i--) {
            if(avgThroughput.get(i) > 0.0){
                sizeWithoutZero += 1;
            }
        }
        double[][] tmp = new double[sizeWithoutZero][2];
        for (int i = avgThroughput.size() - 1; i > avgThroughput.size() - 4; i--) {
            if(avgThroughput.get(i) > 0.0){
                tmp[j][0] = avgThroughput.get(i)*1.0;
                tmp[j][1] = cc*1.0;
                obs.add(cc*1.0, avgThroughput.get(i)*1.0);
                j++;
            }
        }
        simpleRegression.addData(tmp);

        /*
        for(int i = 1; i < 31; i++){
            System.out.println("For cc = " + i + " predicted thpt is " + this.getGaussMaxThpt(i));

        }
        */
        //*
        for(int i = 1; i < 31; i++){
            System.out.println("For thpt = " + (i*1000.) + " predicted cc is " + this.getGaussCC(i*1000.));
        }
        //*/
        fitter = GaussianCurveFitter.create();
        double[] bestFit = fitter.fit(obs.toList());
        System.out.println("[+] size of data: "+obs.toList().size() + ", norm: "+
            bestFit[0] + ", mean: " + bestFit[1] + ", sigma: " + bestFit[2]);
    }
    public void curveFitter(double[][] data){
        for (int index = 0; index < data.length; index++) {
            obs.add(data[index][0], data[index][1]);
        }
        double[] bestFit = fitter.fit(obs.toList());
    }
    public int getGaussCC(double thpt){
        fitter = GaussianCurveFitter.create();
        double[] bestFit = fitter.fit(obs.toList());
        double tillVal = 0;
        if((thpt > 0) && (bestFit[0]/thpt > 0)){
            tillVal = -2.0 * Math.log(bestFit[0]/thpt);
        }
        if (tillVal >= 0.){
            tillVal = (Math.sqrt(tillVal) * bestFit[2]) + bestFit[1];
        }else{
            tillVal = 0.;
        }
        return (int)tillVal;
    }

    public double getGaussMaxThpt(int cc){
        fitter = GaussianCurveFitter.create();
        System.out.println("[+] Size of data: "+obs.toList().size());
        double[] bestFit = fitter.fit(obs.toList());
        System.out.println("[+] Size of data: "+obs.toList().size() + ", norm: "+
            bestFit[0] + ", mean: " + bestFit[1] + ", sigma: " + bestFit[2]);
        double tillVal = 0.;
        if (bestFit[2] > 0){
            tillVal = bestFit[0] * Math.exp(-0.5 * Math.pow((cc-bestFit[1])/bestFit[2], 2.0));
        }
        return tillVal;
    }
}