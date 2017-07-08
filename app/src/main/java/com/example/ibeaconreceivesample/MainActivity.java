package com.example.ibeaconreceivesample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {

    private BluetoothAdapter mBluetoothAdapter;
    NumberPicker numPicker;
    TextView tv, tv2, tv3;
    Handler handler;
    String uuid, major, minor, RSSI = null;
    String strLatitude, strLongitude, strTime;
    int Tx;
    int count = 0;       //スキャン回数 จำนวนของการสแกน
    int tx_count = 0;   // จำนวนแต่ละระยะ
    //int flag;
    Button button;
    private String keyPush;

    //GPS
    // 許可されたパーミッションの種類を識別する番号 ตัวเลขที่ระบุประเภทของสิทธิ์ที่ได้รับอนุญาต
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;
    // GPSの位置情報を許可するボタン
    //private Button mBtnGrantGPS;ปุ่มเพื่อให้ข้อมูลตำแหน่งจีพีเอสของ
    // 各種情報を表示するラベル
    private TextView mTvLocationPermissionState, mTvLatitude, mTVLongitude, mTVTime;
    // 位置情報を管理するクラス ระดับที่จัดการข้อมูลสถานที่
    private LocationManager mLocationManager;
    public FirebaseDatabase database;
    public DatabaseReference myRefRSSI, myRefLat, myRefLong, myRefTD;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = FirebaseDatabase.getInstance();
        myRefRSSI = database.getReference("RSSI");
        myRefLat = database.getReference("Lat");
        myRefLong = database.getReference("Long");
        myRefTD = database.getReference("TimeDate");


        //setContentViewしたViewの中からこのidを持つViewを探す
        tv = (TextView) findViewById(R.id.textView);
        tv2 = (TextView) findViewById(R.id.textView4);
        tv3 = (TextView) findViewById(R.id.textView5);

        //GPS
        mTvLocationPermissionState = (TextView) findViewById(R.id.tvFineLocationPermissionState);
        mTvLatitude = (TextView) findViewById(R.id.tvLatitude);
        mTVLongitude = (TextView) findViewById(R.id.tvLongitude);
        mTVTime = (TextView) findViewById(R.id.tvTime);


        //Handlerはメインスレッドで実行したいメソッドがあるときに使用
        handler = new Handler();

        //GPS許可(permissionCheck)
        // Android 6.0以上の場合
        if (android.os.Build.VERSION.SDK_INT >= 23) {//OSバージョンを表す定数
            checkSelfPermission();
        } else {// Android 6.0以下の場合
            // インストール時点で許可されているのでチェックの必要なし
            Toast.makeText(MainActivity.this, "位置情報の取得は既に許可されています(Android 5.0以下です)", Toast.LENGTH_SHORT).show();
            InitLocationManager();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        Button btn = (Button) findViewById(R.id.rec_btn);
        btn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                //tx_count++;

                Button btn2 = (Button) findViewById(R.id.stop_btn);
                btn2.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                tv2.setText("スキャン停止");
                            }
                        });
                        //スキャン停止
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                });
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv2.setText("スキャン中です...");
                    }
                });
                // スキャン開始
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
        });

        findViews();
        initViews();

    }

/*	public void onSwitchClicked(View view) {
        Switch swtOnOff = (Switch) view;
		if (swtOnOff.isChecked()) { // ON状態になったとき
			flag =1 ;
		} else {
			flag =0;
		}
	}*/

    private void findViews() {
        numPicker = (NumberPicker) findViewById(R.id.numberPicker);
        button = (Button) findViewById(R.id.button);
    }

    private void initViews() {
        numPicker.setMaxValue(30);
        numPicker.setMinValue(0);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                tx_count = numPicker.getValue();
                Toast.makeText(getApplicationContext(), +tx_count + "m計測中", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void RecordFile(String RSSI, String strLatitude, String strLongitude, String strTime) {
        if (RSSI != null) {
            try {
                OutputStream out = openFileOutput("rssi_" + tx_count + "dis.txt", MODE_WORLD_WRITEABLE | MODE_APPEND);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
                //追記する
                writer.append("{" + RSSI + "," + strLatitude + "," + strLongitude + "," + strTime + "}\n");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {//อ่านค่า RSSI จาก BLE
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            // Log.d(TAG, "receive!!!");
            getScanData(scanRecord);
            RSSI = String.valueOf(rssi);
            count++;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    tv3.setText("ただ今," + count + "回目です.");
                }
            });

            if (count > 100) {
                count = 0;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv2.setText("RSSIを100回測定完了、スキャンを停止");
                    }
                });
            } else {
                /*try {
					OutputStream out = openFileOutput("rssi_"+ tx_count+"dis.txt", MODE_WORLD_WRITEABLE | MODE_APPEND);
					PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
					//追記する
					writer.append(RSSI + "\n");
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();

				}*/

                RecordFile(RSSI, strLatitude, strLongitude, strTime);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tv2.setText("スキャン,記録中です...");
                    }
                });
            }


            handler.post(new Runnable() {
                @Override
                public void run() {
                    tv.setText("uuid : " + uuid + "\n"
                            + "major : " + major + "\n"
                            + "minor : " + minor + "\n"
                            + "RSSI : " + RSSI + "\n");
                }
            });
