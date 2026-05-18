package com.example.inventarioar;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class activity_ar_scanner extends AppCompatActivity {

    private ArFragment arFragment;
    private ProgressBar loadingProgressBar;

    private HashMap<String, String> diccionarioProductos = new HashMap<>();
    private HashMap<String, TransformableNode> modelosColocados = new HashMap<>();
    private float ultimoToqueX = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_scanner);

        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        MaterialButton btnCerrar = findViewById(R.id.btnCerrarAR);

        if (btnCerrar != null) {
            btnCerrar.setOnClickListener(v -> finish());
        }

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

        if (arFragment != null) {
            arFragment.setOnSessionConfigurationListener(this::configurarMotorDeImagenes);
            arFragment.getArSceneView().getScene().addOnUpdateListener(this::vigilarCamara);
        }
    }

    private void configurarMotorDeImagenes(Session session, Config config) {
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
        config.setFocusMode(Config.FocusMode.AUTO);

        loadingProgressBar.setVisibility(View.VISIBLE);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Productos");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());

                executor.execute(() -> {
                    android.util.Log.d("AR_DEBUG", "1. INICIANDO CEREBRO DE AR...");
                    AugmentedImageDatabase baseDeDatosVisual = new AugmentedImageDatabase(session);

                    int contador = 1;

                    for (DataSnapshot productoSnap : snapshot.getChildren()) {
                        String id = productoSnap.child("id").getValue(String.class);
                        String imagenUrl = productoSnap.child("imagenUrl").getValue(String.class);
                        String modelo3DUrl = productoSnap.child("modelo3DUrl").getValue(String.class);

                        if (id != null && imagenUrl != null && !imagenUrl.isEmpty()) {
                            Log.d("AR_DEBUG", "2. Descargando foto #" + contador + " (ID: " + id + ")");
                            Bitmap fotoBitmap = descargarImagenDesdeURL(imagenUrl);

                            if (fotoBitmap != null) {
                                try {
                                    Log.d("AR_DEBUG", "3. Guardando en ARCore...");
                                    baseDeDatosVisual.addImage(id, fotoBitmap,0.15f);
                                    diccionarioProductos.put(id, modelo3DUrl);

                                    // 🚨 LA CURA DEFINITIVA: Destruir la foto original para salvar la RAM 🚨
                                    fotoBitmap.recycle();

                                   Log.d("AR_DEBUG", "4. ¡Foto guardada y RAM liberada con éxito!");
                                } catch (Exception e) {
                                    Log.e("AR_DEBUG", "Error al procesar la foto en ARCore: " + e.getMessage());
                                }
                            }
                        }
                        contador++;
                    }

                   Log.d("AR_DEBUG", "5. TODAS LAS FOTOS LISTAS. ENCENDIENDO CÁMARA...");

                    handler.post(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        try {
                            config.setAugmentedImageDatabase(baseDeDatosVisual);
                            session.configure(config);
                            loadingProgressBar.setVisibility(View.GONE);
                            Log.d("AR_DEBUG", "6. ¡ÉXITO TOTAL!");
                            Toast.makeText(activity_ar_scanner.this, "Escáner listo. Apunta a la imagen.", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e("AR_DEBUG", "Error encendiendo cámara: " + e.getMessage());
                        }
                    });
                });
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                loadingProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private Bitmap descargarImagenDesdeURL(String urlString) {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            input = connection.getInputStream();

            return BitmapFactory.decodeStream(input);

        } catch (Exception e) {
            return null;
        } finally {

            try {
                if (input != null) input.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void vigilarCamara(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) return;

        Collection<AugmentedImage> imagenesDetectadas = frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage imagen : imagenesDetectadas) {
            String idDelProducto = imagen.getName();

            if (imagen.getTrackingState() == TrackingState.PAUSED) {
              Log.d("AR_DEBUG", "Mmm... veo algo parecido a [" + idDelProducto + "] pero estoy calculando...");
            }


            else if (imagen.getTrackingState() == TrackingState.TRACKING) {

                if (!modelosColocados.containsKey(idDelProducto)) {
                    String urlDelModelo3D = diccionarioProductos.get(idDelProducto);

                   Log.d("AR_DEBUG", "Su URL 3D es: " + urlDelModelo3D);

                    if (urlDelModelo3D != null && !urlDelModelo3D.isEmpty()) {
                        modelosColocados.put(idDelProducto, null);
                        descargarYColocarModelo(imagen, urlDelModelo3D, idDelProducto);
                    }
                }
            }
        }
    }

    private void descargarYColocarModelo(AugmentedImage imagenDetectada, String modeloURL, String idProducto) {
        runOnUiThread(() -> loadingProgressBar.setVisibility(View.VISIBLE));

        ModelRenderable.builder()
                .setSource(this, Uri.parse(modeloURL))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(modelRenderable -> {
                    runOnUiThread(() -> loadingProgressBar.setVisibility(View.GONE));

                    AnchorNode anchorNode = new AnchorNode(imagenDetectada.createAnchor(imagenDetectada.getCenterPose()));
                    anchorNode.setParent(arFragment.getArSceneView().getScene());


                    TransformableNode transformNode = new TransformableNode(arFragment.getTransformationSystem());
                    transformNode.setParent(anchorNode);
                    transformNode.setRenderable(modelRenderable);

                    transformNode.getScaleController().setMinScale(0.001f);
                    transformNode.getScaleController().setMaxScale(2.0f);
                    transformNode.setLocalScale(new Vector3(0.01f, 0.01f, 0.01f));


                    transformNode.setLocalRotation(Quaternion.axisAngle(
                            new Vector3(1.0f, 0.0f, 0.0f), -90.0f));

                    transformNode.getRotationController().setEnabled(false);


                    transformNode.setOnTouchListener((hitTestResult, motionEvent) -> {
                        switch (motionEvent.getAction()) {
                            case MotionEvent.ACTION_DOWN:

                                ultimoToqueX = motionEvent.getX();
                                return true;

                            case MotionEvent.ACTION_MOVE:

                                float toqueActualX = motionEvent.getX();
                                float deltaX = toqueActualX - ultimoToqueX;
                                ultimoToqueX = toqueActualX;

                               Quaternion rotacionDelta =
                                        Quaternion.axisAngle(
                                                new Vector3(0.0f, 1.0f, 0.0f), // Eje vertical Y
                                                deltaX * 0.5f);
                                transformNode.setLocalRotation(
                                        Quaternion.multiply(
                                                transformNode.getLocalRotation(),
                                                rotacionDelta
                                        ));
                                return true;
                        }
                        return false;
                    });

                    transformNode.select();
                    modelosColocados.put(idProducto, transformNode);
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> loadingProgressBar.setVisibility(View.GONE));
                    return null;
                });
    }
}