package com.example.inventarioar;

import android.content.Intent;
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
import com.google.android.material.snackbar.Snackbar;
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
import com.google.ar.sceneform.collision.Box;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class activity_ar_scanner extends AppCompatActivity {

    //Variables
    private ArFragment arFragment;
    private ProgressBar loadingProgressBar;


    private HashMap<String, String[]> diccionarioProductos = new HashMap<>(); //guarda el id del producto y el enlace del modelo 3d para descargardo y los asigna a una llave
    private HashMap<String, TransformableNode> modelosColocados = new HashMap<>(); //sirve como un validador para no poner dos veces el mismo objeto en el suelo
    private float ultimoToqueX = 0;
    private float ultimoToqueY = 0;

    //variables donde se guarda el modelo detectado antes de que el usuario toque el suelo
    private String modeloPendienteUrl = null;
    private String idProductoPendiente = null;
    //indicador para ver si se ha detectado o no un producto y colocarlo en el suelo
    private boolean modoColocacion = false;
    private View panelInstrucciones;
    private TextView txtInstruccion;

    private String sucursalDetectadaKey = "sucursal_metrocentro";
    private String sucursalDetectadaNombre = "Metrocentro San Miguel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_scanner);

        // Capturamos los datos enviados desde el fragmento de inventario
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("sucursalKey")) {
                sucursalDetectadaKey = intent.getStringExtra("sucursalKey");
            }
            if (intent.hasExtra("nombreSucursal")) {
                sucursalDetectadaNombre = intent.getStringExtra("nombreSucursal");
            }
        }

        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        panelInstrucciones = findViewById(R.id.panelInstrucciones);
        txtInstruccion = findViewById(R.id.txtInstruccion);

        MaterialButton btnCerrar = findViewById(R.id.btnCerrarAR);
        if (btnCerrar != null) btnCerrar.setOnClickListener(v -> finish());

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

        if (arFragment != null) {
            //inicializacion de la camara del arcore
            //instruccion para descargar la base de datos y que tenga el contexto de lo que debe de buscar
            arFragment.setOnSessionConfigurationListener(this::configurarMotorDeImagenes);
            //le decimos que vigile la camara todo el tiempo en busca de coinicidencias
            arFragment.getArSceneView().getScene().addOnUpdateListener(this::vigilarCamara);

            //
            arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
                if (modoColocacion && modeloPendienteUrl != null) {

                    panelInstrucciones.setVisibility(View.GONE); //ocultamos el mensaje de la imagen detectada
                    Anchor anchorDetectado = hitResult.createAnchor(); //crea un ancla en las coodernadas donde el uusario toco
                    descargarYColocarModeloEnPiso(anchorDetectado, modeloPendienteUrl, idProductoPendiente);
                    modoColocacion = false; //coloca el semaforo en rojo para limpiar los toques
                    modeloPendienteUrl = null; //asignamos en null el modelo detectado
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
                    AlertDialog dialogCerrarCamara = new MaterialAlertDialogBuilder(activity_ar_scanner.this)
                            .setTitle("Inventario Vacío")
                            .setMessage("No hay productos registrados en la base de datos en este momento.")
                            .setPositiveButton("Entendido", (dialog, which) -> {
                                // Al darle "Entendido", cerramos la pantalla porque no hay nada que hacer aquí
                                finish();
                            })
                            .setCancelable(false) // Esto evita que el usuario lo cierre tocando fuera del cuadro
                            .create();
                        dialogCerrarCamara.show();

                    int modoPantalla = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                    boolean esModoOscuro = (modoPantalla == android.content.res.Configuration.UI_MODE_NIGHT_YES);

                    int colorTexto = esModoOscuro ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

                    // 4. Pintamos el botón "Cancelar" con el color inteligente
                    dialogCerrarCamara.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(colorTexto);
                    return;
                }
                ExecutorService executor = Executors.newFixedThreadPool(4);
                CountDownLatch latch = new java.util.concurrent.CountDownLatch(totalProductos);
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
                                    imagenUrl = imagenUrl.replace("/upload/", "/upload/w_1000/");
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
                            latch.countDown(); //va descontando de uno a uno cuando la imagen ya ha sido descargada
                        }
                    });
                }
                new Thread(() -> { //funciona hasta que el countdown ya ha llegado a cero
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
            public void onCancelled(@NonNull DatabaseError error) {
                loadingLayout.setVisibility(View.GONE);
            }
        });
    }

    private Bitmap descargarImagenDesdeURL(String urlString) {
        try {
            //Se conecta a internet buscando la dirección de la foto (URL)
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.connect();
            //Abre una "tubería" por donde van a entrar los datos de la imagen
            InputStream input = connection.getInputStream();
            BitmapFactory.Options opciones = new BitmapFactory.Options();
            opciones.inPreferredConfig = Bitmap.Config.ARGB_8888; //se asegura que la imagen se descargue en alta calidad
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, opciones);
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
                        txtInstruccion.setText("¡" + nombreReal + " detectado!\nToca los puntos blancos en el piso para colocarlo.");
                        panelInstrucciones.setVisibility(View.VISIBLE); // Se muestra gigante y claro
                    });
                }
            }
        }
    }

    private void descargarYColocarModeloEnPiso(Anchor anchorDetectado, String modeloURL, String idProducto) {
        ModelRenderable.builder()
                .setSource(this, Uri.parse(modeloURL))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(modelRenderable -> {

                    AnchorNode anchorNode = new AnchorNode(anchorDetectado);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    TransformableNode transformNode = new TransformableNode(arFragment.getTransformationSystem());
                    transformNode.setParent(anchorNode);
                    transformNode.setRenderable(modelRenderable);

                    //mide el objeto 3D que descargué, calcula dónde está su 'techo'
                    // para poner el letrero, y ajusta la escala automáticamente para que cualquier
                    // producto mida 40 centímetros al aparecer en el piso,
                    // poniéndole límites al zoom del usuario.
                    float alturaLocalDelTecho = 0.1f;
                    Box collisionBox = (Box) modelRenderable.getCollisionShape();
                    if (collisionBox != null) {
                        Vector3 size = collisionBox.getSize();
                        Vector3 center = collisionBox.getCenter();
                        alturaLocalDelTecho = center.y + (size.y / 2f);

                        float ladoMasGrande = Math.max(size.x, Math.max(size.y, size.z));
                        float escalaCalculada = 0.4f / ladoMasGrande;
                        transformNode.setLocalScale(new Vector3(escalaCalculada, escalaCalculada, escalaCalculada));
                        transformNode.getScaleController().setEnabled(true);
                        transformNode.getScaleController().setMinScale(escalaCalculada * 0.5f);
                        transformNode.getScaleController().setMaxScale(escalaCalculada * 3.0f);
                    }

                    final float techoOriginal = alturaLocalDelTecho;

                    View infoView = View.inflate(this, R.layout.ar_label_producto, null);
                    TextView txtNombre = infoView.findViewById(R.id.txtNombre);
                    TextView txtDetalles = infoView.findViewById(R.id.txtDetalles);

                    txtNombre.setText("Cargando...");
                    txtDetalles.setText("");

                    FirebaseDatabase.getInstance().getReference("Productos").orderByChild("id").equalTo(idProducto)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    for (DataSnapshot data : snapshot.getChildren()) {
                                        String nombreReal = data.child("nombre").getValue(String.class);
                                        String precioReal = String.valueOf(data.child("precio").getValue());

                                        // ETIQUETA FLOATING AR MINIMALISTA
                                        txtNombre.setText(nombreReal);
                                        txtDetalles.setText("Precio: $" + precioReal);
                                    }
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });

                    //crea el letrero de información y le aplica un método de actualización constante (onUpdate).
                    // Utiliza álgebra de vectores (Vector3) para asegurarse de que el letrero flote siempre arriba
                    // y adelante del producto, ajustándose automáticamente si el usuario hace zoom.
                    // Además, usa Quaternion para que el texto gire dinámicamente y siempre esté mirando de frente
                    // a la cámara del usuario.
                    ViewRenderable.builder().setView(this, infoView).build()
                            .thenAccept(viewRenderable -> {
                                Node textNode = new Node() {
                                    @Override
                                    public void onUpdate(FrameTime frameTime) {
                                        super.onUpdate(frameTime);
                                        if (getScene() == null || getScene().getCamera() == null) return;
                                        Vector3 modelPos = transformNode.getWorldPosition();
                                        Vector3 cameraPos = getScene().getCamera().getWorldPosition();
                                        float zoomActual = transformNode.getWorldScale().y;

                                        Vector3 direccionHaciaUsuario = Vector3.subtract(cameraPos, modelPos);
                                        direccionHaciaUsuario.y = 0;
                                        direccionHaciaUsuario = direccionHaciaUsuario.normalized();

                                        float distanciaArriba = (techoOriginal * zoomActual) + 0.15f;
                                        float distanciaAdelante = 0.2f * zoomActual;
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
                                textNode.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
                            });

                    transformNode.getTranslationController().setEnabled(true);
                    transformNode.getRotationController().setEnabled(false);

                    transformNode.setOnTouchListener((hitTestResult, motionEvent) -> {
                        int cantidadDedos = motionEvent.getPointerCount();
                        switch (motionEvent.getActionMasked()) {
                            case MotionEvent.ACTION_POINTER_DOWN:
                                if (cantidadDedos == 2) {
                                    ultimoToqueX = (motionEvent.getX(0) + motionEvent.getX(1)) / 2;
                                    ultimoToqueY = (motionEvent.getY(0) + motionEvent.getY(1)) / 2;
                                }
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if (cantidadDedos == 2) {
                                    float medioX = (motionEvent.getX(0) + motionEvent.getX(1)) / 2;
                                    float medioY = (motionEvent.getY(0) + motionEvent.getY(1)) / 2;
                                    float deltaX = medioX - ultimoToqueX;
                                    float deltaY = medioY - ultimoToqueY;
                                    ultimoToqueX = medioX;
                                    ultimoToqueY = medioY;
                                    Quaternion rotacionY = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), deltaX * 0.5f);
                                    Quaternion rotacionX = Quaternion.axisAngle(new Vector3(1.0f, 0.0f, 0.0f), deltaY * 0.5f);
                                    Quaternion rotacionTotal = Quaternion.multiply(rotacionX, rotacionY);
                                    transformNode.setLocalRotation(Quaternion.multiply(transformNode.getLocalRotation(), rotacionTotal));
                                }
                                break;
                        }
                        return false;
                    });

                    transformNode.setOnTapListener((hitTestResult, motionEvent) -> {
                        mostrarInformacionDelProducto(idProducto);
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
                            String nombre = data.child("nombre").getValue(String.class);
                            String precio = String.valueOf(data.child("precio").getValue());
                            String descripcion = data.child("descripcion").getValue(String.class);

                            if (descripcion == null || descripcion.isEmpty()) {
                                descripcion = "Sin descripción disponible.";
                            }

                            StringBuilder mensajeDialog = new StringBuilder();
                            mensajeDialog.append("Precio: $").append(precio).append("\n");
                            mensajeDialog.append("Descripción: ").append(descripcion).append("\n\n");
                            mensajeDialog.append("DISPONIBILIDAD DEL PRODUCTO:\n\n");

                            DataSnapshot stockPorSucursalSnap = data.child("stockPorSucursal");

                            if (stockPorSucursalSnap.exists()) {
                                long stockLocal = -1;
                                StringBuilder otrasSucursalesConStock = new StringBuilder();
                                boolean hayOtrasOpciones = false;

                                for (DataSnapshot sucursal : stockPorSucursalSnap.getChildren()) {
                                    String keySucursal = sucursal.getKey();
                                    long cantidad = 0;
                                    if (sucursal.getValue() instanceof Long) {
                                        cantidad = (long) sucursal.getValue();
                                    } else if (sucursal.getValue() instanceof Integer) {
                                        cantidad = (long)(int) sucursal.getValue();
                                    }

                                    String nombreSucursalLegible = keySucursal;
                                    if (keySucursal.equals("sucursal_metrocentro")) nombreSucursalLegible = "Metrocentro San Miguel";
                                    else if (keySucursal.equals("sucursal_centro")) nombreSucursalLegible = "Sucursal Centro";
                                    else if (keySucursal.equals("sucursal_fmo")) nombreSucursalLegible = "FMO - UES";

                                    if (keySucursal.equalsIgnoreCase(sucursalDetectadaKey)) {
                                        stockLocal = cantidad;
                                    } else {
                                        if (cantidad > 0) {
                                            otrasSucursalesConStock.append(nombreSucursalLegible).append(": ").append(cantidad).append(" unidades\n");
                                            hayOtrasOpciones = true;
                                        }
                                    }
                                }

                                // Lógica inteligente de sucursales en el Alert
                                if (stockLocal > 0) {
                                    mensajeDialog.append("Sucursal actual (").append(sucursalDetectadaNombre).append("):\n");
                                    mensajeDialog.append(stockLocal).append(" unidades disponibles\n\n");
                                    if (hayOtrasOpciones) {
                                        mensajeDialog.append("También disponible en:\n").append(otrasSucursalesConStock.toString());
                                    }
                                } else if (stockLocal == 0) {
                                    mensajeDialog.append("Sucursal actual (").append(sucursalDetectadaNombre).append("):\n");
                                    mensajeDialog.append("Sin unidades disponibles\n\n");
                                    if (hayOtrasOpciones) {
                                        mensajeDialog.append("Opciones en sucursales cercanas:\n").append(otrasSucursalesConStock.toString());
                                    } else {
                                        mensajeDialog.append("Producto completamente agotado en todas las sucursales.");
                                    }
                                } else {
                                    if (hayOtrasOpciones) {
                                        mensajeDialog.append("Opciones disponibles:\n").append(otrasSucursalesConStock.toString());
                                    } else {
                                        mensajeDialog.append("Sin inventario registrado para este producto.");
                                    }
                                }
                            } else {
                                Integer stockReal = data.child("stock").getValue(Integer.class);
                                if (stockReal == null) stockReal = 0;
                                mensajeDialog.append("Stock general disponible: ").append(stockReal).append(" unidades");
                            }

                            AlertDialog dialogoInfo = new MaterialAlertDialogBuilder(activity_ar_scanner.this)
                                    .setTitle(nombre)
                                    .setMessage(mensajeDialog.toString())
                                    .setPositiveButton("Cerrar", null)
                                    .create();
                            dialogoInfo.show();
                            int modoPantalla = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                            boolean esModoOscuro = (modoPantalla == android.content.res.Configuration.UI_MODE_NIGHT_YES);

                            int colorCerrar = esModoOscuro ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

                            dialogoInfo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                                    .setTextColor(colorCerrar);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
}