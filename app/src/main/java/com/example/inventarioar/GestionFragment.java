package com.example.inventarioar;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.inventarioar.models.Producto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class GestionFragment extends Fragment {

    private TextInputEditText txtNombre, txtDescripcion, txtPrecio, txtStock;
    private Spinner spCategoria;
    private MaterialButton btnGuardar;

    private DatabaseReference databaseReference;

    public GestionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
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

        databaseReference = FirebaseDatabase.getInstance().getReference("Productos");

        String[] categorias = {
                "Mobiliario",
                "Electrónica",
                "Decoración",
                "Herramientas",
                "Otros"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categorias);
        spCategoria.setAdapter(adapter);

        btnGuardar.setOnClickListener(v->validarYGuardar());

    }

    private void validarYGuardar(){
        String nombre = txtNombre.getText().toString().trim();
        String descripcion = txtDescripcion.getText().toString().trim();
        String precioStr = txtPrecio.getText().toString().trim();
        String stockStr = txtStock.getText().toString().trim();
        String categoria = spCategoria.getSelectedItem().toString();

        if (nombre.isEmpty()) {
            txtNombre.setError("El nombre es obligatorio");
            txtNombre.requestFocus();
            return;
        }

        if (precioStr.isEmpty()) {
            txtPrecio.setError("Ingresa un precio válido");
            txtPrecio.requestFocus();
            return;
        }

        if (stockStr.isEmpty()) {
            txtStock.setError("Ingresa la cantidad en stock");
            txtStock.requestFocus();
            return;
        }

        double precio = Double.parseDouble(precioStr);
        int stock = Integer.parseInt(stockStr);

        // Generar un ID único alfanumérico en Firebase (Equivalente al AutoGenerate de Room)
        String id = databaseReference.push().getKey();

        // Crear el objeto Producto con los datos del formulario
        Producto nuevoProducto = new Producto(id, nombre, categoria, descripcion, precio, stock);

        // Guardar el objeto en la base de datos bajo su ID
        if (id != null) {
            databaseReference.child(id).setValue(nuevoProducto)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Producto guardado exitosamente", Toast.LENGTH_SHORT).show();
                        limpiarFormulario();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void limpiarFormulario() {
        txtNombre.setText("");
        txtDescripcion.setText("");
        txtPrecio.setText("");
        txtStock.setText("");
        spCategoria.setSelection(0);
        txtNombre.requestFocus();
    }
}