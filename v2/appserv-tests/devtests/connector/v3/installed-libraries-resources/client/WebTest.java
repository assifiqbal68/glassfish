package client;

import java.lang.*;
import java.io.*;
import java.net.*;

import com.sun.ejte.ccl.reporter.*;

public class WebTest {
    private static String TEST_NAME = "installed-libraries";
    static SimpleReporterAdapter stat = new SimpleReporterAdapter("appserv-tests");
    static int count;

    public static void main(String args[]) {

        String host = args[0];
        String portS = args[1];
        String contextRoot = args[2];
        String testSuffix = args[3];
        TEST_NAME = TEST_NAME + testSuffix;

        stat.addDescription(TEST_NAME);

        int port = Integer.valueOf(portS);

        goGet(host, port, "TEST", contextRoot + "/SimpleServlet");
        stat.printSummary(TEST_NAME);
    }

    private static void goGet(String host, int port,
                              String result, String contextPath) {

        try {
            long time = System.currentTimeMillis();
            Socket s = new Socket(host, port);
            s.setSoTimeout(10000);
            OutputStream os = s.getOutputStream();

            contextPath += "?url=" + contextPath;
            System.out.println(("GET " + contextPath + " HTTP/1.1\n"));
            os.write(("GET " + contextPath + " HTTP/1.1\n").getBytes());
            os.write("Host: localhost\n".getBytes());
            os.write("\n".getBytes());

            InputStream is = s.getInputStream();
            System.out.println("Time: " + (System.currentTimeMillis() - time));
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            int index;
            while ((line = bis.readLine()) != null) {
                index = line.indexOf(result);
                System.out.println("[Server response]" + line);

                if (index != -1) {
                    index = line.indexOf(":");
                    String status = line.substring(index + 1);

                    if (status.equalsIgnoreCase("PASS")) {
                        stat.addStatus(TEST_NAME, stat.PASS);
                    } else {
                        stat.addStatus(TEST_NAME, stat.FAIL);
                    }
                }

                int pos = line.indexOf("END_OF_TEST");
                if (pos != -1) {
                    bis.close();
                    is.close();
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            stat.addStatus(TEST_NAME, stat.FAIL);
        }
    }
}
