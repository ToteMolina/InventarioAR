package com.example.inventarioar;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.inventarioar.Adaptadores.ProductoAdapter;
import com.example.inventarioar.models.Producto;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventarioFragment extends Fragment {

    private RecyclerView rvInventario;
    private ProductoAdapter adapter;

    private List<Producto> listaProductosMaestra, listaProductosFiltrados;
    private DatabaseReference databaseReference;
    private ProgressBar progressBar;
    private FloatingActionButton btnAgregar;

    private TextInputEditText etBuscarProducto;
    private AutoCompleteTextView spFiltroSucursal, spFiltroCategoria, spFiltroorden;
    private MaterialButton btnLimpiarFiltros;

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
        return inflater.inflate(R.layout.fragment_inventario, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        String[] sucursales = {"Almacén", "Metrocentro San Miguel", "Sucursal Centro", "FMO - UES"};
        spFiltroSucursal.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sucursales));

        String[] categorias = {"Todas", "Mobiliario", "Electrónica", "Decoración", "Herramientas", "Otros"};
        spFiltroCategoria.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categorias));

        String[] ordenes = {"Más antiguos", "Más recientes", "Nombre (A-Z)", "Nombre (Z-A)", "Precio (Menor-Mayor)", "Precio (Mayor-Menor)"};
        spFiltroorden.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, ordenes));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvInventario = view.findViewById(R.id.rvInventario);
        progressBar = view.findViewById(R.id.progressBarInventario);
        btnAgregar = view.findViewById(R.id.btnAgregarProducto);

        etBuscarProducto = view.findViewById(R.id.etBuscarProducto);
        spFiltroSucursal = view.findViewById(R.id.spFiltroSucursal);
        spFiltroCategoria = view.findViewById(R.id.spFiltroCategoria);
        spFiltroorden = view.findViewById(R.id.spFiltroOrden);
        btnLimpiarFiltros = view.findViewById(R.id.btnLimpiarFiltros);

        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        btnAgregar.setOnClickListener(v->{
            NavHostFragment.findNavController(this).navigate(R.id.gestionFragment);
        });

        listaProductosMaestra = new ArrayList<>();
        listaProductosFiltrados = new ArrayList<>();

        adapter = new ProductoAdapter(listaProductosFiltrados, new ProductoAdapter.OnProductoListener() {
            @Override
            public void onEditar(Producto producto) {
                Bundle args = new Bundle();
                args.putString("productoId", producto.getId());
                NavHostFragment.findNavController(InventarioFragment.this)
                        .navigate(R.id.gestionFragment, args);
            }

            @Override
            public void onEliminar(Producto producto) {
                AlertDialog dialogoEliminar = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Eliminar producto")
                        .setMessage("¿Está seguro que desea eliminar " + producto.getNombre() + "?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            FirebaseDatabase.getInstance().getReference("Productos")
                                    .child(producto.getId())
                                    .removeValue();
                        })
                        .setNegativeButton("Cancelar", null)
                        .create();

                dialogoEliminar.show();

                int modoPantalla = requireContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                boolean esModoOscuro = (modoPantalla == android.content.res.Configuration.UI_MODE_NIGHT_YES);
                int colorTexto = esModoOscuro ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

                dialogoEliminar.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(colorTexto);
                dialogoEliminar.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(android.graphics.Color.parseColor("#D32F2F"));
            }
        });
        rvInventario.setAdapter(adapter);

        configurarFiltros();
        databaseReference = FirebaseDatabase.getInstance().getReference("Productos");
        cargarProductos();
    }

    private void configurarFiltros(){
        String[] sucursales = {"Almacén", "Metrocentro San Miguel", "Sucursal Centro", "FMO - UES"};
        spFiltroSucursal.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sucursales));
        spFiltroSucursal.setText("Almacén", false);

        String[] categorias = {"Todas", "Mobiliario", "Electrónica", "Decoración", "Herramientas", "Otros"};
        spFiltroCategoria.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categorias));
        spFiltroCategoria.setText("Todas", false);

        String[] ordenes = {"Más antiguos", "Más recientes", "Nombre (A-Z)", "Nombre (Z-A)", "Precio (Menor-Mayor)", "Precio (Mayor-Menor)"};
        spFiltroorden.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, ordenes));
        spFiltroorden.setText("Más antiguos", false);

        spFiltroSucursal.setOnItemClickListener((parent, view, position, id) -> aplicarFiltros());
        spFiltroCategoria.setOnItemClickListener((parent, view, position, id) -> aplicarFiltros());
        spFiltroorden.setOnItemClickListener((parent, view, position, id) -> aplicarFiltros());

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {}

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                aplicarFiltros();
            }
        });

        btnLimpiarFiltros.setOnClickListener(v->{
            etBuscarProducto.setText("");
            spFiltroSucursal.setText("Almacén", false);
            spFiltroCategoria.setText("Todas", false);
            spFiltroorden.setText("Más antiguos", false);
            aplicarFiltros();
        });
    }

    private void aplicarFiltros(){
        listaProductosFiltrados.clear();

        String busqueda = etBuscarProducto.getText().toString().trim().toLowerCase();
        String sucursalSeleccionada = spFiltroSucursal.getText().toString();
        String categoriaSeleccionada = spFiltroCategoria.getText().toString();
        String ordenSeleccionado = spFiltroorden.getText().toString();

        String sucursalKey = "";
        if (sucursalSeleccionada.equals("Metrocentro San Miguel")) sucursalKey = "sucursal_metrocentro";
        else if (sucursalSeleccionada.equals("Sucursal Centro")) sucursalKey = "sucursal_centro";
        else if (sucursalSeleccionada.equals("FMO - UES")) sucursalKey = "sucursal_fmo";

        adapter.setSucursalFiltroActual(sucursalKey.isEmpty() ? "Almacén" : sucursalKey);

        for (Producto p : listaProductosMaestra){
            if (!busqueda.isEmpty() && !p.getNombre().toLowerCase().contains(busqueda)){
                continue;
            }
            if (!categoriaSeleccionada.equals("Todas") && !p.getCategoria().equals(categoriaSeleccionada)){
                continue;
            }
            if (!sucursalSeleccionada.equals("Almacén")){
                if (p.getStockPorSucursal() == null || !p.getStockPorSucursal().containsKey(sucursalKey) || p.getStockPorSucursal().get(sucursalKey) <= 0){
                    continue;
                }
            }
            listaProductosFiltrados.add(p);
        }

        Collections.sort(listaProductosFiltrados, (p1, p2)->{
            if (ordenSeleccionado.equals("Más recientes")){
                return p2.getId().compareTo(p1.getId());
            } else if (ordenSeleccionado.equals("Más antiguos")){
                return p1.getId().compareTo(p2.getId());
            } else if (ordenSeleccionado.equals("Nombre (Z-A)")){
                return p2.getNombre().compareToIgnoreCase(p1.getNombre());
            } else if (ordenSeleccionado.equals("Precio (Menor-Mayor)")){
                return Double.compare(p1.getPrecio(), p2.getPrecio());
            } else if (ordenSeleccionado.equals("Precio (Mayor-Menor)")){
                return Double.compare(p2.getPrecio(), p1.getPrecio());
            } else {
                return p1.getNombre().compareToIgnoreCase(p2.getNombre());
            }
        });

        adapter.notifyDataSetChanged();
    }

    private void cargarProductos(){
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaProductosMaestra.clear();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()){
                    Producto producto = dataSnapshot.getValue(Producto.class);
                    if (producto != null){
                        listaProductosMaestra.add(producto);
                    }
                }
                aplicarFiltros();
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