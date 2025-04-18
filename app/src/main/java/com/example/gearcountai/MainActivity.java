// MainActivity.java
package com.example.gearcountai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView resultText;
    private EditText driverGearTeethInput;
    private EditText drivenGearTeethInput;
    private TextView ratioResultText;
    private Bitmap selectedImage;

    private final OkHttpClient client = new OkHttpClient();
    private final String apiUrl = "https://cog-detector-api.onrender.com/detect";

    private final List<Integer> gearTeethList = new ArrayList<>();
    private boolean nextInputIsDriver = true;

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    selectedImage = (Bitmap) extras.get("data");
                    imageView.setImageBitmap(selectedImage);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        resultText = findViewById(R.id.resultText);
        driverGearTeethInput = findViewById(R.id.driverGearTeeth);
        drivenGearTeethInput = findViewById(R.id.drivenGearTeeth);
        ratioResultText = findViewById(R.id.ratioResultText);
        Button captureButton = findViewById(R.id.captureButton);
        Button analyzeButton = findViewById(R.id.analyzeButton);
        Button calculateRatioButton = findViewById(R.id.calculateRatioButton);

        captureButton.setOnClickListener(view -> dispatchTakePictureIntent());

        analyzeButton.setOnClickListener(view -> {
            if (selectedImage != null) {
                uploadImageToApi(selectedImage);
            } else {
                resultText.setText("Please capture an image first.");
            }
        });

        calculateRatioButton.setOnClickListener(view -> calculateGearRatio());
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePictureLauncher.launch(takePictureIntent);
    }

    private void uploadImageToApi(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "gear.jpg",
                        RequestBody.create(byteArray, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> resultText.setText("API request failed: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> resultText.setText("API error: " + response.code()));
                    return;
                }

                String responseData = response.body().string().replaceAll("[^\\d]", "");
                runOnUiThread(() -> {
                    resultText.setText("Detected teeth: " + responseData);
                    try {
                        int teethCount = Integer.parseInt(responseData);
                        gearTeethList.add(teethCount);

                        if (nextInputIsDriver) {
                            driverGearTeethInput.setText(String.valueOf(teethCount));
                            nextInputIsDriver = false;
                        } else {
                            drivenGearTeethInput.setText(String.valueOf(teethCount));
                            nextInputIsDriver = true;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
        });
    }

    private void calculateGearRatio() {
        try {
            int driver = Integer.parseInt(driverGearTeethInput.getText().toString());
            int driven = Integer.parseInt(drivenGearTeethInput.getText().toString());
            double ratio = (double) driven / driver;
            ratioResultText.setText("Gear Ratio: " + String.format("%.2f", ratio));
        } catch (NumberFormatException e) {
            ratioResultText.setText("Please enter valid integers for both gears.");
        }
    }
}
