package com.example.inventarioar;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.inventarioar.Adaptadores.ProductoAdapter;
import com.example.inventarioar.models.Producto;
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
        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));

        listaProductos = new ArrayList<>();
        adapter = new ProductoAdapter(listaProductos);
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