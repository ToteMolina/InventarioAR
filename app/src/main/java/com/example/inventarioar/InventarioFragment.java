package com.example.inventarioar;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.inventarioar.Adaptadores.ProductoAdapter;
import com.example.inventarioar.models.Producto;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;


public class InventarioFragment extends Fragment {

    private RecyclerView rvInventario;
    private ProductoAdapter adapter;
    private List<Producto> listaProductos;
    private DatabaseReference databaseReference;
    private ProgressBar progressBar;
    private FloatingActionButton btnAgregar;

    public InventarioFragment() {
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
        return inflater.inflate(R.layout.fragment_inventario, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvInventario = view.findViewById(R.id.rvInventario);
        progressBar = view.findViewById(R.id.progressBarInventario);
        btnAgregar = view.findViewById(R.id.btnAgregarProducto);

        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        btnAgregar.setOnClickListener(v->{
            NavHostFragment.findNavController(this).navigate(R.id.gestionFragment);
        });

        MaterialButton btnEscanear = view.findViewById(R.id.btnEscanearUniversal);

        // Le damos la orden de abrir la ventana de Realidad Aumentada
        if (btnEscanear != null) {
            btnEscanear.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), activity_ar_scanner.class);

               
                android.content.SharedPreferences prefs = requireContext().getSharedPreferences("InventarioPrefs", android.content.Context.MODE_PRIVATE);

                String sucursalKey = prefs.getString("sucursalKey", "sucursal_metrocentro");
                String sucursalNombre = prefs.getString("sucursalNombre", "Metrocentro San Miguel");

                intent.putExtra("sucursalKey", sucursalKey);
                intent.putExtra("nombreSucursal", sucursalNombre);

                startActivity(intent);
            });
        }

        listaProductos = new ArrayList<>();
        adapter = new ProductoAdapter(listaProductos, new ProductoAdapter.OnProductoListener() {
            @Override
            public void onEditar(Producto producto) {
                Bundle args = new Bundle();
                args.putString("productoId", producto.getId());
                NavHostFragment.findNavController(InventarioFragment.this)
                        .navigate(R.id.gestionFragment, args);
            }

            @Override
            public void onEliminar(Producto producto) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Eliminar producto")
                        .setMessage("¿Está seguro que desea eliminar " + producto.getNombre() + "?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            FirebaseDatabase.getInstance().getReference("Productos")
                                    .child(producto.getId())
                                    .removeValue();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });
        rvInventario.setAdapter(adapter);

        databaseReference = FirebaseDatabase.getInstance().getReference("Productos");

        cargarProductos();
    }

    private void cargarProductos(){
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaProductos.clear(); // limpiar lista antes de llenar para no duplicar datos

                for (DataSnapshot dataSnapshot : snapshot.getChildren()){
                    Producto producto = dataSnapshot.getValue(Producto.class);
                    if (producto != null){
                        listaProductos.add(producto);
                    }
                }
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Error al cargar el inventario: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}