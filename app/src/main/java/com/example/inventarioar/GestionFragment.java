package com.example.inventarioar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.inventarioar.models.Producto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
    private DatabaseReference databaseReference;

    private Uri uriImagen = null;
    private Uri uriModelo = null;
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
        btnGuardar.setOnClickListener(v -> validarYIniciarSubida());
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
        if (uriImagen != null && uriModelo != null) {
            tvEstadoArchivos.setText("¡Imagen y Modelo 3D listos!");
            tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (uriImagen != null) {
            tvEstadoArchivos.setText("Imagen lista. Falta el Modelo 3D.");
        } else if (uriModelo != null) {
            tvEstadoArchivos.setText("Modelo 3D listo. Falta la Imagen.");
        }
    }

    private void validarYIniciarSubida() {
        String nombre = txtNombre.getText().toString().trim();
        String precioStr = txtPrecio.getText().toString().trim();
        String stockStr = txtStock.getText().toString().trim();
        String categoria = spCategoria.getText().toString();

        if (nombre.isEmpty() || precioStr.isEmpty() || stockStr.isEmpty() || uriImagen == null || uriModelo == null) {
            Toast.makeText(getContext(), "Completa el formulario y selecciona ambos archivos", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Subiendo archivos...");

        MediaManager.get().upload(uriImagen)
                .option("unsigned", true)
                .option("upload_preset", "v28m3mxs")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        urlFotoCloudinary = (String) resultData.get("secure_url");
                        subirModelo3DCloudinary();
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(getContext(), "Error Foto: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar Producto");
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void subirModelo3DCloudinary() {
        MediaManager.get().upload(uriModelo)
                .option("unsigned", true)
                .option("upload_preset", "v28m3mxs")
                .option("resource_type", "raw")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        urlModeloCloudinary = (String) resultData.get("secure_url");
                        guardarFinalEnFirebase();
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(getContext(), "Error Modelo: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar Producto");
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void guardarFinalEnFirebase() {
        String nombre = txtNombre.getText().toString().trim();
        String descripcion = txtDescripcion.getText().toString().trim();
        double precio = Double.parseDouble(txtPrecio.getText().toString().trim());
        int stock = Integer.parseInt(txtStock.getText().toString().trim());
        String categoria = spCategoria.getText().toString();
        HashMap<String, Integer> sucursalesEnCero = new HashMap<>();
        sucursalesEnCero.put("sucursal_centro", 0);
        sucursalesEnCero.put("sucursal_metrocentro", 0);
        sucursalesEnCero.put("sucursal_fmo", 0);

        String id = databaseReference.push().getKey();



        Producto nuevoProducto = new Producto(id, nombre, categoria, descripcion, precio, stock);
        nuevoProducto.setImagenUrl(urlFotoCloudinary);
        nuevoProducto.setModelo3DUrl(urlModeloCloudinary);
        nuevoProducto.setStockPorSucursal(sucursalesEnCero);
        if (id != null) {
            databaseReference.child(id).setValue(nuevoProducto)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "¡Producto y multimedia registrados!", Toast.LENGTH_LONG).show();
                        limpiarFormulario();
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
        tvEstadoArchivos.setText("Falta seleccionar imagen y modelo 3D");
        tvEstadoArchivos.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        btnGuardar.setEnabled(true);
        btnGuardar.setText("Guardar Producto");
    }
}