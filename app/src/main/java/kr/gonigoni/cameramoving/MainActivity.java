package kr.gonigoni.cameramoving;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Camera;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends ActionBarActivity {

    /* 사진 촬영을 위한 이미지 버튼 */
    private ImageButton captureImgButton;
    /* 카메라 미리 보기를 위한 SurfaceView */
    CameraSurfaceView cameraView;

    private static final String IMGBTN_TAG = "Capture button";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window win = getWindow();
        win.setContentView(R.layout.activity_main);

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

        captureImgButton = (ImageButton) findViewById(R.id.captureButton);
        // 버튼에 tag 설정
        captureImgButton.setTag(IMGBTN_TAG);

        // 버튼 위치 옮기기를 위해 DragListener 지정.
        findViewById(R.id.upper_lay).setOnDragListener(new MyDragListener(this));

        // Long-click listener 설정
        captureImgButton.setOnLongClickListener(new MyClickListener());

        /* 촬영 버튼 기능 */
        captureImgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.capture(new Camera.PictureCallback() {
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
                });
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

    /* 회전 시 카메라 방향 설정을 위함. */
    /* TODO : 화면 회전 시 delay 없도록. */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 가로 전환 시
            cameraView.setCamOrientation(0);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 세로 전환 시
            cameraView.setCamOrientation(90);
        }
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

            /* 처음 실행 시 미리보기가 회전되어 나오는 문제 해결을 위함. */
            int activityOrientation =
                    currentActivity.getResources().getConfiguration().orientation;

            if (activityOrientation == Configuration.ORIENTATION_LANDSCAPE) {    // 가로
                camera.setDisplayOrientation(0);
                /* 촬영 시 기울여져 나오는 문제 있어서 추가함. */
                Camera.Parameters camParam = camera.getParameters();
                camParam.setRotation(0);
                camera.setParameters(camParam);
            }
            else if (activityOrientation == Configuration.ORIENTATION_PORTRAIT) {// 세로
                camera.setDisplayOrientation(90);
                /* 촬영 시 기울여져 나오는 문제 있어서 추가함. */
                Camera.Parameters camParam = camera.getParameters();
                camParam.setRotation(90);
                camera.setParameters(camParam);
            }
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
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        public boolean capture(Camera.PictureCallback handler) {
            if (camera != null) {
                camera.takePicture(null, null, handler);
                return true;
            } else {
                return false;
            }
        }

        /**
         * 처음 실행 시 카메라 미리보기가 제대로 표시 안 됨.
         * 회전 시에도 문제 생기므로 다음 method 추가 */
        public void setCamOrientation (int value){
            camera.setDisplayOrientation(value);
            /* 촬영 시 기울여져 나오는 문제 있어서 추가함. */
            Camera.Parameters camParam = camera.getParameters();
            camParam.setRotation(value);
            camera.setParameters(camParam);
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
            View view;
            ViewGroup viewGroup;
            RelativeLayout upperView;
            ViewGroup.MarginLayoutParams buttonParam;
            SharedPreferences pref;
            SharedPreferences.Editor prefEditor;

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
                    // change the shape of the view?
                    //Log.i("CameraMoving", "ACTION_DRAG_ENTERED");
                    //Log.i("CameraMoving", "Old position : " + Float.toString(firstX) + "," + Float.toString(firstY));
                    /* 이 시점에서 position이 저장되기 시작. */
                    //Log.i("CameraMoving", "New position : " + Float.toString(event.getX()) + "," + Float.toString(event.getY()));
                    break;

                // 유저가 View의 bounding box 바깥으로 drag shadow를 옮겼을 때
                case DragEvent.ACTION_DRAG_EXITED:
                    //Log.i("CameraMoving", "ACTION_DRAG_EXITED");
                    //Log.i("CameraMoving", "(exited) Old position : " + Float.toString(firstX) + "," + Float.toString(firstY));
                    //Log.i("CameraMoving", "(exited) New position : " + Float.toString(event.getX()) + "," + Float.toString(event.getY()));
                    /* 위치 초기화 */
                    view = (View) event.getLocalState();
                    //Log.i("CameraMoving", "view is " + view.toString());    // ImageButton
                    viewGroup = (ViewGroup) view.getParent();
                    //Log.i("CameraMoving", "viewGroup is " + viewGroup.toString());  // RelativeLayout
                    // 기존의 ImageButton은 지움.
                    viewGroup.removeView(view);

                    // argument로 온 v는 ImageButton의 상위 view임.
                    upperView = (RelativeLayout) v;

                    RelativeLayout.LayoutParams exitParam = (RelativeLayout.LayoutParams) view.getLayoutParams();

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

                    prefEditor.commit();

                    break;

                // drag가 끝났을 때, 드래그 포인트가 View의 bounding box 내에 있을 때
                case DragEvent.ACTION_DROP:
                    /* 위치를 구해서 고정함 */
                    //Log.i("CameraMoving", "ACTION_DROP");
                    //Log.i("CameraMoving", "V is " + v.toString());

                    /* 이 때의 Position을 저장해야 함. */
                    //Log.i("CameraMoving", "Old position : " + Float.toString(firstX) + "," + Float.toString(firstY));
                    //Log.i("CameraMoving", "New position : " + Float.toString(event.getX()) + "," + Float.toString(event.getY()));
                    //Log.i("CameraMoving", "Width : " + Integer.toString(screenWidth) + " Height : " + Integer.toString(screenHeight));

                    /* 75 = image button size */
                    lastX = (int) event.getX() - firstX - 75;
                    if (lastX < 0)                          /* 맨 왼쪽 */
                        lastX = 0;
                    else if (screenWidth - (int) event.getX() < 150)      /* 맨 오른쪽 */
                        lastX = screenWidth - 150;

                    lastY = (int) event.getY() - firstY - 75;

                    if (lastY < 0)                              /* 맨 위쪽 */
                        lastY = 0;
                    else if (screenHeight - (int) event.getY() < 150)         /* 맨 아래쪽 */
                        lastY = screenHeight - 200;

                    view = (View) event.getLocalState();
                    //Log.i("CameraMoving", "view is " + view.toString());    // ImageButton
                    viewGroup = (ViewGroup) view.getParent();
                    //Log.i("CameraMoving", "viewGroup is " + viewGroup.toString());  // RelativeLayout
                    // 기존의 ImageButton은 지움.
                    viewGroup.removeView(view);

                    // argument로 온 v는 ImageButton의 상위 view임.
                    upperView = (RelativeLayout) v;

                    // 버튼에 대한 Parameter 설정.
                    buttonParam = new ViewGroup.MarginLayoutParams(view.getLayoutParams());

                    buttonParam.setMargins(lastX, lastY, 0, 0);
                    view.setLayoutParams(new RelativeLayout.LayoutParams(buttonParam));

                    // 버튼을 다시 추가.
                    upperView.addView(view);
                    view.setVisibility(View.VISIBLE);

                    /* 위치를 저장함 */
                    pref = getSharedPreferences("buttonPosition", currentActivity.MODE_PRIVATE);
                    prefEditor = pref.edit();

                    prefEditor.putInt("firstX", lastX);
                    prefEditor.putInt("firstY", lastY);

                    prefEditor.commit();

                    break;

                // drag and drop이 완전히 끝났을 때
                case DragEvent.ACTION_DRAG_ENDED:
                    /*Log.i("CameraMoving", "ACTION_DRAG_ENDED");
                    //이 때는 position이 저장 안 됨.
                    Log.i("CameraMoving", "Old position : " + Float.toString(firstX) + "," + Float.toString(firstY));
                    Log.i("CameraMoving", "New position : " + Float.toString(event.getX()) + "," + Float.toString(event.getY()));*/
                    break;

                default:
                    break;
            }
            return true;
        }
    }
}
