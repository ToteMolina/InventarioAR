package com.example.inventarioar;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class DespachoFragment extends Fragment {

    private MaterialAutoCompleteTextView spProducto, spSucursal;
    private TextInputEditText txtCantidad;
    private MaterialButton btnConfirmarDespacho;
    private TextView tvStockBodega;
    private DatabaseReference databaseReference;

    private ArrayList<ProductoItem> listaProductos = new ArrayList<>();
    private ProductoItem productoSelected;

    private static final String SUC_CENTRO = "sucursal_centro";
    private static final String SUC_METRO = "sucursal_metrocentro";
    private static final String SUC_FMO = "sucursal_fmo";

    public DespachoFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_despacho, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        spProducto = view.findViewById(R.id.spProducto);
        spSucursal = view.findViewById(R.id.spSucursal);
        txtCantidad = view.findViewById(R.id.txtCantidad);
        tvStockBodega = view.findViewById(R.id.tvStockBodega);
        btnConfirmarDespacho = view.findViewById(R.id.btnConfirmarDespacho);

        databaseReference = FirebaseDatabase.getInstance().getReference("Productos");


        cargarProductos();
        cargarSucursales();

        spProducto.setOnItemClickListener((parent, view1, position, id) -> {
            productoSelected = (ProductoItem) parent.getItemAtPosition(position);
            tvStockBodega.setText("Stock actual en bodega: " + productoSelected.stock);
            tvStockBodega.setVisibility(View.VISIBLE);
        });

        // 3. Acción del botón de despacho
        btnConfirmarDespacho.setOnClickListener(v -> procesarDespacho());
    }

    private void cargarSucursales() {

        String[] sucursalesBonitas = {"Sucursal Centro", "Metrocentro San Miguel", "FMO - UES"};

        ArrayAdapter<String> adapterSucursal = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                sucursalesBonitas
        );
        spSucursal.setAdapter(adapterSucursal);
    }

    private void cargarProductos() {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaProductos.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    ProductoItem producto = data.getValue(ProductoItem.class);
                    if (producto != null) {
                        listaProductos.add(producto);
                    }
                }

                ArrayAdapter<ProductoItem> productoAdapter = new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        listaProductos
                );
                spProducto.setAdapter(productoAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Error al cargar productos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void procesarDespacho() {
        // Validaciones de seguridad
        if (productoSelected == null) {
            Toast.makeText(getContext(), "Por favor selecciona un producto", Toast.LENGTH_SHORT).show();
            return;
        }
        String nombreSucursal = spSucursal.getText().toString();
        String idSucursal = "";

        if (nombreSucursal.equals("Sucursal Centro")) idSucursal = SUC_CENTRO;
        else if (nombreSucursal.equals("Metrocentro San Miguel")) idSucursal = SUC_METRO;
        else if (nombreSucursal.equals("FMO - UES")) idSucursal = SUC_FMO;

        if (idSucursal.isEmpty()) {
            Toast.makeText(getContext(), "Selecciona una sucursal destino", Toast.LENGTH_SHORT).show();
            return;
        }
        String cantidadStr = txtCantidad.getText().toString();
        if (cantidadStr.isEmpty() || Integer.parseInt(cantidadStr) <= 0) {
            txtCantidad.setError("Ingresa una cantidad válida");
            return;
        }
        int cantidadEnviar = Integer.parseInt(cantidadStr);

        if (cantidadEnviar > productoSelected.stock) {
            Toast.makeText(getContext(), "¡Error! Solo tienes " + productoSelected.stock + " unidades en bodega.", Toast.LENGTH_LONG).show();
            return;
        }

        HashMap<String, Object> actualizaciones = new HashMap<>();

        actualizaciones.put("stock", ServerValue.increment(-cantidadEnviar));

        actualizaciones.put("stock_sucursales/" + idSucursal, ServerValue.increment(cantidadEnviar));

        databaseReference.child(productoSelected.id).updateChildren(actualizaciones)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "¡Despacho Exitoso!", Toast.LENGTH_LONG).show();

                    txtCantidad.setText("");
                    spProducto.setText("");
                    spSucursal.setText("");
                    tvStockBodega.setVisibility(View.GONE);
                    productoSelected = null;
                    cargarProductos();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public static class ProductoItem {


        public String id;
        public String nombre;
        public int stock;

        public ProductoItem() {
            // Constructor vacío obligatorio
        }

        public ProductoItem(String id, String nombre, int stock) {
            this.id = id;
            this.nombre = nombre;
            this.stock = stock;
        }

        @Override
        public String toString() {

            return nombre + " (Stock: " + stock + ")";
        }
    }
}