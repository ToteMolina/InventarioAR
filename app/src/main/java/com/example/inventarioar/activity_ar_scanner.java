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
import androidx.appcompat.app.AlertDialog;
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
    private float inicioToqueX = 0;
    private float inicioToqueY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_scanner);

        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        MaterialButton btnCerrar = findViewById(R.id.btnCerrarAR);
        if (btnCerrar != null) btnCerrar.setOnClickListener(v -> finish());

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        if (arFragment != null) {
            arFragment.setOnSessionConfigurationListener(this::configurarMotorDeImagenes);
            arFragment.getArSceneView().getScene().addOnUpdateListener(this::vigilarCamara);
        }
    }

    private void configurarMotorDeImagenes(Session session, Config config) {
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
        config.setFocusMode(Config.FocusMode.AUTO);
        loadingProgressBar.setVisibility(View.VISIBLE);

        FirebaseDatabase.getInstance().getReference("Productos").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());
                executor.execute(() -> {
                    AugmentedImageDatabase baseDeDatosVisual = new AugmentedImageDatabase(session);
                    for (DataSnapshot productoSnap : snapshot.getChildren()) {
                        String id = productoSnap.child("id").getValue(String.class);
                        String imagenUrl = productoSnap.child("imagenUrl").getValue(String.class);
                        String modelo3DUrl = productoSnap.child("modelo3DUrl").getValue(String.class);
                        if (id != null && imagenUrl != null) {
                            Bitmap fotoBitmap = descargarImagenDesdeURL(imagenUrl);
                            if (fotoBitmap != null) {
                                baseDeDatosVisual.addImage(id, fotoBitmap, 0.15f);
                                diccionarioProductos.put(id, modelo3DUrl);
                                fotoBitmap.recycle();
                            }
                        }
                    }
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        config.setAugmentedImageDatabase(baseDeDatosVisual);
                        session.configure(config);
                        loadingProgressBar.setVisibility(View.GONE);
                    });
                });
            }
            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { loadingProgressBar.setVisibility(View.GONE); }
        });
    }

    private Bitmap descargarImagenDesdeURL(String urlString) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.connect();
            return BitmapFactory.decodeStream(connection.getInputStream());
        } catch (Exception e) { return null; }
    }

    private void vigilarCamara(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) return;
        for (AugmentedImage imagen : frame.getUpdatedTrackables(AugmentedImage.class)) {
            if (imagen.getTrackingState() == TrackingState.TRACKING && !modelosColocados.containsKey(imagen.getName())) {
                String url = diccionarioProductos.get(imagen.getName());
                if (url != null) {
                    modelosColocados.put(imagen.getName(), null);
                    descargarYColocarModelo(imagen, url, imagen.getName());
                }
            }
        }
    }

    private void descargarYColocarModelo(AugmentedImage imagenDetectada, String modeloURL, String idProducto) {
        ModelRenderable.builder().setSource(this, Uri.parse(modeloURL)).setIsFilamentGltf(true).build()
                .thenAccept(modelRenderable -> {
                    AnchorNode anchorNode = new AnchorNode(imagenDetectada.createAnchor(imagenDetectada.getCenterPose()));
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    TransformableNode transformNode = new TransformableNode(arFragment.getTransformationSystem());
                    transformNode.setParent(anchorNode);
                    transformNode.setRenderable(modelRenderable);

                    // --- AJUSTES DE ZOOM (ScaleController) ---
                    transformNode.getScaleController().setEnabled(true);
                    transformNode.getScaleController().setMinScale(0.001f);
                    transformNode.getScaleController().setMaxScale(2.0f);
                    transformNode.setLocalScale(new Vector3(0.01f, 0.01f, 0.01f));

                    // --- POSTURA ---
                    transformNode.setLocalRotation(Quaternion.axisAngle(new Vector3(1.0f, 0.0f, 0.0f), -90.0f));
                    transformNode.getRotationController().setEnabled(false); // Usamos nuestro listener para rotación manual

                    // --- HITTEST Y GESTOS ---
                    transformNode.setOnTouchListener((hitTestResult, motionEvent) -> {
                        switch (motionEvent.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                ultimoToqueX = motionEvent.getX();
                                inicioToqueX = motionEvent.getX();
                                inicioToqueY = motionEvent.getY();
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                // Rotación con 1 dedo
                                if (motionEvent.getPointerCount() == 1) {
                                    float deltaX = motionEvent.getX() - ultimoToqueX;
                                    ultimoToqueX = motionEvent.getX();
                                    Quaternion rotacionDelta = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), deltaX * 0.5f);
                                    transformNode.setLocalRotation(Quaternion.multiply(transformNode.getLocalRotation(), rotacionDelta));
                                }
                                return true;
                            case MotionEvent.ACTION_UP:
                                float movX = Math.abs(motionEvent.getX() - inicioToqueX);
                                float movY = Math.abs(motionEvent.getY() - inicioToqueY);
                                if (movX < 15 && movY < 15) mostrarInformacionDelProducto(idProducto);
                                return true;
                        }
                        return false;
                    });
                    transformNode.select();
                    modelosColocados.put(idProducto, transformNode);
                });
    }

    private void mostrarInformacionDelProducto(String idProducto) {
        FirebaseDatabase.getInstance().getReference("Productos").orderByChild("id").equalTo(idProducto)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot data : snapshot.getChildren()) {
                            new AlertDialog.Builder(activity_ar_scanner.this)
                                    .setTitle("Detalle: " + data.child("nombre").getValue(String.class))
                                    .setMessage("Precio: $" + String.valueOf(data.child("precio").getValue()) +
                                            "\nStock: " + String.valueOf(data.child("stock").getValue()))
                                    .setPositiveButton("Cerrar", null).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });
    }
}