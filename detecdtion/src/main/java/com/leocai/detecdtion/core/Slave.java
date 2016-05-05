package com.leocai.detecdtion.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.leocai.detecdtion.blebrodcast.ShakeParameters;
import com.leocai.publiclibs.ShakingData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by leocai on 15-12-31.
 */
public class Slave extends KeyExtractor {

    private static final String TAG = "SLAVE";
    private final BluetoothAdapter mAdapter;
    private  BluetoothSocket bleSocket;
    private List<ShakingData> shakingDatas;


    public Slave(String remoteAddress) {
        setMaster(false);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        try {
            BluetoothDevice device = mAdapter.getRemoteDevice(remoteAddress);
            bleSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGetShakingDatas(final List<ShakingData> shakingDatas) {
        this.shakingDatas = shakingDatas;
        Log.d(TAG,Slave.this.shakingDatas.size()+"b");
        mAdapter.cancelDiscovery();
        startConnect(new ConnectedCallBack() {
            @Override
            public void onConnected() {
                DataOutputStream dataOutputStream = new DataOutputStream(out);
                StringBuilder logInfo = new StringBuilder();//TODO Test
                Log.d(TAG,Slave.this.shakingDatas.size()+"");
                for (int i = 0; i < TRAINNING_SIZE; i++) {
                    ShakingData shakingData = Slave.this.shakingDatas.get(i);
                    logInfo.append(shakingData.toString());
                    logInfo.append("\n");
                    try {
                        dataOutputStream.write(shakingData.getBytes());
                        dataOutputStream.flush();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                setLogInfo(logInfo.toString());
                setStates(ExtractorStates.TRAINDATA_SENDED);
                try {
                    onReceieveShakingParameter(ShakeParameters.read(in));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    public void startConnect(final ConnectedCallBack connectedCallBack) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    setStates(ExtractorStates.CONNECTING);
                    bleSocket.connect();
                    setStates(ExtractorStates.CONNECTED);
                    setIn(bleSocket.getInputStream());
                    setOut(bleSocket.getOutputStream());
                    connectedCallBack.onConnected();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void onReceieveShakingParameter(ShakeParameters shakeParameters){
        setStates(ExtractorStates.PARAMETER_RECEIEVED);
        setLogInfo(shakeParameters.toString());
        double[][] initMatrix = getInitMatrix(
                shakingDatas.get(0).getGravityAccData(),
                shakeParameters.getInitThetaSlave());
        List<ShakingData> convertedDatas = transformByParameter(initMatrix,shakingDatas,shakingDatas.size());
        ShakeBits shakeBits = generateBits(convertedDatas);
        Log.d(TAG,"ConvertedDataSize:"+convertedDatas.size());
        Log.d(TAG,"ShakeBitsSize:"+shakeBits.getBits().size());
        startReconcilation(shakeBits, new ReconcilationEndCallBack() {
            @Override
            public void onReconcilationEnd(List<Byte> bitsList, double mismatchRate) {
                String logInfo = "";
                for (byte b : bitsList) {
                    logInfo += b;
                }
                logInfo+="\n";
                logInfo+="MismatchRate:"+mismatchRate;
                setLogInfo(logInfo);
            }
        });
    }

    @Override
    protected boolean compareParity(byte[] bits, int subStart, int subEnd) throws IOException {
        int parity = 0;
        boolean slaveEven;
        int len = bits.length;
        for (int i = subStart; i <= subEnd; i++) parity += bits[i%len];
        slaveEven = (parity%2==0);
        DataInputStream dataIn = new DataInputStream(in);
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeBoolean(slaveEven);
        return dataIn.readBoolean();
    }

}