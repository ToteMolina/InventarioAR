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
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
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
    private HashMap<String, String[]> diccionarioProductos = new HashMap<>();
    private HashMap<String, TransformableNode> modelosColocados = new HashMap<>();
    private float ultimoToqueX = 0;
    private float inicioToqueX = 0;
    private float inicioToqueY = 0;
    private float ultimoToqueY = 0;
    private String modeloPendienteUrl = null;
    private String idProductoPendiente = null;
    private boolean modoColocacion = false;

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


            arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {

                if (modoColocacion && modeloPendienteUrl != null) {

                    Anchor anchorDetectado = hitResult.createAnchor();


                    descargarYColocarModeloEnPiso(anchorDetectado, modeloPendienteUrl, idProductoPendiente);


                    modoColocacion = false;
                    modeloPendienteUrl = null;
                }
            });
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
                            String nombre = productoSnap.child("nombre").getValue(String.class);
                            String imagenUrl = productoSnap.child("imagenUrl").getValue(String.class);
                            String modelo3DUrl = productoSnap.child("modelo3DUrl").getValue(String.class);

                            if (id != null && imagenUrl != null) {
                                if (imagenUrl.contains("/upload/")) {
                                    imagenUrl = imagenUrl.replace("/upload/", "/upload/w_600/");
                                }

                                Bitmap fotoBitmap = descargarImagenDesdeURL(imagenUrl);
                                if (fotoBitmap != null) {
                                    synchronized (baseDeDatosVisual) {
                                        try {
                                            baseDeDatosVisual.addImage(id, fotoBitmap, 0.15f);
                                            diccionarioProductos.put(id, new String[]{modelo3DUrl, nombre});
                                        } catch (Exception e) {
                                            Log.e("ARCore", "Imagen rechazada: " + e.getMessage());
                                        }
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
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(urlString).openConnection();
            connection.connect();
            java.io.InputStream input = connection.getInputStream();

            android.graphics.BitmapFactory.Options opciones = new android.graphics.BitmapFactory.Options();

            opciones.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;

            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input, null, opciones);
            input.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void vigilarCamara(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) return;

        for (AugmentedImage imagen : frame.getUpdatedTrackables(AugmentedImage.class)) {
            if (imagen.getTrackingState() == TrackingState.TRACKING && !modelosColocados.containsKey(imagen.getName())) {

                String idProducto = imagen.getName();


                String[] datosProducto = diccionarioProductos.get(idProducto);

                if (datosProducto != null) {

                    String url = datosProducto[0];
                    String nombreReal = datosProducto[1];

                    modeloPendienteUrl = url;
                    idProductoPendiente = idProducto;
                    modoColocacion = true;

                    modelosColocados.put(idProductoPendiente, null);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "¡" + nombreReal + " detectado! Toca los puntos blancos en el piso.", Toast.LENGTH_LONG).show();
                    });
                }
            }
        }
    }

    private void descargarYColocarModeloEnPiso(Anchor anchorDetectado, String modeloURL, String idProducto) {
        ModelRenderable.builder().setSource(this, Uri.parse(modeloURL)).setIsFilamentGltf(true).build()
                .thenAccept(modelRenderable -> {


                    AnchorNode anchorNode = new AnchorNode(anchorDetectado);
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

                                        String nombreReal = data.child("nombre").getValue(String.class);
                                        String precioReal = String.valueOf(data.child("precio").getValue());
                                        Integer stockReal = data.child("stock").getValue(Integer.class);

                                        if (stockReal == null) {
                                            stockReal = 0;
                                        }

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


                                com.google.ar.sceneform.Node textNode = new com.google.ar.sceneform.Node() {
                                    @Override
                                    public void onUpdate(FrameTime frameTime) {
                                        super.onUpdate(frameTime);
                                        if (getScene() == null || getScene().getCamera() == null) return;


                                        Vector3 modelPos = transformNode.getWorldPosition();
                                        Vector3 cameraPos = getScene().getCamera().getWorldPosition();
                                        float escalaVisual = transformNode.getWorldScale().y;


                                        Vector3 direccionHaciaUsuario = Vector3.subtract(cameraPos, modelPos);
                                        direccionHaciaUsuario.y = 0;
                                        direccionHaciaUsuario = direccionHaciaUsuario.normalized();


                                        float distanciaArriba = 0.8f * escalaVisual;
                                        float distanciaAdelante = 0.3f * escalaVisual;


                                        Vector3 nuevaPosicion = new Vector3(
                                                modelPos.x + (direccionHaciaUsuario.x * distanciaAdelante),
                                                modelPos.y + distanciaArriba,
                                                modelPos.z + (direccionHaciaUsuario.z * distanciaAdelante)
                                        );
                                        setWorldPosition(nuevaPosicion);


                                        Vector3 direction = Vector3.subtract(cameraPos, getWorldPosition());
                                        direction.y = 0;

                                        Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
                                        lookRotation = Quaternion.multiply(lookRotation, Quaternion.axisAngle(new Vector3(0, 1, 0), 180f));
                                        setWorldRotation(lookRotation);
                                    }
                                };

                                textNode.setParent(anchorNode);
                                textNode.setRenderable(viewRenderable);


                                transformNode.getScaleController().setEnabled(true);
                                transformNode.getScaleController().setMinScale(0.01f);
                                transformNode.getScaleController().setMaxScale(2.0f);

                                transformNode.getTranslationController().setEnabled(true);

                                transformNode.getRotationController().setEnabled(true);


                                transformNode.setOnTapListener((hitTestResult, motionEvent) -> {

                                    mostrarInformacionDelProducto(idProducto);
                                });

                                transformNode.select();
                                modelosColocados.put(idProducto, transformNode);
                            });


                    transformNode.getScaleController().setEnabled(true);
                    transformNode.getScaleController().setMinScale(1f);
                    transformNode.getScaleController().setMaxScale(2.0f);

                    transformNode.getTranslationController().setEnabled(true);
                    transformNode.getRotationController().setEnabled(false);

                    transformNode.setOnTouchListener((hitTestResult, motionEvent) -> {
                        int cantidadDedos = motionEvent.getPointerCount();

                        switch (motionEvent.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                inicioToqueX = motionEvent.getX();
                                inicioToqueY = motionEvent.getY();
                                return false;
                            case MotionEvent.ACTION_POINTER_DOWN:
                                if (cantidadDedos == 2) {
                                    ultimoToqueX = motionEvent.getX(0);
                                    ultimoToqueY = motionEvent.getY(0);
                                }
                                return false;
                            case MotionEvent.ACTION_MOVE:
                                if (cantidadDedos == 2) {

                                    float deltaX = motionEvent.getX(0) - ultimoToqueX;
                                    float deltaY = motionEvent.getY(0) - ultimoToqueY;

                                    ultimoToqueX = motionEvent.getX(0);
                                    ultimoToqueY = motionEvent.getY(0);

                                    Quaternion rotacionY = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), deltaX * 0.2f);
                                    Quaternion rotacionX = Quaternion.axisAngle(new Vector3(1.0f, 0.0f, 0.0f), deltaY * 0.2f);
                                    Quaternion rotacionTotal = Quaternion.multiply(rotacionX, rotacionY);

                                    transformNode.setLocalRotation(Quaternion.multiply(transformNode.getLocalRotation(), rotacionTotal));
                                }
                                return false;

                            case MotionEvent.ACTION_UP:

                                float movX = Math.abs(motionEvent.getX() - inicioToqueX);
                                float movY = Math.abs(motionEvent.getY() - inicioToqueY);

                                if (movX < 15 && movY < 15) {
                                    mostrarInformacionDelProducto(idProducto);
                                    return true;
                                }
                                return false;
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