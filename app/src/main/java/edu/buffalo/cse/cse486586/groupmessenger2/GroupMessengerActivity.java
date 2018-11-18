package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final String[] REMOTE_PORTS = {REMOTE_PORT0,REMOTE_PORT1,REMOTE_PORT2,REMOTE_PORT3,REMOTE_PORT4};
    static final int SERVER_PORT = 10000;

    private HashMap<String, Integer> sequenceVector = new HashMap<String, Integer>();
    //private Hashtable<String, String> holdBackQueueTable = new Hashtable<String, String>();
    //private Hashtable<String, Long> elapsedTimeStartTable = new Hashtable<String, Long>();
    private HashMap<String, String[]> totalOrderMsgQueue = new HashMap<String, String[]>();
    private HashMap<String, HashMap<String, Integer>> proposalReceived = new HashMap<String, HashMap<String, Integer>>();
    //private HashMap<Long, String> sortingTotalOrderQueue = new HashMap<Long, String>();
    private String whatsMyPort = "";
    private int sequenceNumber = 0;
    private int proposalCounter = 0;
    private int messageCounter = 0;
    private int milliSecInterval = 500;
    private long timeDelay = 1000;
    //private Handler responseHandler;


    //ref: https://www.programcreek.com/java-api-examples/?class=android.net.Uri&method=Builder
    //ref: http://www.codexpedia.com/java/building-url-using-uri-in-android/
    //ref: https://stackoverflow.com/questions/19167954/use-uri-builder-in-android-or-create-url-with-variables
    private Uri buildUri(String scheme, String authority){
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme(scheme);
        uriBuilder.authority(authority);
        return uriBuilder.build();
    }

    Uri contentUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*responseHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append((String)msg.obj + "\t\n");
            }
        };*/

        for(String remotePort: REMOTE_PORTS){
            sequenceVector.put(remotePort, 0);
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        whatsMyPort = myPort;

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * Retrieve a pointer to the input box (EditText) defined in the layout
         * XML file (res/layout/main.xml).
         *
         * This is another example of R class variables. R.id.edit_text refers to the EditText UI
         * element declared in res/layout/main.xml. The id of "edit_text" is given in that file by
         * the use of "android:id="@+id/edit_text""
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button sendButton = (Button) findViewById(R.id.button4);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText(""); // This is one way to reset the input box.
                //TextView localTextView = (TextView) findViewById(R.id.textView1);
                //localTextView.append("\t" + msg); // This is one way to display a string.
                //TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
                //remoteTextView.append("\n");

                    /*
                     * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                     * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                     * the difference, please take a look at
                     * http://developer.android.com/reference/android/os/AsyncTask.html
                     */
                if (msg.equals("qr:ppsks")) {
                    System.out.println(proposalReceived.keySet());
                } else if (msg.equals("qr:ppsvl")) {
                    int tempCounter = 0;
                    for (Map.Entry<String, HashMap<String,Integer>> entry1 : proposalReceived.entrySet()) {
                        for (Map.Entry<String,Integer> entry2 : entry1.getValue().entrySet()) {
                            tempCounter += 1;
                            String msgOut = tempCounter + " => " + entry1.getKey() + " : " + entry2.getKey() + " : " + Integer.toString(entry2.getValue());
                            System.out.println(msgOut);
                        }
                    }
                }else if (msg.equals("qr:tmqks")) {
                    System.out.println(totalOrderMsgQueue.keySet());
                }else if (msg.equals("qr:tmqvl")) {
                    int tempCounter = 0;
                    for (Map.Entry<String, String[]> entry : totalOrderMsgQueue.entrySet()) {
                        tempCounter += 1;
                        System.out.println(tempCounter + " => " + entry.getKey() + " : " + Arrays.toString(entry.getValue()));
                    }
                } else if (!msg.isEmpty()) {
                    messageCounter += 1;
                    for(final String remotePort: REMOTE_PORTS){
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Msg", msg, remotePort);
                    }

                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

            Socket socket = null;
            while(true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                BufferedReader in = null;
                PrintWriter out = null;
                try {
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String inStr;
                String outStr = "";
                try {
                    inStr = in.readLine();
                    if(inStr != null) {
                        String[] st = inStr.split("\\t");
                        if (st[0].trim().equals("Msg")) {
                            proposalCounter += 1;
                            String currentProposalCounter = Integer.toString(proposalCounter);
                            String messageID = st[1].trim();
                            String msgFrom = st[2].trim();
                            String msgReceived = st[3].trim();
                            int proposedPortNum = Collections.indexOfSubList(Arrays.asList(REMOTE_PORTS), Arrays.asList(whatsMyPort));
                            totalOrderMsgQueue.put(msgFrom + messageID, new String[]{currentProposalCounter, Integer.toString(proposedPortNum), msgReceived, "0"});
                            //Log.v("Server-Msg-From", msgFrom + "\t" + messageID + "\t" + Arrays.toString(totalOrderMsgQueue.get(msgFrom + messageID)));
                            outStr = whatsMyPort + "\t" + messageID + "\t" + currentProposalCounter;
                        } else if (st[0].trim().equals("Agr")) {
                            String msgFrom = st[1].trim();
                            String messageID = st[2].trim();
                            String proposedPort = st[3].trim();
                            String proposedValue = st[4].trim();
                            if (totalOrderMsgQueue.containsKey(msgFrom + messageID) && proposedValue.equals("0")) {
                                //Log.v("Server-Agr-Failed", totalOrderMsgQueue.get(msgFrom + messageID).toString());
                                totalOrderMsgQueue.remove(msgFrom + messageID);
                            } else if (totalOrderMsgQueue.containsKey(msgFrom + messageID)) {
                                int proposedPortNum = Collections.indexOfSubList(Arrays.asList(REMOTE_PORTS), Arrays.asList(proposedPort));
                                totalOrderMsgQueue.get(msgFrom + messageID)[0] = proposedValue;
                                totalOrderMsgQueue.get(msgFrom + messageID)[1] = Integer.toString(proposedPortNum);
                                totalOrderMsgQueue.get(msgFrom + messageID)[3] = "1";

                                //Log.v("Server-Agr-Success", msgFrom + messageID);

                                outStr = "Agreement Success";
                                publishProgress("Dlv");
                            }
                        } else if (st[0].trim().equals("Alv")) {
                            outStr = "Node is Alive";
                        }

                        out.println(outStr);
                        socket.close();

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            if (strings[0].equals("Dlv")) {

                Boolean deliverable = Boolean.TRUE;

                try {
                    TimeUnit.MILLISECONDS.sleep(timeDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    while (deliverable) {
                        Map.Entry<String, String[]> minEntry = null;
                        for (Map.Entry<String, String[]> entry : totalOrderMsgQueue.entrySet()) {
                            if (minEntry == null || Integer.parseInt(minEntry.getValue()[0]) > Integer.parseInt(entry.getValue()[0])) {
                                minEntry = entry;
                            } else if (Integer.parseInt(minEntry.getValue()[0]) == Integer.parseInt(entry.getValue()[0])) {
                                if (!minEntry.getValue()[3].equals("1") || !entry.getValue()[3].equals("1")) {
                                    minEntry = null;
                                    //Log.v("Server-Agr-not1", "???");
                                    break;
                                } else if (Integer.parseInt(minEntry.getValue()[1]) > Integer.parseInt(entry.getValue()[1])) {
                                    minEntry = entry;
                                    //Log.v("Server-Agr-is1", minEntry.getKey());
                                }

                            }
                        }

                        if (totalOrderMsgQueue.size() >= 2) {
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Alv", minEntry.getKey().substring(0,5), minEntry.getKey().substring(5));
                        }

                        //Log.v("Testing", minEntry.getKey().substring(0,5) + " : " + minEntry.getKey().substring(5));
                        //Log.v("Server-Agr-Keyset", totalOrderMsgQueue.keySet().toString());
                        //Log.v("Server-Agr-minEntry", minEntry.getKey() + " => " +Arrays.toString(minEntry.getValue()));
                        if (minEntry == null || minEntry.getValue()[3].equals("0")) {
                            deliverable = Boolean.FALSE;
                        } else if (minEntry.getValue()[3].equals("1")) {
                            //Log.v("Server-holdbackque-keys",  totalOrderMsgQueue.keySet().toString());
                            String finalProposedValue = totalOrderMsgQueue.get(minEntry.getKey())[0];
                            String finalProposedPort = totalOrderMsgQueue.get(minEntry.getKey())[1];
                            String msgFromPlusMessageID = minEntry.getKey();
                            String msgToStore = totalOrderMsgQueue.get(minEntry.getKey())[2];
                            totalOrderMsgQueue.remove(minEntry.getKey());

                            TextView localTextView = (TextView) findViewById(R.id.textView1);
                            localTextView.append(finalProposedValue + " : " + finalProposedPort + " : " + msgFromPlusMessageID + " : " + sequenceNumber + " : " + msgToStore + "\t\n");

                            ContentValues contentValue = new ContentValues();
                            contentValue.put("key", sequenceNumber);
                            contentValue.put("value", msgToStore);
                            sequenceNumber++;
                            Uri newUri = getContentResolver().insert(contentUri, contentValue);

                            String filename = "GroupMessengerOutput";
                            String string = msgToStore + "\n";
                            FileOutputStream outputStream;

                            try {
                                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                                outputStream.write(string.getBytes());
                                outputStream.close();
                            } catch (Exception e) {
                                Log.e(TAG, "File write failed");
                            }

                        }
                    }

                } catch (NullPointerException e) {
                    Log.e(TAG, "ServerTask NullPointerException");
                    System.err.println("Exception Class: "
                            + e.getClass().getSimpleName());
                    System.err.println("Exception Message: " + e.getMessage());
                }
            }

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, String, Void> {

        @Override
        protected Void doInBackground(final String... msgs) {
            if (msgs[0].trim().equals("Msg")) {
                int currentMessageCounter = messageCounter;
                String remotePort = msgs[2].trim();
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    String msgToSend;
                    String msgToReceive;
                    String msgToProcess = "";
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */

                    msgToSend = "Msg" + "\t" + currentMessageCounter + "\t" + whatsMyPort  + "\t" + msgs[1].trim();
                    //Log.v("Client-Msg-remotePort", remotePort + " => " + msgToSend);

                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println(msgToSend);

                    socket.setSoTimeout(milliSecInterval);

                    msgToReceive = in.readLine();

                    if (msgToReceive != null) {
                        msgToProcess = msgToReceive;
                    } else if (msgToReceive == null) {
                        msgToProcess = remotePort + "\t" + currentMessageCounter + "\t" + "0";
                    }

                    if (!msgToProcess.equals("")) {
                        publishProgress(msgToProcess);
                    }


                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    //Exp:https://coderanch.com/t/694619/java/Confused-Socket-setSoTimeout-works-SocketTimeoutException
                    System.err.println("Exception Class: "
                            + e.getClass().getSimpleName());
                    System.err.println("Exception Message: " + e.getMessage());
                    System.out.println("Socket read timeout?: "
                            + (e.getClass() == SocketTimeoutException.class));
                    String msgErrorToSend = remotePort + "\t" + currentMessageCounter + "\t" + "0";
                    publishProgress(msgErrorToSend);
                }
            } else if (msgs[0].trim().equals("Agr")) {
                for(final String remotePort: REMOTE_PORTS){
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));

                        String msgToSend;
                        String msgToReceive;
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                        msgToSend = "Agr" + "\t" + whatsMyPort + "\t" + msgs[1].trim() + "\t" + msgs[2].trim() + "\t" + msgs[3].trim();
                        //Log.v("Client-Agr-remotePort", remotePort + " => " + msgToSend);

                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        out.println(msgToSend);

                        socket.setSoTimeout(milliSecInterval);

                        msgToReceive = in.readLine();
                        if (msgToReceive != null) {
                            //Log.v("Client-Msg-Agr", msgToReceive);
                            //publishProgress(msgToReceive);
                        }

                        socket.close();
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException");
                        //Exp:https://coderanch.com/t/694619/java/Confused-Socket-setSoTimeout-works-SocketTimeoutException
                        System.err.println("Exception Class: "
                                + e.getClass().getSimpleName());
                        System.err.println("Exception Message: " + e.getMessage());
                        System.out.println("Socket read timeout?: "
                                + (e.getClass() == SocketTimeoutException.class));
                    }
                }
            } else if (msgs[0].trim().equals("Alv")) {
                String remotePort = msgs[1].trim();
                String messageID = msgs[2].trim();
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    String msgToSend;
                    String msgToReceive;
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                    msgToSend = "Alv";
                    //Log.v("Client-Agr-remotePort", remotePort + " => " + msgToSend);

                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println(msgToSend);

                    socket.setSoTimeout(milliSecInterval);

                    msgToReceive = in.readLine();
                    if (msgToReceive != null) {
                        //Log.v("Client-Msg-Agr", msgToReceive);
                        //publishProgress(msgToReceive);
                    } else if (msgToReceive == null && totalOrderMsgQueue.containsKey(remotePort + messageID)) {
                        totalOrderMsgQueue.remove(remotePort + messageID);
                    }

                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    //Exp:https://coderanch.com/t/694619/java/Confused-Socket-setSoTimeout-works-SocketTimeoutException
                    System.err.println("Exception Class: "
                            + e.getClass().getSimpleName());
                    System.err.println("Exception Message: " + e.getMessage());
                    System.out.println("Socket read timeout?: "
                            + (e.getClass() == SocketTimeoutException.class));
                    if (totalOrderMsgQueue.containsKey(remotePort + messageID)) {
                        totalOrderMsgQueue.remove(remotePort + messageID);
                    }
                }
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {
            String[] st = strings[0].split("\\t");
            //Log.v("Client-Pps", msgToReceive);
            String ppsFrom = st[0].trim();
            String messageID = st[1].trim();
            String proposedValue = st[2].trim();
            //Log.v("Server-Pps-Inc", messageID + " => " + ppsFrom + " : " + proposedValue);
            if (proposalReceived.containsKey(messageID)) {
                proposalReceived.get(messageID).put(ppsFrom, Integer.parseInt(proposedValue));
            } else if ((!proposalReceived.containsKey(messageID))){
                proposalReceived.put(messageID, new HashMap<String, Integer>());
                proposalReceived.get(messageID).put(ppsFrom, Integer.parseInt(proposedValue));
            }

            //System.out.println(proposalReceived.get(messageID).keySet().toString());

            if ((proposalReceived.containsKey(messageID) && proposalReceived.get(messageID).size() == 5)) {
                Integer maxProposedValueInMap = Collections.max(proposalReceived.get(messageID).values());
                List<Integer> keys = new ArrayList<Integer>();
                for (Map.Entry<String, Integer> entry : proposalReceived.get(messageID).entrySet()) {
                    if (entry.getValue()==maxProposedValueInMap) {
                        keys.add(Integer.parseInt(entry.getKey()));
                    }
                }
                int proposedPort = Collections.min(keys);
                //Log.v("Client-Agr", messageID + "\t" + Integer.toString(proposedPort) + "\t" + Integer.toString(maxProposedValueInMap));
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Agr", messageID, Integer.toString(proposedPort), Integer.toString(maxProposedValueInMap));
                proposalReceived.remove(messageID);
            }

            return;
        }
    }


}