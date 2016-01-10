package kr.gonigoni.cameramoving;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends ActionBarActivity implements SensorEventListener {

    /* 사진 촬영을 위한 이미지 버튼 */
    private ImageButton captureImgButton;

    /* 전 후면 카메라 전환을 위한 이미지 버튼 */
    private ImageButton switchCamButton;

    /* 카메라 미리 보기를 위한 SurfaceView */
    CameraSurfaceView cameraView;

    private static final String IMGBTN_TAG = "Capture button";
    private static final String SWICAMBTN_TAG = "Switch Camera button";

    /* 사진 저장 및 프리뷰 재시작을 위한 Callback */
    private static Camera.PictureCallback picCallback;

    /* Sensor 관련 객체 */
    SensorManager m_sensor_manager;
    Sensor m_acc_sensor, m_mag_sensor;

    /* Sensor 데이터를 저장할 변수들 */
    float[] m_acc_data = null, m_mag_data = null;
    float[] m_rotation = new float[9];
    float[] m_result_data = new float[3];

    /* Debugging을 위한 TextView */
    private TextView txtAzimuth;
    private TextView txtPitch;
    private TextView txtRoll;

    /* 전면 카메라 사용 중인지 여부 */
    private boolean isUsingFrontCam = false;

    /**
     * "dp" 단위를 "pixel"로 변경.
     */
    public static float convertDpToPixel(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return px;
    }

    /**
     * "pixel" 단위를 "dp" 단위로 변경.
     */
    public static float convertPixelsToDp(float px, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float dp = px / (metrics.densityDpi / 160f);
        return dp;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window win = getWindow();
        win.setContentView(R.layout.activity_main);

        /* Status bar 숨기기. 해당 기능은 Android 4.1 이후에서 지원함. */
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        /* ActionBar 숨기기 */
        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        /* Camera preview를 불러옴. */
        cameraView = new CameraSurfaceView(getApplicationContext(), this);
        FrameLayout previewFrame = (FrameLayout) findViewById(R.id.cameraPreviewFrame);
        previewFrame.addView(cameraView);

        /* Layout 겹치기 -- LayoutInflater 클래스를 이용함 */
        LayoutInflater inflater = (LayoutInflater) getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        /* inflate method의 두 번째 argument에 null 넣지 말 것. parent group을 의미함. */
        RelativeLayout upper = (RelativeLayout) inflater.inflate
                (R.layout.upper_layout, previewFrame, false);

        /* RelativeLayout에 대한 parameter */
        RelativeLayout.LayoutParams upperParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);

        /* 이제 진짜 겹침. 두 번째 argument에 null이 들어가면 안 됨. */
        win.addContentView(upper, upperParams);

        // 촬영 버튼을 객체로 만듦.
        captureImgButton = (ImageButton) findViewById(R.id.captureButton);
        // 버튼에 tag 설정
        captureImgButton.setTag(IMGBTN_TAG);

        // 카메라 전환 버튼을 객체로 만듦.
        switchCamButton = (ImageButton) findViewById(R.id.switchCamButton);
        // 버튼에 tag 설정
        switchCamButton.setTag(SWICAMBTN_TAG);

        // 버튼 위치 옮기기를 위해 DragListener 지정.
        findViewById(R.id.upper_lay).setOnDragListener(new MyDragListener(this));

        // Long-click listener 설정
        captureImgButton.setOnLongClickListener(new MyClickListener());

        /* PictureCallback */
        picCallback = new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                File pictureFile = getOutputMediaFile();
                if (pictureFile == null){
                    Log.d("CameraMoving", "Error creating media file, check storage permissions.");
                    return;
                }

                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();

                    // 갤러리에 보이도록 하기 위해 MediaScanner 호출.
                    MediaScanning scanning = new MediaScanning(getApplicationContext(), pictureFile);
                } catch (FileNotFoundException e) {
                    Log.d("CameraMoving", "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d("CameraMoving", "Error accessing file: " + e.getMessage());
                }

                // restart preview.
                camera.startPreview();
            }
        };

        /* 촬영 버튼 기능 */
        captureImgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.capture(picCallback);
            }
        });

        /* 전/후면 카메라 전환 기능 */
        switchCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isUsingFrontCam = cameraView.toggleCamera(isUsingFrontCam);
            }
        });

        // 버튼 위치 불러오기
        SharedPreferences pref = getSharedPreferences("buttonPosition", this.MODE_PRIVATE);
        ViewGroup.MarginLayoutParams buttonParams = new ViewGroup.MarginLayoutParams(captureImgButton.getLayoutParams());

        Log.i("CameraMoving", "firstX : " + Integer.toString(pref.getInt("firstX", 0)) +
                " firstY : " + Integer.toString(pref.getInt("firstY", 0)));

        if (!(pref.getInt("firstX", 0) == 0 && pref.getInt("firstY", 0) == 0)) {
            buttonParams.setMargins(pref.getInt("firstX", 0), pref.getInt("firstY", 0), 0, 0);
            captureImgButton.setLayoutParams(new RelativeLayout.LayoutParams(buttonParams));
        }

        /* 시스템 서비스로부터 SensorManager 객체를 얻음 */
        m_sensor_manager = (SensorManager) getSystemService(SENSOR_SERVICE);

        /* SensorManager를 이용하여 가속 센서와 자기장 센서 객체를 얻음 */
        m_acc_sensor = m_sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        m_mag_sensor = m_sensor_manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        /* Debugging TextView 설정 */
        txtAzimuth = (TextView) findViewById(R.id.textAzimuth);
        txtPitch = (TextView) findViewById(R.id.textPitch);
        txtRoll = (TextView) findViewById(R.id.textRoll);
    }

    /* 이 Activity가 포커스를 잃으면, 리스너 해제. (어차피 센서 데이터를 얻어도 소용 없으므로) */
    @Override
    protected void onPause() {
        super.onPause();

        // 리스너 해제
        m_sensor_manager.unregisterListener(this);
    }

    /* 이 Activity가 포커스를 얻으면, 가속 데이터와 자기장 데이터를 얻도록 리스너 등록 */
    @Override
    protected void onResume() {
        super.onResume();

        // 센서 값을 이 Context에서 받도록 리스너 등록
        m_sensor_manager.registerListener(this, m_acc_sensor, SensorManager.SENSOR_DELAY_UI);
        m_sensor_manager.registerListener(this, m_mag_sensor, SensorManager.SENSOR_DELAY_UI);
    }

    /* 파일으로 사진 저장 */
    private static File getOutputMediaFile()
    {
        File mediaStorageDir = new File (Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CameraMoving");

        /* 디렉토리 없으면 새로 만듦. */
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("CameraMoving", "Failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");

        return mediaFile;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* 센서 좌표 변경 시 이 함수 수정. 측정한 값을 전달함. */
    @Override
    public void onSensorChanged(SensorEvent event) {


        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // 가속 센서가 전달한 데이터인 경우 수치 데이터 복사.
            m_acc_data = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            // 자기장 센서가 전달한 데이터인 경우 수치 데이터 복사.
            m_mag_data = event.values.clone();
        }

        // 데이터가 존재하는 경우
        if (m_acc_data != null && m_mag_data != null) {
            // 가속 데이터와 자기장 데이터로 회전 매트릭스를 얻음.
            SensorManager.getRotationMatrix(m_rotation, null, m_acc_data, m_mag_data);
            // 회전 매트릭스로 방향 데이터를 얻는다.
            SensorManager.getOrientation(m_rotation, m_result_data);

            // radian 값을 degree 값으로 변경한다.
            m_result_data[0] = (float)Math.toDegrees(m_result_data[0]);
            m_result_data[1] = (float)Math.toDegrees(m_result_data[1]);
            m_result_data[2] = (float)Math.toDegrees(m_result_data[2]);

            // 변경한 값이 0보다 작으면, 360을 더함.
            if (m_result_data[0] < 0) m_result_data[0] += 360;

            // 우리는 방위값만 필요함. 방위값에 따라 네 가지 경우로 나누어 줌.
            // 주석으로 표시한 방향은 단말기의 위가 어디에 있는지에 따른 기준임.
            if (m_result_data[0] >= 45f && m_result_data[0] < 135f)         // 세로, 위
                cameraView.setCamOrientation(180);
            else if (m_result_data[0] >= 135f && m_result_data[0] < 225f)   // 가로, 오른쪽
                cameraView.setCamOrientation(270);
            else if (m_result_data[0] >= 225f && m_result_data[0] < 315f)   // 세로, 아래
                cameraView.setCamOrientation(0);
            else                                                            // 가로, 왼쪽
                cameraView.setCamOrientation(90);

            // Set debugging text.
            txtAzimuth.setText("Azimuth: " + String.format("%.6f", m_result_data[0]));
            txtPitch.setText("Pitch: " + String.format("%.6f", m_result_data[1]));
            txtRoll.setText("Roll: " + String.format("%.6f", m_result_data[2]));
        }

    }

    /* Sensor 정확도 변경 시 호출되는 method. 거의 호출될 일 없음. */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /* 카메라 미리보기를 위한 SurfaceView 정의 */
    private class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera camera = null;
        private MainActivity currentActivity = null;

        public CameraSurfaceView(Context context, MainActivity current) {
            super(context);

            mHolder = getHolder();
            mHolder.addCallback(this);

            currentActivity = current;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open();
            Camera.Parameters camParam = camera.getParameters();

            /* 처음 실행 시 미리보기가 회전되어 나오는 문제 해결을 위함. */
            int activityOrientation =
                    currentActivity.getResources().getConfiguration().orientation;

            // 세로 방향에서 시작함. 이렇게 하지 않으면 처음 실행 시 미리보기가 회전되어 나옴.
            if (activityOrientation == Configuration.ORIENTATION_PORTRAIT) {
                camera.setDisplayOrientation(90);
                /* 촬영 시 기울여져 나오는 문제 있어서 추가함. */
                camParam.setRotation(90);
            }
            camera.setParameters(camParam);
            try {
                camera.setPreviewDisplay(mHolder);
            } catch (Exception e) {
                Log.e("CameraSurfaceView", "Failed to set camera preview.", e);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // 프리뷰 종료 시 카메라 사용이 끝났다고 간주, 리소스 반환.
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        public boolean capture(Camera.PictureCallback handler) {
            if (camera != null) {
                /* To support auto focus when you take picture. */
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        camera.takePicture(null, null, picCallback);
                    }
                });

                return true;
            } else {
                return false;
            }
        }

        /**
         * 처음 실행 시 카메라 미리보기가 제대로 표시 안 됨.
         * 회전 시에도 문제 생기므로 다음 method 추가 */
        public void setCamOrientation (int value) {
            /* 처음 시작하면 camera가 null임. camera가 null이 아닌 때부터 회전 각도 적용되도록 함. */
            if (camera != null) {
                /* 촬영 시 기울여져 나오는 문제 있어서 추가함. */
                Camera.Parameters camParam = camera.getParameters();
                camParam.setRotation(value);
                camera.setParameters(camParam);
            }
        }

        /* 전/후면 카메라 전환하는 method */
        public boolean toggleCamera (boolean isFront) {
            // 카메라의 수 얻기
            int numOfCams = Camera.getNumberOfCameras();
            boolean temp = isFront;

            if (numOfCams >= 2) {
                if (isFront) {
                    // 전면 카메라 사용 중 -> 후면으로 전환
                    // 1. 기존 카메라 프리뷰는 중지.
                    camera.stopPreview();
                    camera.release();

                    // 2. 카메라 전환
                    camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                    temp = false;
                } else {
                    // 후면 카메라 사용 중 -> 전면으로 전환
                    // 1. 기존 카메라 프리뷰는 중지.
                    camera.stopPreview();
                    camera.release();

                    // 2. 카메라 전환.
                    camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    temp = true;
                }

                // 3. Preview 시작.
                try {
                    camera.setDisplayOrientation(90);
                    camera.setPreviewDisplay(mHolder);
                    camera.startPreview();
                } catch (Exception e) {
                    Log.d("CameraMoving", "Can't set camera preview.");
                }
            }

            return temp;
        }
    }

    private final class MyClickListener implements View.OnLongClickListener {

        // 버튼이 long click될 시 호출됨.
        @Override
        public boolean onLongClick(View v) {
            // Object의 Tag로부터 만듦.
            ClipData.Item item = new ClipData.Item((CharSequence)v.getTag());

            String[] mimeTypes = {ClipDescription.MIMETYPE_TEXT_PLAIN};
            ClipData data = new ClipData(v.getTag().toString(), mimeTypes, item);
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);

            /**
             * data : data to be dragged.
             * shadowBuilder : drag shadow
             * view : local data about the drag and drop operation
             * 0 : no needed flags
             */
            v.startDrag(data, shadowBuilder, v, 0);

            v.setVisibility(View.INVISIBLE);
            return true;
        }
    }

    class MyDragListener implements View.OnDragListener {

        private MainActivity currentActivity = null;

        public MyDragListener (MainActivity current) {
            currentActivity = current;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            int firstX, firstY;
            int lastX, lastY;
            int screenWidth, screenHeight;

            View view = (View) event.getLocalState();
            //Log.i("CameraMoving", "view is " + view.toString());    // ImageButton
            ViewGroup viewGroup;
            RelativeLayout upperView;
            ViewGroup.MarginLayoutParams buttonParam;
            SharedPreferences pref;
            SharedPreferences.Editor prefEditor;
            RelativeLayout.LayoutParams exitParam = (RelativeLayout.LayoutParams) view.getLayoutParams();

            // 원래 위치를 구함.
            firstX = (int) v.getX();
            firstY = (int) v.getY();

            // 화면 전체 크기를 구함
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;

            switch (event.getAction()) {
                // 드래그 시작 시
                case DragEvent.ACTION_DRAG_STARTED:
                    // do nothing
                    break;

                // 드래그 포인트가 View의 bounding box에 들어갔을 때
                case DragEvent.ACTION_DRAG_ENTERED:
                    /* 이 시점에서 position이 저장되기 시작. */
                    //Log.i("CameraMoving", "New position : " + Float.toString(event.getX()) + "," + Float.toString(event.getY()));
                    break;

                // 유저가 View의 bounding box 바깥으로 drag shadow를 옮겼을 때
                case DragEvent.ACTION_DRAG_EXITED:
                    //Log.i("CameraMoving", "view is " + view.toString());    // ImageButton
                    viewGroup = (ViewGroup) view.getParent();
                    //Log.i("CameraMoving", "viewGroup is " + viewGroup.toString());  // RelativeLayout
                    // 기존의 ImageButton은 지움.
                    viewGroup.removeView(view);

                    // argument로 온 v는 ImageButton의 상위 view임.
                    upperView = (RelativeLayout) v;

                    exitParam.addRule(RelativeLayout.CENTER_HORIZONTAL);
                    exitParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    exitParam.setMargins(0, 0, 0, 20);

                    view.setLayoutParams(exitParam);

                    // 버튼을 다시 추가.
                    upperView.addView(view);
                    view.setVisibility(View.VISIBLE);

                    /* 초기화된 위치를 저장함 */
                    pref = getSharedPreferences("buttonPosition", currentActivity.MODE_PRIVATE);
                    prefEditor = pref.edit();

                    prefEditor.putInt("firstX", 0);
                    prefEditor.putInt("firstY", 0);

                    prefEditor.apply();

                    break;

                // drag가 끝났을 때, 드래그 포인트가 View의 bounding box 내에 있을 때
                case DragEvent.ACTION_DROP:
                    /* 위치를 구해서 고정함 */
                    //Log.i("CameraMoving", "ACTION_DROP");
                    //Log.i("CameraMoving", "V is " + v.toString());

                    /* 이 때의 Position을 저장해야 함. */
                    Log.i("CameraMoving", "Old position : " + Float.toString(firstX) + "," + Float.toString(firstY));
                    Log.i("CameraMoving", "New position : " + Float.toString(event.getX()) + "," + Float.toString(event.getY()));
                    //Log.i("CameraMoving", "Width : " + Integer.toString(screenWidth) + " Height : " + Integer.toString(screenHeight));


                    viewGroup = (ViewGroup) view.getParent();
                    //Log.i("CameraMoving", "viewGroup is " + viewGroup.toString());  // RelativeLayout
                    // 버튼에 대한 Parameter 설정.
                    buttonParam = new ViewGroup.MarginLayoutParams(view.getLayoutParams());

                    /* 75 = image button size */
                    lastX = (int) event.getX() - firstX - 75;
                    lastY = (int) event.getY() - firstY - 75;

                    // 기존의 ImageButton은 지움.
                    viewGroup.removeView(view);

                    if (lastX < 0 || screenWidth - (int) event.getX() < 75) {
                        /* 맨 왼쪽 || 맨 오른쪽 --> 초기화 */
                        exitParam.addRule(RelativeLayout.CENTER_HORIZONTAL);
                        exitParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                        exitParam.setMargins(0, 0, 0, 20);
                        view.setLayoutParams(exitParam);
                    } else if (lastY < 0 || screenHeight - (int) event.getY() < 75) {
                        /* 맨 위쪽 || 맨 아래쪽 --> 초기화 */
                        exitParam.addRule(RelativeLayout.CENTER_HORIZONTAL);
                        exitParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                        exitParam.setMargins(0, 0, 0, 20);
                        view.setLayoutParams(exitParam);
                    } else {
                        /* 그 이외의 경우 */
                        // pixel 기준으로 오는 것을 dp로 변환
                        float lastXdp = convertPixelsToDp((float) lastX, currentActivity.getApplicationContext());
                        float lastYdp = convertPixelsToDp((float) lastY, currentActivity.getApplicationContext());
                        Log.i("CameraMoving", "lastXdp: " + lastXdp + " lastYdp: " + lastYdp);

                        /* 전/후면 카메라와 겹치지 않게 버튼 배치 */
                        if (lastXdp < 60f && lastYdp < 60f) {
                            if (lastXdp < 60f)
                                lastX = (int) convertDpToPixel(50f, currentActivity.getApplicationContext());
                            if (lastYdp < 60f)
                                lastY = (int) convertDpToPixel(50f, currentActivity.getApplicationContext());
                        }

                        /* Margin 설정 */
                        buttonParam.setMargins(lastX, lastY, 0, 0);
                        view.setLayoutParams(new RelativeLayout.LayoutParams(buttonParam));
                    }

                    // argument로 온 v는 ImageButton의 상위 view임.
                    upperView = (RelativeLayout) v;

                    // 버튼을 다시 추가.
                    upperView.addView(view);
                    view.setVisibility(View.VISIBLE);

                    /* 위치를 저장함 */
                    pref = getSharedPreferences("buttonPosition", MODE_PRIVATE);
                    prefEditor = pref.edit();

                    prefEditor.putInt("firstX", lastX);
                    prefEditor.putInt("firstY", lastY);

                    prefEditor.apply();

                    break;

                // drag and drop이 완전히 끝났을 때
                case DragEvent.ACTION_DRAG_ENDED:
                    //이 때는 position이 저장 안 됨.
                    break;

                default:
                    break;
            }
            return true;
        }
    }
}
