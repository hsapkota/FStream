clear;
cd ..;
~/Downloads/apache-maven-3.8.1/bin/mvn -X compile;
cd AdaptiveGridFTPClient;
~/Downloads/apache-maven-3.8.1/bin/mvn -X compile;

~/Downloads/apache-maven-3.8.1/bin/mvn exec:java;
#tail -f ./print.log;




