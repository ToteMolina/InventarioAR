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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import com.google.ar.sceneform.rendering.ViewRenderable;
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
    private float ultimoToqueY = 0;


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
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);

        View loadingLayout = findViewById(R.id.loadingLayout);
        loadingLayout.setVisibility(View.VISIBLE);

        FirebaseDatabase.getInstance().getReference("Productos").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalProductos = (int) snapshot.getChildrenCount();

                if (totalProductos == 0) {
                    loadingLayout.setVisibility(View.GONE);
                    return;
                }


                ExecutorService executor = Executors.newFixedThreadPool(4);
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(totalProductos);
                Handler handler = new Handler(Looper.getMainLooper());
                AugmentedImageDatabase baseDeDatosVisual = new AugmentedImageDatabase(session);

                for (DataSnapshot productoSnap : snapshot.getChildren()) {
                    executor.execute(() -> {
                        try {
                            String id = productoSnap.child("id").getValue(String.class);
                            String imagenUrl = productoSnap.child("imagenUrl").getValue(String.class);
                            String modelo3DUrl = productoSnap.child("modelo3DUrl").getValue(String.class);

                            if (id != null && imagenUrl != null) {
                                Bitmap fotoBitmap = descargarImagenDesdeURL(imagenUrl);
                                if (fotoBitmap != null) {
                                    synchronized (baseDeDatosVisual) {
                                        baseDeDatosVisual.addImage(id, fotoBitmap, 0.15f);
                                        diccionarioProductos.put(id, modelo3DUrl);
                                    }
                                    fotoBitmap.recycle();
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                new Thread(() -> {
                    try {
                        latch.await();
                        handler.post(() -> {
                            if (isDestroyed()) return;
                            config.setAugmentedImageDatabase(baseDeDatosVisual);
                            session.configure(config);

                            // Ocultamos la ruedita
                            loadingLayout.setVisibility(View.GONE);
                            Toast.makeText(activity_ar_scanner.this, "Inventario listo", Toast.LENGTH_SHORT).show();
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                loadingLayout.setVisibility(View.GONE);
            }
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

         
                    View infoView = View.inflate(this, R.layout.ar_label_producto, null);
                    TextView txtNombre = infoView.findViewById(R.id.txtNombre);
                    TextView txtDetalles = infoView.findViewById(R.id.txtDetalles);


                    txtNombre.setText("Cargando nombre...");
                    txtDetalles.setText("Buscando precio...");


                    FirebaseDatabase.getInstance().getReference("Productos").orderByChild("id").equalTo(idProducto)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    for (DataSnapshot data : snapshot.getChildren()) {
                                        // Obtenemos los datos reales
                                        String nombreReal = data.child("nombre").getValue(String.class);
                                        String precioReal = String.valueOf(data.child("precio").getValue());
                                        Integer stockReal = data.child("stock").getValue(Integer.class);

                                        if (stockReal == null) {
                                            stockReal = 0;
                                        }
                                        // Actualizamos la etiqueta flotante 3D
                                        txtNombre.setText(nombreReal);
                                        txtDetalles.setText("Precio: $" + precioReal +"\n" +
                                            "Stock: " + stockReal);
                                    }
                                }
                                @Override
                                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                            });

                    ViewRenderable.builder().setView(this, infoView).build()
                            .thenAccept(viewRenderable -> {
                                TransformableNode textNode = new TransformableNode(arFragment.getTransformationSystem());
                                textNode.setParent(transformNode);
                                textNode.setRenderable(viewRenderable);
                                textNode.setLocalPosition(new Vector3(0.0f, 0.3f, 0.2f));
                                textNode.getScaleController().setEnabled(false);
                                textNode.getRotationController().setEnabled(false);
                                textNode.getTranslationController().setEnabled(false);
                                textNode.setLocalScale(new Vector3(1f, 1f, 1f));
                            });


                    transformNode.getScaleController().setEnabled(true);
                    transformNode.getScaleController().setMinScale(0.01f);
                    transformNode.getScaleController().setMaxScale(2.0f);


                    transformNode.getRotationController().setEnabled(false); // Apagamos el nativo para usar el giro libre 3D

                    transformNode.setOnTouchListener((hitTestResult, motionEvent) -> {

                        if (motionEvent.getPointerCount() > 1) {
                            return false;
                        }

                        switch (motionEvent.getAction()) {
                            case MotionEvent.ACTION_DOWN:

                                ultimoToqueX = motionEvent.getX();
                                ultimoToqueY = motionEvent.getY();
                                inicioToqueX = motionEvent.getX();
                                inicioToqueY = motionEvent.getY();
                                return true;

                            case MotionEvent.ACTION_MOVE:
                                float deltaX = motionEvent.getX() - ultimoToqueX;
                                float deltaY = motionEvent.getY() - ultimoToqueY;

                                ultimoToqueX = motionEvent.getX();
                                ultimoToqueY = motionEvent.getY();

                                Quaternion rotacionY = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), deltaX * 0.5f);
                                Quaternion rotacionX = Quaternion.axisAngle(new Vector3(1.0f, 0.0f, 0.0f), deltaY * 0.5f);
                                Quaternion rotacionTotal = Quaternion.multiply(rotacionX, rotacionY);

                                transformNode.setLocalRotation(Quaternion.multiply(transformNode.getLocalRotation(), rotacionTotal));
                                return true;

                            case MotionEvent.ACTION_UP:
                                float movX = Math.abs(motionEvent.getX() - inicioToqueX);
                                float movY = Math.abs(motionEvent.getY() - inicioToqueY);


                                if (movX < 15 && movY < 15) {
                                    mostrarInformacionDelProducto(idProducto);
                                }
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
                            new MaterialAlertDialogBuilder(activity_ar_scanner.this)
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