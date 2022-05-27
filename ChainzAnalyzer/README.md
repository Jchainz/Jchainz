# How to run ChainzAnalyzer

How to run on single chain:

1. Edit `config.properties` file to set paths for project and JARs
1. Build with `mvn clean install`
1. cd into `target/`
1. `java -jar chains-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar -c 'org.apache.commons.collections4.map.UnmodifiableMap: void readObject(java.io.ObjectInputStream) ==> java.io.ObjectInputStream: void defaultReadObject() ==> java.io.ObjectInputStream: void defaultReadFields(java.lang.Object,java.io.ObjectStreamClass) ==> java.io.ObjectInputStream: java.lang.Object readObject0(boolean) ==> java.io.ObjectInputStream: java.lang.Object readOrdinaryObject(boolean) ==> java.io.ObjectStreamClass: java.lang.Object invokeReadResolve(java.lang.Object) ==> java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])'`

Run on all chains with runner:

  **Set your absolute path to jchainz directory. If you have jchainz under /home no changes need to be made meaning**

`$ nohup bash -c 'time /home/jchainz/ChainzAnalyzer/runner.py /home/jchainz/ChainzFinder/output/chains/' > /home/ChainzAnalyzerOutput_DATE.txt &`
