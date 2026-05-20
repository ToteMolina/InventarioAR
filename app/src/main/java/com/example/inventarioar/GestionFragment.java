package com.example.inventarioar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.inventarioar.models.Producto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GestionFragment extends Fragment {

    private TextInputEditText txtNombre, txtDescripcion, txtPrecio, txtStock;
    private AutoCompleteTextView spCategoria;
    private MaterialButton btnGuardar, btnGaleria, btnCamara, btnSeleccionarModelo;
    private TextView tvEstadoArchivos;
    private FloatingActionButton btnRegresar;
    private ImageView ivVistaPrevia;
    private DatabaseReference databaseReference;
    private Uri uriImagen = null;
    private Uri uriModelo = null;
    private String urlImagenExistente = "";
    private String urlModeloExistente = "";
    private String rutaCamara;
    private String urlFotoCloudinary = "";
    private String urlModeloCloudinary = "";

    public GestionFragment() {
        // Required empty public constructor
    }

    private final ActivityResultLauncher<PickVisualMediaRequest> selectorGaleria =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    uriImagen = uri;
                    actualizarTextoEstado();
                }
            });

    private final ActivityResultLauncher<Uri> selectorCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), resultado -> {
                if (resultado) {
                    actualizarTextoEstado();
                } else {
                    Toast.makeText(getContext(), "No se tomó la foto", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> pedirPermisoCamara =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), resultado -> {
                if (resultado) {
                    abrirCamaraNativa();
                } else {
                    Toast.makeText(getContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> selectorModelo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uriModelo = uri;
                    actualizarTextoEstado();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", "dwwh0ivoe");
            config.put("api_key", "574758944718996");
            config.put("api_secret", "-J5hIeUbQ4zB-DLwOnv1Wt0XeYE");
            MediaManager.init(requireContext(), config);
        } catch (IllegalStateException e) {
            // Ya inicializado
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gestion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtNombre = view.findViewById(R.id.txtNombre);
        txtDescripcion = view.findViewById(R.id.txtDescripcion);
        txtPrecio = view.findViewById(R.id.txtPrecio);
        txtStock = view.findViewById(R.id.txtStock);
        spCategoria = view.findViewById(R.id.spCategoria);
        btnGuardar = view.findViewById(R.id.btnGuardar);
        btnGaleria = view.findViewById(R.id.btnGaleria);
        btnCamara = view.findViewById(R.id.btnCamara);
        btnSeleccionarModelo = view.findViewById(R.id.btnSeleccionarModelo);
        tvEstadoArchivos = view.findViewById(R.id.tvEstadoArchivos);
        btnRegresar = view.findViewById(R.id.btnRegresar);
        ivVistaPrevia = view.findViewById(R.id.ivVistaPrevia);

        String productoId = getArguments() != null ? getArguments().getString("productoId") : null;

        if (productoId != null){
            FirebaseDatabase.getInstance().getReference("Productos")
                    .child(productoId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Producto p = snapshot.getValue(Producto.class);
                            if (p != null){
                                txtNombre.setText(p.getNombre());
                                txtDescripcion.setText(p.getDescripcion());
                                txtPrecio.setText(String.valueOf(p.getPrecio()));
                                txtStock.setText(String.valueOf(p.getStock()));
                                spCategoria.setText(p.getCategoria(), false);

                                // guardamos las URLs que ya existen en la base de datos
                                urlImagenExistente = p.getImagenUrl() != null ? p.getImagenUrl() : "";
                                urlModeloExistente = p.getModelo3DUrl() != null ? p.getModelo3DUrl() : "";

                                if (!urlImagenExistente.isEmpty()){
                                    ivVistaPrevia.setImageTintList(null);
                                    ivVistaPrevia.setPadding(0, 0, 0, 0);
                                    Glide.with(requireContext()).load(urlImagenExistente).into(ivVistaPrevia);
                                }

                                btnGuardar.setText("Actualizar producto");
                                btnGuardar.setTag(productoId); // guardar el id para usarlo al actualizar
                                actualizarTextoEstado(); // refrescamos el mensaje de los archivos
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                        }
                    });
        }

        databaseReference = FirebaseDatabase.getInstance().getReference("Productos");

        String[] categorias = {
                "Mobiliario",
                "Electrónica",
                "Decoración",
                "Herramientas",
                "Otros"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categorias);
        spCategoria.setAdapter(adapter);

        btnGaleria.setOnClickListener(v -> abrirGaleria());
        btnCamara.setOnClickListener(v -> validarYAbriCamara());
        btnSeleccionarModelo.setOnClickListener(v -> selectorModelo.launch("*/*"));
        btnGuardar.setOnClickListener(v -> validarIniciarSubida());
        btnRegresar.setOnClickListener(v-> NavHostFragment.findNavController(this).navigateUp());
    }

    private void abrirGaleria() {
        selectorGaleria.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void validarYAbriCamara() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamaraNativa();
        } else {
            pedirPermisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirCamaraNativa() {
        try {
            File archivoImagen = crearArchivoImagenTemp();
            rutaCamara = archivoImagen.getAbsolutePath();
            uriImagen = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    archivoImagen
            );
            selectorCamara.launch(uriImagen);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error al preparar la cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private File crearArchivoImagenTemp() throws IOException {
        String nombreArchivo = "CAM_" + System.currentTimeMillis() + ".jpg";
        File directorio = new File(requireContext().getFilesDir(), "imagenes");
        if (!directorio.exists()) {
            directorio.mkdirs();
        }
        return new File(directorio, nombreArchivo);
    }

    private void actualizarTextoEstado() {
        boolean tieneImagen = (uriImagen != null) || (!urlImagenExistente.isEmpty());
        boolean tieneModelo = (uriModelo != null) || (!urlModeloExistente.isEmpty());

        if (tieneImagen && tieneModelo) {
            if (uriImagen != null || uriModelo != null) {
                tvEstadoArchivos.setText("¡Archivos listos para guardar!");
                tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvEstadoArchivos.setText("Archivos actuales cargados. Solo selecciona nuevos si deseas reemplazarlos.");
                tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            }
        } else if (tieneImagen) {
            tvEstadoArchivos.setText("Imagen lista. Falta el Modelo 3D.");
            tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if (tieneModelo) {
            tvEstadoArchivos.setText("Modelo 3D listo. Falta la Imagen.");
            tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            tvEstadoArchivos.setText("Falta seleccionar imagen y modelo 3D");
            tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (uriImagen != null){
            ivVistaPrevia.setImageTintList(null);
            ivVistaPrevia.setPadding(0, 0, 0, 0);
            Glide.with(requireContext()).load(uriImagen).into(ivVistaPrevia);
        }
    }

    private void validarIniciarSubida() {
        String nombre = txtNombre.getText().toString().trim();
        String precioStr = txtPrecio.getText().toString().trim();
        String stockStr = txtStock.getText().toString().trim();
        String categoria = spCategoria.getText().toString();

        boolean tieneImagen = (uriImagen != null) || (!urlImagenExistente.isEmpty());
        boolean tieneModelo = (uriModelo != null) || (!urlModeloExistente.isEmpty());

        if (nombre.isEmpty() || precioStr.isEmpty() || stockStr.isEmpty() || categoria.isEmpty() || !tieneImagen || !tieneModelo) {
            Toast.makeText(getContext(), "Completa el formulario y asegúrate de tener ambos archivos", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Procesando datos...");

        procesarSubidaImagen();
    }

    private void procesarSubidaImagen() {
        // si el usuario seleccionó una nueva imagen, la subimos a Cloudinary
        if (uriImagen != null) {
            MediaManager.get().upload(uriImagen)
                    .option("unsigned", true)
                    .option("upload_preset", "v28m3mxs")
                    .callback(new UploadCallback() {
                        @Override public void onStart(String requestId) {}
                        @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            urlFotoCloudinary = (String) resultData.get("secure_url");
                            procesarSubidaModelo();
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Toast.makeText(getContext(), "Error Foto: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                            restaurarBotonGuardar();
                        }

                        @Override public void onReschedule(String requestId, ErrorInfo error) {}
                    }).dispatch();
        } else {
            // si no seleccionó nueva imagen, reutilizamos la que ya estaba (modo edición)
            urlFotoCloudinary = urlImagenExistente;
            procesarSubidaModelo();
        }
    }

    private void procesarSubidaModelo() {
        // si el usuario seleccionó un nuevo modelo 3D lo subimos
        if (uriModelo != null) {
            MediaManager.get().upload(uriModelo)
                    .option("unsigned", true)
                    .option("upload_preset", "v28m3mxs")
                    .option("resource_type", "raw")
                    .callback(new UploadCallback() {
                        @Override public void onStart(String requestId) {}
                        @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            urlModeloCloudinary = (String) resultData.get("secure_url");
                            guardarFinalEnFirebase();
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Toast.makeText(getContext(), "Error Modelo: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                            restaurarBotonGuardar();
                        }

                        @Override public void onReschedule(String requestId, ErrorInfo error) {}
                    }).dispatch();
        } else {
            // si no seleccionó nuevo modelo, reutilizamos el que ya estaba (modo edición)
            urlModeloCloudinary = urlModeloExistente;
            guardarFinalEnFirebase();
        }
    }

    private void restaurarBotonGuardar() {
        btnGuardar.setEnabled(true);
        boolean esEdicion = btnGuardar.getTag() != null;
        btnGuardar.setText(esEdicion ? "Actualizar producto" : "Guardar Producto");
    }

    private void guardarFinalEnFirebase() {
        String nombre = txtNombre.getText().toString().trim();
        String descripcion = txtDescripcion.getText().toString().trim();
        double precio = Double.parseDouble(txtPrecio.getText().toString().trim());
        int stock = Integer.parseInt(txtStock.getText().toString().trim());
        String categoria = spCategoria.getText().toString();

        String idExistente = btnGuardar.getTag() != null ? (String) btnGuardar.getTag() : null;
        String id = idExistente != null ? idExistente : databaseReference.push().getKey();

        HashMap<String, Integer> stockGPS = new HashMap<>();
        stockGPS.put("bodega_central", stock);

        Producto nuevoProducto = new Producto(id, nombre, categoria, descripcion, precio, stock);
        nuevoProducto.setImagenUrl(urlFotoCloudinary);
        nuevoProducto.setModelo3DUrl(urlModeloCloudinary);
        nuevoProducto.setStockPorSucursal(stockGPS);

        if (id != null) {
            databaseReference.child(id).setValue(nuevoProducto)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), idExistente != null ? "¡Producto actualizado!" : "¡Producto registrado!", Toast.LENGTH_LONG).show();
                        limpiarFormulario();
                        NavHostFragment.findNavController(GestionFragment.this).navigateUp();
                    });
        }
    }

    private void limpiarFormulario() {
        txtNombre.setText("");
        txtDescripcion.setText("");
        txtPrecio.setText("");
        txtStock.setText("");
        spCategoria.setText("Mobiliario", false);
        txtNombre.requestFocus();

        uriImagen = null;
        uriModelo = null;
        urlImagenExistente = "";
        urlModeloExistente = "";
        ivVistaPrevia.setImageResource(R.drawable.ic_inventory);
        ivVistaPrevia.setPadding(60, 60, 60, 60);
        ivVistaPrevia.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
        btnGuardar.setTag(null); // quita el modo edición

        actualizarTextoEstado();
        restaurarBotonGuardar();
    }
}