//            myRefmyRefRSSI.child("RSSI").child("values").setValue(RSSI);
            myRefRSSI.child("RSSI").child("values").setValue(RSSI);

            keyPush = myRefRSSI.child("History").push().getKey();
            myRefRSSI.child("History").child(keyPush).child("RSSI").setValue(RSSI);


            // Log.d(TAG, "device name:"+device.getName() );
            // Log.d(TAG, "device address:"+device.getAddress() );
        }

    };

	/*@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}*/

    private void getScanData(byte[] scanRecord) {
        if (scanRecord.length > 30) {
            if ((scanRecord[5] == (byte) 0x4c) && (scanRecord[6] == (byte) 0x00) &&
                    (scanRecord[7] == (byte) 0x02) && (scanRecord[8] == (byte) 0x15)) {
                String uuid = Integer.toHexString(scanRecord[9] & 0xff)
                        + Integer.toHexString(scanRecord[10] & 0xff)
                        + Integer.toHexString(scanRecord[11] & 0xff)
                        + Integer.toHexString(scanRecord[12] & 0xff)
                        + "-"
                        + Integer.toHexString(scanRecord[13] & 0xff)
                        + Integer.toHexString(scanRecord[14] & 0xff)
                        + "-"
                        + Integer.toHexString(scanRecord[15] & 0xff)
                        + Integer.toHexString(scanRecord[16] & 0xff)
                        + "-"
                        + Integer.toHexString(scanRecord[17] & 0xff)
                        + Integer.toHexString(scanRecord[18] & 0xff)
                        + "-"
                        + Integer.toHexString(scanRecord[19] & 0xff)
                        + Integer.toHexString(scanRecord[20] & 0xff)
                        + Integer.toHexString(scanRecord[21] & 0xff)
                        + Integer.toHexString(scanRecord[22] & 0xff)
                        + Integer.toHexString(scanRecord[23] & 0xff)
                        + Integer.toHexString(scanRecord[24] & 0xff);

                String major = Integer.toHexString(scanRecord[25] & 0xff) + Integer.toHexString(scanRecord[26] & 0xff);
                String minor = Integer.toHexString(scanRecord[27] & 0xff) + Integer.toHexString(scanRecord[28] & 0xff);

            }
        }
    }

    //位置情報許可の確認
    public void checkSelfPermission() {
        //すでに許可している
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 権限があればLocationManagerを取得
            Toast.makeText(MainActivity.this, "位置情報の取得は既に許可されています", Toast.LENGTH_SHORT).show();
            mTvLocationPermissionState.setText("GPS取得状態:実行中");
            InitLocationManager();
        }
        //拒否していた場合
        else {
            // なければ権限を求めるダイアログを表示
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);
        }
    }

    //LocationManagerを取得する関数
    private void InitLocationManager() {
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3000, // 位置情報更新を行う最低更新時間間隔（ms）
                5, // 位置情報更新を行う最小距離間隔（メートル）
                new LocationListener() {
                    // ロケーションが変更された時の動き
                    @Override
                    public void onLocationChanged(final Location location) {
                        // 権限のチェック
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            return;
                        // テキストとログに位置情報を表示
                        Toast.makeText(MainActivity.this, "位置情報が更新されました", Toast.LENGTH_SHORT).show();
                        mTvLocationPermissionState.setText("GPS取得状態:取得済み");

                        //取得したデータをtxtで保存
						/*try {
							OutputStream out = openFileOutput("Location.txt", MODE_APPEND);
							PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
							writer.append("Latitude" + String.valueOf(location.getLatitude()) + "Longitude" + String.valueOf(location.getLongitude()) + "Time" + df.format(location.getTime()));
							writer.close();
						} catch (IOException e) {
							e.printStackTrace();
						}*/

                        strLatitude = String.valueOf(location.getLatitude());
                        strLongitude = String.valueOf(location.getLongitude());
                        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                        strTime = String.valueOf(df.format(location.getTime()));

                        RecordFile(RSSI, strLatitude, strLongitude, strTime);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                mTvLatitude.setText(String.valueOf(location.getLatitude()));
                                mTVLongitude.setText(String.valueOf(location.getLongitude()));
                                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                                mTVTime.setText(df.format(location.getTime()));
                                //mLocationManager.removeUpdates(this);
                            }
                        });


                        myRefLat.child("Latitude").child("values").setValue(location.getLatitude());
                        myRefLong.child("Logtitude").child("values").setValue(location.getLongitude());
                        myRefTD.child("TimeDate").child("values").setValue(df.format(location.getTime()));


//                        keyPush = myRefRSSI.child("History").push().getKey();
//                        myRefRSSI.child("History").child(keyPush).child("Latitude").setValue(location.getLatitude());
//                        myRefRSSI.child("History").child(keyPush).child("Logtitude").setValue(location.getLongitude());
//                        myRefRSSI.child("History").child(keyPush).child("TimeDate").setValue(df.format(location.getTime()));




                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        Toast.makeText(MainActivity.this, "GPS provide is disabled", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                        Toast.makeText(MainActivity.this, "GPS provide is enabled", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                        Toast.makeText(MainActivity.this, "GPS provide status changed", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
}
