package chainz;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import soot.*;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import static chainz.ChainzManager.*;

public class ChainzExplorer {

    String entryExitCouple;
    String targetJarName;
    int maxChainDepth;
    Boolean applyClassFilter;

    String entryPoint;
    String exitPoint;

    String entryPointPackageName;
    String entryPointClassName;
    String entryPointMethodName;

    String exitPointPackageName;
    String exitPointClassName;
    String exitPointMethodName;

    File file;
    BufferedWriter bw;

    LocalDateTime timestart = null;
    int timeout;

    int maxChainz;

    ArrayList<String> chainzStrings = new ArrayList<String>();
    CallGraph methodsCallGraph = null;

    public ChainzExplorer(String targetJarPath, String entryexitcouple, int maxchaindepth, Boolean classFilterFlag, int maxChainz, int timeout) {
        int l = targetJarPath.split("/").length;
        // Saves the jar name, that is the jar last part
        targetJarName = targetJarPath.equals("") ? "openJDK/" : targetJarPath.split("/")[l - 1] + "/";
        entryExitCouple = entryexitcouple;
        maxChainDepth = maxchaindepth;
        applyClassFilter = classFilterFlag;

        /* SETTING ENTRY AND EXIT POINTS */
        entryPoint = entryExitCouple.split(":")[0];
        exitPoint = entryExitCouple.split(":")[1];

        /* Setting time-out */
        this.timeout = timeout;

        String[] tmp;
        tmp = entryPoint.split("\\.");
        entryPointPackageName = "";
        for (int i = 0; i < tmp.length - 2; i++) {
            entryPointPackageName += i == 0 ? tmp[i] : "." + tmp[i];
        }
        entryPointClassName = tmp[tmp.length - 2];
        entryPointMethodName = tmp[tmp.length - 1];

        // saves in the variable the exit point package (package is exitPoint - class - method)
        tmp = exitPoint.split("\\.");
        exitPointPackageName = "";
        for (int i = 0; i < tmp.length - 2; i++) {
            exitPointPackageName += i == 0 ? tmp[i] : "." + tmp[i];
        }
        // Saves the exit point class (penultimate element of the exitpoint string)
        exitPointClassName = tmp[tmp.length - 2];
        ;
        // Saves the exit point method (last element of the exitpoint string)
        exitPointMethodName = tmp[tmp.length - 1];
        /*=============================*/

        /* SETTING CHAINZ OUTPUT FILE */
        try {
            // It creates a file where all the chains found will be written, the file is created at the following path:
            // /jchainz/ChainzFinder/output/chains/JARNAME.jar
            // Name of example file: AbstractSerializableCollectionDecorator.readObject.10.chains
            file = new File(PROJECT_HOME + chainsDirectoryPath + targetJarName + entryPointClassName + "." + entryPointMethodName + "." + maxChainDepth + "." + "chains");
            file.getParentFile().mkdirs();
            bw = new BufferedWriter(new FileWriter(file, true));
            // Writes inside the file the first row containing the target jar from the analysis
            // (example: commons-collections-3.1.jar/)
            bw.append(this.targetJarName + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*=============================*/


        this.maxChainz = maxChainz;
    }

    public void printConfig() {
        String s = "===== ChainzExplorer =====\n";
        s += "\t- PROJECT_HOME: [" + PROJECT_HOME + "]\n";
        s += "\t- classpath: [" + classpath + "]\n";
        s += "\t- chainsDirectoryPath: [" + chainsDirectoryPath + "]\n";
        s += "\t- DEBUG: [" + DEBUG + "]\n";
        s += "..........................\n";
        s += "\t- entryExitCouple: [" + entryExitCouple + "]\n";
        s += "\t- maxChainDepth: [" + maxChainDepth + "]\n";
        s += "\t- entryPointPackageName: [" + entryPointPackageName + "]\n";
        s += "\t- entryPointClassName: [" + entryPointClassName + "]\n";
        s += "\t- entryPointMethodName: [" + entryPointMethodName + "]\n";
        s += "\t- exitPointPackageName: [" + exitPointPackageName + "]\n";
        s += "\t- exitPointClassName: [" + exitPointClassName + "]\n";
        s += "\t- exitPointMethodName: [" + exitPointMethodName + "]\n";
        s += "==========================";
        System.out.println("\n" + s);
        System.out.flush();
    }

    private String getDeltaTimeString(long start, long end) {
        long deltaTime = end - start;
        long deltaMillis, deltaSeconds, deltaMinutes, deltaHours, deltaDays;

        deltaMillis = TimeUnit.MILLISECONDS.toMillis(deltaTime) % 1000;
        deltaSeconds = TimeUnit.MILLISECONDS.toSeconds(deltaTime) % 60;
        deltaMinutes = TimeUnit.MILLISECONDS.toMinutes(deltaTime) % 60;
        deltaHours = TimeUnit.MILLISECONDS.toHours(deltaTime) % 24;
        deltaDays = TimeUnit.MILLISECONDS.toDays(deltaTime);

        return deltaDays + ":" + deltaHours + ":" + deltaMinutes + ":" + deltaSeconds + ":" + deltaMillis;
    }

    private static String methodToString(SootMethod method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    private static String methodToStringOut(SootMethod method) {
        return method.getDeclaringClass() + ": " + method.getSubSignature();
    }

    private void visit(int currentChainDepth, String currentMethodPath, String currentMethodPathOut, SootMethod edgeSourceMethod) {

        // If the exit point is reached the execution is terminated and returns from this method.
        if (currentMethodPath.endsWith(exitPointClassName + "." + exitPointMethodName)) {
            return;
        }

        Iterator<Edge> edgeIterator = methodsCallGraph.edgesOutOf(edgeSourceMethod);
        SootMethod edgeTargetMethod;
        SootClass edgeTargetMethodClass;
        String currentNewChain, currentNewChainOut;

        while (edgeIterator.hasNext()) {

            // Checks if the execution timeout has been reached, if so it terminates the execution.
            if (!canContinueTimeout())
                System.exit(123);

            edgeTargetMethod = edgeIterator.next().tgt().method();
            edgeTargetMethodClass = edgeTargetMethod.method().getDeclaringClass();

            if (applyClassFilter) {
                Type serializableType = Scene.v().getType("java.io.Serializable");
                // Checks if the class of the method found (the next one in the call graph) is serializable
                boolean targetImplementsSerializable = Scene.v().getFastHierarchy().canStoreType(edgeTargetMethod.getDeclaringClass().getType(), serializableType);
                // Checks if the class of the edge method found is superclass of the source method.
                boolean targetIsSuperclassOfSource = Scene.v().getFastHierarchy().canStoreType(edgeSourceMethod.getDeclaringClass().getType(), edgeTargetMethod.getDeclaringClass().getType());
                // Checks if the target edge method found is the same exit point defined as input parameter.
                boolean targetIsExitPoint = exitPointMethodName.equals(edgeTargetMethod.getName());
                // If the method is not the exit point and the target method class is not serializable and it's not
                // a superclass of the source method then stops this iteration and finds the next edge
                // (in the next while iteration)
                if (!targetIsExitPoint && !targetImplementsSerializable && !targetIsSuperclassOfSource) {
                    //System.out.println("NO ARC " + edgeSourceMethod.getDeclaringClass().getType() +" - "+ edgeTargetMethod.getDeclaringClass().getType());
                    continue;
                }

                //System.out.println("CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                // Added controls to check the Hashtable CommonsCollections7 chain
                SootClass hashTableClass = Scene.v().getSootClass("java.util.Hashtable");
                SootMethod reconstitutionPutMethod = hashTableClass.getMethodByName("reconstitutionPut");
                if(currentChainDepth == 1 && !edgeSourceMethod.equals(reconstitutionPutMethod)) { // edgeTargetMethod
                    continue;
                }
                if(currentChainDepth == 1) System.out.println("STEP1 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                /*SootClass AbstractMapDecoratorClass = Scene.v().getSootClass("org.apache.commons.collections.map.AbstractMapDecorator");
                SootMethod amdEqualsMethod = AbstractMapDecoratorClass.getMethodByName("equals");
                if(currentChainDepth == 2 && !edgeSourceMethod.equals(amdEqualsMethod)) {
                    continue;
                }
                if(currentChainDepth == 2) System.out.println("STEP2 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                */SootClass AbstractMapClass = Scene.v().getSootClass("java.util.AbstractMap$SimpleEntry");
                SootMethod amEqualsMethod = AbstractMapClass.getMethodByName("equals");
                if(currentChainDepth == 2 && !edgeSourceMethod.equals(amEqualsMethod)) {
                    continue;
                }
                if(currentChainDepth == 2) System.out.println("STEP3 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass lazyMapClass = Scene.v().getSootClass("org.apache.commons.collections.map.LazyMap");
                SootMethod lmGetMethod = lazyMapClass.getMethodByName("get");
                if(currentChainDepth == 3 && !edgeSourceMethod.equals(lmGetMethod)) {
                    continue;
                }
                if(currentChainDepth == 3) System.out.println("STEP4 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass chainedTransformerClass = Scene.v().getSootClass("org.apache.commons.collections.functors.ChainedTransformer");
                SootMethod ctTransformMethod = chainedTransformerClass.getMethodByName("transform");
                if(currentChainDepth == 4 && !edgeSourceMethod.equals(ctTransformMethod)) {
                    continue;
                }
                if(currentChainDepth == 4) System.out.println("STEP5 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass invokerTransformerClass = Scene.v().getSootClass("org.apache.commons.collections.functors.InvokerTransformer");
                SootMethod itTransformMethod = invokerTransformerClass.getMethodByName("transform");
                if(currentChainDepth == 5 && !edgeSourceMethod.equals(itTransformMethod)) {
                    continue;
                }
                if(currentChainDepth == 5) System.out.println("STEP6 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass methodClass = Scene.v().getSootClass("java.lang.reflect.Method");
                SootMethod mInvokeMethod = methodClass.getMethodByName("invoke");
                if(currentChainDepth == 5 && !edgeTargetMethod.equals(mInvokeMethod)) {
                    continue;
                }
                if(currentChainDepth == 5 && edgeTargetMethod.equals(mInvokeMethod)) System.out.println("STEP7 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);


                /*
                // Added controls to check the Hashtable CommonsCollections8 chain
                SootClass step1Class = Scene.v().getSootClass("org.apache.commons.collections4.bag.AbstractMapBag");
                SootMethod step1Method = step1Class.getMethodByName("doReadObject");
                if(currentChainDepth == 1 && !edgeSourceMethod.equals(step1Method)) { // edgeTargetMethod
                    continue;
                }
                if(currentChainDepth == 1) System.out.println("STEP1 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step2Class = Scene.v().getSootClass("java.util.TreeMap");
                SootMethod step2Method = step2Class.getMethodByName("put");
                if(currentChainDepth == 2 && !edgeSourceMethod.equals(step2Method)) {
                    continue;
                }
                if(currentChainDepth == 2) System.out.println("STEP2 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step3Class = Scene.v().getSootClass("java.util.TreeMap");
                SootMethod step3Method = step3Class.getMethodByName("compare");
                if(currentChainDepth == 3 && !edgeSourceMethod.equals(step3Method)) {
                    continue;
                }
                if(currentChainDepth == 3) System.out.println("STEP3 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step4Class = Scene.v().getSootClass("org.apache.commons.collections4.comparators.TransformingComparator");
                SootMethod step4Method = step4Class.getMethodByName("compare");
                if(currentChainDepth == 4 && !edgeSourceMethod.equals(step4Method)) {
                    continue;
                }
                if(currentChainDepth == 4) System.out.println("STEP4 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step5Class = Scene.v().getSootClass("org.apache.commons.collections4.functors.InvokerTransformer");
                SootMethod step5Method = step5Class.getMethodByName("transform");
                if(currentChainDepth == 5 && !edgeSourceMethod.equals(step5Method)) {
                    continue;
                }
                if(currentChainDepth == 5) System.out.println("STEP5 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step6Class = Scene.v().getSootClass("java.lang.reflect.Method");
                SootMethod step6Method = step6Class.getMethodByName("invoke");
                if(currentChainDepth == 5 && !edgeTargetMethod.equals(step6Method)) {
                    continue;
                }
                if(currentChainDepth == 5 && edgeTargetMethod.equals(step6Method)) System.out.println("STEP6 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                /*SootClass step7Class = Scene.v().getSootClass("java.lang.reflect.Method");
                SootMethod step7Method = step7Class.getMethodByName("invoke");
                if(currentChainDepth == 7 && !edgeSourceMethod.equals(step7Method)) {
                    continue;
                }
                if(currentChainDepth == 7) System.out.println("STEP7 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                */


                // Added controls to check the Hashtable CommonsCollections10 chain
                /*SootClass step1Class = Scene.v().getSootClass("org.apache.commons.collections4.map.LRUMap");
                SootMethod step1Method = step1Class.getMethodByName("doReadObject");
                if(currentChainDepth == 1 && !edgeSourceMethod.equals(step1Method)) { // edgeTargetMethod
                    continue;
                }
                if(currentChainDepth == 1) System.out.println("STEP1 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step2Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractHashedMap");
                SootMethod step2Method = step2Class.getMethodByName("doReadObject");
                if(currentChainDepth == 2 && !edgeSourceMethod.equals(step2Method)) {
                    continue;
                }
                if(currentChainDepth == 2) System.out.println("STEP2 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step3Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractHashedMap");
                SootMethod step3Method = step3Class.getMethodByName("put");
                if(currentChainDepth == 3 && !edgeSourceMethod.equals(step3Method)) {
                    continue;
                }
                if(currentChainDepth == 3) System.out.println("STEP3 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step4Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractHashedMap");
                SootMethod step4Method = step4Class.getMethodByName("isEqualKey");
                if(currentChainDepth == 4 && !edgeSourceMethod.equals(step4Method)) {
                    continue;
                }
                if(currentChainDepth == 4) System.out.println("STEP4 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step5Class = Scene.v().getSootClass("java.util.Hashtable");
                SootMethod step5Method = step5Class.getMethodByName("equals");
                if(currentChainDepth == 5 && !edgeSourceMethod.equals(step5Method)) {
                    continue;
                }
                if(currentChainDepth == 5) System.out.println("STEP5 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step6Class = Scene.v().getSootClass("org.apache.commons.collections4.map.DefaultedMap");
                SootMethod step6Method = step6Class.getMethodByName("get");
                if(currentChainDepth == 6 && !edgeSourceMethod.equals(step6Method)) {
                    continue;
                }
                if(currentChainDepth == 6) System.out.println("STEP6 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step7Class = Scene.v().getSootClass("org.apache.commons.collections4.functors.InvokerTransformer");
                SootMethod step7Method = step7Class.getMethodByName("transform");
                if(currentChainDepth == 7 && !edgeSourceMethod.equals(step7Method)) {
                    continue;
                }
                if(currentChainDepth == 7) System.out.println("STEP7 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step8Class = Scene.v().getSootClass("java.lang.reflect.Method");
                SootMethod step8Method = step8Class.getMethodByName("invoke");
                if(currentChainDepth == 7 && !edgeTargetMethod.equals(step8Method)) {
                    continue;
                }
                if(currentChainDepth == 7 && edgeTargetMethod.equals(step8Method)) System.out.println("STEP8 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
*/
                // Added controls to check the Hashtable CommonsCollections11 chain
               /* SootClass step1Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractReferenceMap");
                SootMethod step1Method = step1Class.getMethodByName("doReadObject");
                if(currentChainDepth == 1 && !edgeSourceMethod.equals(step1Method)) { // edgeTargetMethod
                    continue;
                }
                if(currentChainDepth == 1) System.out.println("STEP1 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step2Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractReferenceMap");
                SootMethod step2Method = step2Class.getMethodByName("put");
                if(currentChainDepth == 2 && !edgeSourceMethod.equals(step2Method)) {
                    continue;
                }
                if(currentChainDepth == 2) System.out.println("STEP2 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step3Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractHashedMap");
                SootMethod step3Method = step3Class.getMethodByName("put");
                if(currentChainDepth == 3 && !edgeSourceMethod.equals(step3Method)) {
                    continue;
                }
                if(currentChainDepth == 3) System.out.println("STEP3 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step4Class = Scene.v().getSootClass("org.apache.commons.collections4.map.AbstractHashedMap");
                SootMethod step4Method = step4Class.getMethodByName("isEqualKey");
                if(currentChainDepth == 4 && !edgeSourceMethod.equals(step4Method)) {
                    continue;
                }
                if(currentChainDepth == 4) System.out.println("STEP4 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step5Class = Scene.v().getSootClass("java.util.AbstractMap");
                SootMethod step5Method = step5Class.getMethodByName("equals");
                if(currentChainDepth == 5 && !edgeSourceMethod.equals(step5Method)) {
                    continue;
                }
                if(currentChainDepth == 5) System.out.println("STEP5 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                /*SootClass step6Class = Scene.v().getSootClass("org.apache.commons.collections4.map.DefaultedMap");
                SootMethod step6Method = step6Class.getMethodByName("get");
                if(currentChainDepth == 6 && !edgeSourceMethod.equals(step6Method)) {
                    continue;
                }
                if(currentChainDepth == 6) System.out.println("STEP6 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step7Class = Scene.v().getSootClass("org.apache.commons.collections4.functors.InvokerTransformer");
                SootMethod step7Method = step7Class.getMethodByName("transform");
                if(currentChainDepth == 7 && !edgeSourceMethod.equals(step7Method)) {
                    continue;
                }
                if(currentChainDepth == 7) System.out.println("STEP7 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
                SootClass step8Class = Scene.v().getSootClass("java.lang.reflect.Method");
                SootMethod step8Method = step8Class.getMethodByName("invoke");
                if(currentChainDepth == 7 && !edgeTargetMethod.equals(step8Method)) {
                    continue;
                }
                if(currentChainDepth == 7 && edgeTargetMethod.equals(step8Method)) System.out.println("STEP8 DONE" + ". CurrentChainDepth: " + currentChainDepth + "  EdgeSourceMethod: " + edgeSourceMethod + "  EdgeTargetMethod: " + edgeTargetMethod);
*/
            }

            if (
                    currentChainDepth < maxChainDepth &&
                            !methodToString(edgeTargetMethod).contains("<clinit>") &&
                            !currentMethodPath.contains(methodToString(edgeTargetMethod)) //if not a loop
            ) {

                //currentNewChain = currentMethodPath + " ==> " + methodToString(edgeTargetMethod) + edgeTargetMethod.getParameterTypes();
                // It saves in the variables the links between class.method currently analyzed and the class.method target (called)
                currentNewChain = currentMethodPath + " ==> " + methodToString(edgeTargetMethod);
                currentNewChainOut = currentMethodPathOut + " ==> " + methodToStringOut(edgeTargetMethod);


                // If this variable with the link class.method ==> targetclass.targetmethod contains the exit point
                // (probably in the target) and if the chains found until now does not contains exactly this new chain
                // (redundancy) so the program executes this if code block where this new chain will be added to the
                // ones already found.
                if (currentNewChain.contains(exitPointClassName + "." + exitPointMethodName) && !chainzStrings.contains(currentNewChain)) {


                    // If the maximum depth is reached then the execution is terminated, else the remaining depth is decreased.
                    if (maxChainz <= 0) {
                        System.exit(123);
                    } else {

                        //System.out.println(maxChainz + " Stop");
                        maxChainz--;
                    }
                    // Prints the chain just found on entrypoints_stdout/JAR/CLASS/METHOD.stdout file.
                    if (DEBUG) {
                        System.out.println("\t- " + currentNewChainOut);
                        System.out.flush();
                    }

                    try {

                        //System.out.println("\nMAX CHAINZ - > "+maxChainz);
                        // Adds the chain just found to the file containing all the chains for
                        // the current JAR/class/method: JAR/CLASS.METHOD.maxDepth.chains
                        // This is the entire chain. That's because if the execution has not terminated yet and it has
                        // reached this point, it means that the exit point has been found.
                        bw.append(currentNewChainOut + "\n");
                        bw.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // It adds the string just found to the chains found until now
                    chainzStrings.add(currentNewChain);

                }

                // The method calls itself recursively incrementing the depth by one, and using as new parameters
                // the chains and methods just found.
                visit(currentChainDepth + 1, currentNewChain, currentNewChainOut, edgeTargetMethod);
            }

        }

    }

    public void getChainz() {

        /* ADDING A CUSTOM TRANSFORMATION TO THE SOOT ANALYSIS PIPELINE */
        PackManager.v().getPack("wjtp").add(
                new Transform(
                        "wjtp." + entryPointClassName + "." + maxChainDepth,
                        new SceneTransformer() {
                            @Override
                            protected void internalTransform(String phaseName, Map options) {
                                System.out.println(entryPointClassName + "." + maxChainDepth);

                                long start;
                                long end = System.currentTimeMillis();
                                String deltaTimeCGCreation = getDeltaTimeString(CGCreationStart, end);

                                CHATransformer.v().transform();

                                // Reminder: all the printlns print at the following path:
                                // ChainzFinder/out/artifacts/ChainzFinderRunner/entrypoints_stdout/JAR/CLASS/METHOD.stdout
                                // Example path:
                                // ChainzFinder/out/artifacts/ChainzFinderRunner/entrypoints_stdout/commons-collections-3.1.jar/DualHashBidiMap/readObject.stdout
                                if (DEBUG) {
                                    System.out.println("\n************************ STARTED ************************");
                                    System.out.flush();

                                    //printConfig();

                                    System.out.println("\n[i] ENTRY AND EXIT POINTS: ");
                                    System.out.println("\t- ENTRY POINT: " + entryPoint + "\n\t- EXIT  POINT: " + exitPoint);
                                    System.out.flush();
                                }

                                /* CREATING METHODS CALL GRAPH */
                                methodsCallGraph = Scene.v().getCallGraph();

                                if (DEBUG) {
                                    System.out.println("\n[+] CREATED METHODS CALL GRAPH \n\t- IN [DD:HH:MM:SS:MM]: " + deltaTimeCGCreation + "\n\t- Graph Size: " + methodsCallGraph.size() + " Edges\n\t- Graph Generation Entry Point: " + Scene.v().getEntryPoints().get(0));
                                    System.out.flush();
                                }
                                /*=============================*/


                                /* VISITING THE METHODS CALL GRAPH */
                                if (DEBUG) {
                                    System.out.println("\n[i] STARTING CALL GRAPH ANALYSIS " + "@ MAX DEPTH [" + maxChainDepth + "]");
                                    System.out.flush();
                                }

                                // Saves the class and the entry point method in the Soot objects.
                                SootClass entryPointClass = Scene.v().getSootClass(entryPointPackageName + "." + entryPointClassName);
                                SootMethod entryPointMethod = entryPointClass.getMethodByName(entryPointMethodName);

                                Set<String> chains = new HashSet<String>();

                                if (DEBUG) {
                                    System.out.println("\n[+] CHAINS FOUND");
                                    System.out.flush();
                                }

                                try {
                                    bw.write("CLASSPATH:" + classpath + "\n");
                                    bw.flush();

                                    start = System.currentTimeMillis();
                                    if (timestart == null)
                                        timestart = getCurrentTimestamp();
                                    // Calls the visit method so that all the chain is visited until the max depth
                                    // is reached, starting from the entry point.
                                    visit(0, methodToString(entryPointMethod), methodToStringOut(entryPointMethod), entryPointMethod);
                                    end = System.currentTimeMillis();

                                    bw.append("FINISHED\n");
                                    bw.flush();
                                    bw.close();

                                    if (DEBUG) {
                                        System.out.println("\n[+] EXPLORED METHODS CALL GRAPH IN [DD:HH:MM:SS:MM]: " + getDeltaTimeString(start, end));
                                        System.out.println("\n************************* ENDED *************************");
                                        System.out.flush();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                /*=============================*/

                            }
                        }
                )
        );

        // It loads the entry point class in a SootClass object and loads all the necessary classes linked to that one.
        SootClass c = Scene.v().forceResolve(entryPointPackageName + "." + entryPointClassName, SootClass.BODIES);
        c.setApplicationClass();
        Scene.v().loadNecessaryClasses();
        // Loads the entry poiny method of the above class in a SootMethod object
        SootMethod method = c.getMethodByName(entryPointMethodName);
        List entryPoints = new ArrayList();
        // Adds this entry poiny method to a list that at the moment contains only this element.
        entryPoints.add(method);
        // Sets the Scene entryPoints as the list just created.
        Scene.v().setEntryPoints(entryPoints);

    }

    private LocalDateTime getCurrentTimestamp() {
        return (LocalDateTime.now());
    }

    private long getDifferenceSecond() {
        return (timestart.until(getCurrentTimestamp(), ChronoUnit.SECONDS));
    }

    private long getDifferenceMinute() {
        return (timestart.until(getCurrentTimestamp(), ChronoUnit.MINUTES));
    }

    private boolean canContinueTimeout() {

        if (timeout <= 0)
            return true;

        //Return true if current timestamp is less then timeout time
        return (getDifferenceSecond() <= timeout);
    }

}
