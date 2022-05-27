package chainz;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ChainzFinderRunnerMain {
    public static Boolean DEBUG = true;
    public static String PROJECT_HOME = "";
    public static String SYSTEM_USER = System.getenv("USER");

    public static String CONFIG_FOLDER = "/ChainzFinder/config";
    public static String ENTRYPOINTS_FILE_PATH = "/ChainzFinder/config/entryExitPoints.txt";
    public static String CHAINZ_FINDER_JAR_PATH = "/ChainzFinder/target/ChainzFinder-1.0-SNAPSHOT-jar-with-dependencies.jar";
    public static String ENTRYPOINTS_STDOUT_DIR_PATH = "/ChainzFinder/out/artifacts/ChainzFinderRunner/entrypoints_stdout";
    public static String targetJarsDirPath = "/ChainzFinder/target_jars";
    public static boolean readObjectOnly;

    public static int MAX_CONCURRENT_CF_PROCESSES;
    public static ExecutorService threadPool;

    public static int maxChainz;
    public static int timeout;

    public class ChainzFinderRunner implements Runnable {
        public String entryExitMaxDepth;
        public String entryExiPointsString;
        public String targetJarPath;
        public String heapMaxSize;
        public Boolean applyClassFilters = true;

        public ChainzFinderRunner(String a, String b, String c, String d, Boolean e, int f, int g) {
            entryExiPointsString = a;
            entryExitMaxDepth = b;
            targetJarPath = c;
            heapMaxSize = d;
            applyClassFilters = e;
            maxChainz = f;
            timeout = g;
        }

        public void run() {
            try {
                String targetJarName = targetJarPath.split("/")[targetJarPath.split("/").length - 1];
                String entryPointClassName = entryExiPointsString.split(":")[0].split("\\.")[entryExiPointsString.split(":")[0].split("\\.").length - 2];
                String entryPointMethodName = entryExiPointsString.split(":")[0].split("\\.")[entryExiPointsString.split(":")[0].split("\\.").length - 1];
                String entryPointSTDOUTFile = PROJECT_HOME + ENTRYPOINTS_STDOUT_DIR_PATH + "/" + targetJarName + "/" + entryPointClassName + "/" + entryPointMethodName + ".stdout";
                // creates the folders for the path jar/class/method that is about to be analyzed.
                new File(entryPointSTDOUTFile).getParentFile().mkdirs();
                // it starts a process that has the ChainzManager as main class, that analyzes and finds the chains for the
                // targetJarPath specified, it writes the output process in entryPointSTDOUTFile (output/JAR/class/method.stout)
                ProcessBuilder pb = new ProcessBuilder(
                        new String[]{"/usr/bin/java",
                                "-Xmx" + heapMaxSize + "g", "-jar", PROJECT_HOME + CHAINZ_FINDER_JAR_PATH,
                                targetJarPath, entryExiPointsString, entryExitMaxDepth,
                                String.valueOf(applyClassFilters), PROJECT_HOME,
                                String.valueOf(maxChainz), String.valueOf(timeout)});
                pb.redirectOutput(new File(entryPointSTDOUTFile));
                //ProcessBuilder pb = new ProcessBuilder(new String[]{"/usr/bin/java", "-Xms16g", "-jar", PROJECT_HOME + CHAINZ_FINDER_JAR_PATH, "java.util.PriorityQueue.readObject:java.lang.reflect.Method.invoke", "7"});
                //ProcessBuilder pb = new ProcessBuilder(new String[]{"/bin/sleep", "5"});
                pb.directory(new File(PROJECT_HOME));
                // This string is printed on the output file where the output has been redirected, otherwise it's printed on the terminal
                System.out.println("= " + targetJarName + " " + entryExiPointsString + " " + entryExitMaxDepth + " " + applyClassFilters + " [ STARTED ]");
                // It starts the process
                Process p = pb.start();
                // Waits the process termination
                while (true) {
                    try {
                        p.exitValue();
                        break;
                    } catch (IllegalThreadStateException e) {
                    }
                }
                System.out.println("= " + targetJarName + " " + entryExiPointsString + " " + entryExitMaxDepth + " " + applyClassFilters + " [ DONE ]");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void addSoftwareLibrary(File file) throws Exception {
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{file.toURI().toURL()});
    }

    public void runnerMain(String[] argv) {

        if (argv.length < 9) {
            System.out.println("[ERROR] Missing arguments:\n\tUsage: java -jar ChainzFinderRunner.jar " +
                    "<MaxConcurrentFinders> " +
                    "<MaxChainDepth> " +
                    "<ProcessHeapMaxSize in GB> " +
                    "<ApplyIntermediateClassesSerializationCheck true/false> " +
                    "<sinkholeMethode including packagename> " +
                    "<projectHome> " +
                    "<MaxChainz> " +
                    "<Timeout Sec (0 for infinite)>" +
                    "<readObjectOnly flag true/false>");
            System.exit(1);
        }

        maxChainz = Integer.parseInt(argv[6]);
        timeout = Integer.parseInt(argv[7]);
        readObjectOnly = Boolean.parseBoolean(argv[8]);

        /* SETTING PROJECT HOME DIRECTORY PATH */
        PROJECT_HOME = argv[5];
        if (DEBUG) {
            System.out.println("\n[i] SYSTEM USER : " + SYSTEM_USER + "\n[i] PROJECT_HOME: " + PROJECT_HOME);
        }

        try {
            String jarFileAbsPath;
            String entryPointMethodString;
            String exitPointMethodString = argv[4];

            String exitclassname = "";
            String exitmethodname;
            Class exitclass = null;

            // In the str variable is saved the exit method name, only the method name without his class.
            String[] str = exitPointMethodString.split("\\.");
            int str_len = str.length;
            exitmethodname = str[str_len - 1];

            // It saves the class name to which the name of the method just saved belongs.
            for (int i = 0; i < str_len - 1; i++) {
                exitclassname += str[i] + ".";
            }

            // It removes the final dot added in the last iteration.
            exitclassname = exitclassname.substring(0, exitclassname.length() - 1);

            // It creates an object with the parameter specified exit point class, found at the previous row.
            try {
                exitclass = Class.forName(exitclassname);
            } catch (Exception e) {
                System.out.println("\n[!] Exit Point Class Not Found!!!");
                System.out.println("\n[!] Class name: " + exitclassname);
                System.exit(1);
            }

            // It searches if exists a method for the exit point class with the name specified as exit point method.
            // If it doesn't exists in the class it means that the specified exit point is invalid.
            // If so the program exits with status 1.
            Boolean methodfound = false;
            for (Method m : exitclass.getDeclaredMethods()) {
                if (m.getName().equals(exitmethodname)) {
                    methodfound = true;
                }
            }
            if (!methodfound) {
                System.out.println("\n[!] Exit Point Method Not Found!!!");
                System.out.println("\n[!] Method name: " + exitmethodname);
                System.exit(1);
            }

            String entryExiPointsString;
            Boolean applyIntermediateClassesSerializationCheck = Boolean.valueOf(argv[3]);
            String heapMaxSize = argv[2];
            String entryExitMaxDepth = argv[1];
            MAX_CONCURRENT_CF_PROCESSES = Integer.valueOf(argv[0]);
            threadPool = Executors.newFixedThreadPool(MAX_CONCURRENT_CF_PROCESSES); //TODO

            if (DEBUG) {
                System.out.println("\n[i] Number of concurrent ChainzFinders : " + MAX_CONCURRENT_CF_PROCESSES);
                System.out.println("[i] Maximum chain depth : " + entryExitMaxDepth);
                System.out.println("[i] Process Max Heap Size : " + heapMaxSize + "GB");
                System.out.println("[i] Apply Class Filter : " + applyIntermediateClassesSerializationCheck);
                System.out.println("[i] Exit Point Method Identification: OK");
            }

            // Creates the config folder and opens a buffer to write on entryExitPoints.txt file.
            new File(PROJECT_HOME + CONFIG_FOLDER).mkdirs();
            BufferedWriter bw = new BufferedWriter(new FileWriter(PROJECT_HOME + ENTRYPOINTS_FILE_PATH, true));
            bw.write("");

            // Saves in the targetJars variable the jar files to analyze contained in the target_jars folder.
            File dir = new File(PROJECT_HOME + targetJarsDirPath);
            String[] targetJars = dir.list();

            if (targetJars.length == 0) {

                System.out.println("[!] The taget jars directory [" + PROJECT_HOME + targetJarsDirPath + "] is empty!!!");
                System.exit(1);

            }

            // For every jar in the target folder
            for (String NthTargetJar : targetJars) {

                try {
                    jarFileAbsPath = PROJECT_HOME + targetJarsDirPath + "/" + NthTargetJar;

                    // Reads the jar file via a JarFile object. In this way it's possible to navigate
                    // the jar content and his properties
                    JarFile jarFile = new JarFile(new File(jarFileAbsPath));
                    System.out.println("\n[i] Analyzing:\n\t- " + jarFile.getName());
                    System.out.println("\n[i] EntryExitPoints Explored:");
                    bw.append("JAR:" + jarFileAbsPath + "\n");

                    URL[] urls = {new URL("jar:file:" + jarFileAbsPath + "!/")};
                    URLClassLoader classLoader = URLClassLoader.newInstance(urls);

                    // Enumeration of files inside the jar analyzed by this iteration.
                    Enumeration<JarEntry> enumOfJar = jarFile.entries();

                    String jarEntryName;

                    String classname;
                    Class<?> classObject;

                    Class<?>[] classInterfaces;
                    Method[] classMethods;

                    // Analyze all the files inside the enumeration.
                    while (enumOfJar.hasMoreElements()) {
                        // Saves in the variable the jar name to analyze in this cycle iteration.
                        jarEntryName = enumOfJar.nextElement().getName();

                        // If the element (file) is a class
                        if (jarEntryName.endsWith(".class") && !jarEntryName.contains("$")) {
                            // If the file (entry) is a class then it saves the class name
                            // (the java class name has . as a delimiter instead of /)
                            // and deletes the last six characters that are the ".class" extension.
                            classname = jarEntryName.replace('/', '.').substring(0, jarEntryName.length() - 6);

                            try {

                                // Tries to load a class in a Java object.
                                classObject = classLoader.loadClass(classname);

                                if (!classObject.isInterface()) {

                                    // If the loaded object is not an interface but a class,
                                    // then saves in a variable the interfaces implemented by that class.
                                    classInterfaces = classObject.getInterfaces();
                                    for (Class<?> c : classInterfaces) {
                                        // Checks for every interface implemented if any of those is Serializable
                                        if (c.getName().equals("java.io.Serializable") || c.getName().equals("Serializable")) {

                                            // If Serializable is implemented, it saves in a variable all the methods
                                            // declared inside the class.
                                            classMethods = classObject.getDeclaredMethods();
                                            for (Method method : classMethods) {

                                                if(readObjectOnly && !method.getName().equals("readObject")) {
                                                    continue;
                                                }

                                                // It saves the complete name of the class (the full path from packages
                                                // to the class) plus the name of the serializable method found.
                                                entryPointMethodString = classname + "." + method.getName();
                                                // It creates a string with the class + serializable method found, followed
                                                // by the exit method (class + method) that is a program input argument.
                                                entryExiPointsString = entryPointMethodString + ":" + exitPointMethodString;
                                                // Writes on file entryExitPoints.txt that in the jar analyzed has been found
                                                // the serializable method : exit method. This has been found in an
                                                // analysis with that maximum depth level.
                                                bw.append(NthTargetJar + " => " + entryExiPointsString + " " + entryExitMaxDepth + "\n");

                                                // Creates a new thread with the following parameters:
                                                // the string created above with the serializable method + exit method,
                                                // the jar path used in this iteration + method just analyzed.
                                                threadPool.execute(
                                                        new ChainzFinderRunner(
                                                                entryExiPointsString,
                                                                entryExitMaxDepth,
                                                                jarFileAbsPath,
                                                                heapMaxSize,
                                                                applyIntermediateClassesSerializationCheck,
                                                                maxChainz,
                                                                timeout
                                                        )
                                                );

                                            }
                                        }
                                    }
                                }
                            } catch (java.lang.NoClassDefFoundError e) {
                                // If the class not exists throws an exception
                            	System.err.println("No class found exception");
			    }catch(Throwable t){
				System.err.println(t.getMessage());
				System.err.println("Error getting jar loop");
			    }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // After all the classes and subclasses serializables the execution ends.
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }

            bw.close();
        } catch (Exception e) {
		System.err.println("Exception during jar loop");
            e.printStackTrace();
        }

    }

    public static void main(String[] argv) {
        new ChainzFinderRunnerMain().runnerMain(argv);
    }


}
