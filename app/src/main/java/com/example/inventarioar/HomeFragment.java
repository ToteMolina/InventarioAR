package com.example.inventarioar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.inventarioar.Adaptadores.ProductoHomeAdapter;
import com.example.inventarioar.models.Producto;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView tvNombreSucursal, tvDistancia, tvTituloProductos, tvTotalProductos, tvStockBajo;
    private MaterialButton btnObtenerUbicacion;
    private RecyclerView rvProductosRecientes;

    // El "motor" de Google para obtener coordenadas
    private FusedLocationProviderClient fusedLocationProviderClient;
    private String sucursalActualKey = "";

    private static final double LATITUD_METROCENTRO = 13.4617631;
    private static final double LONGITUD_METROCENTRO = -88.1678594;
    private static final double LATITUD_CENTRO = 13.4829718;
    private static final double LONGITUD_CENTRO = -88.175523;
    private static final double LATITUD_FMO = 13.4402428;
    private static final double LONGITUD_FMO = -88.1585955;


    // Lanzador moderno para pedir permiso en pantalla
    private ActivityResultLauncher<String> requestPermissionLauncher;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted){
                        obtenerUbicacion(); // el usuario le dió a permitir
                    } else {
                        Toast.makeText(getContext(), "Se requiere el permiso de de ubicación para esta función", Toast.LENGTH_SHORT).show();
                        tvNombreSucursal.setText("Permiso denegado");
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvNombreSucursal = view.findViewById(R.id.tvNombreSucursal);
        tvDistancia = view.findViewById(R.id.tvDistancia);
        tvTituloProductos = view.findViewById(R.id.tvTituloProductos);
        btnObtenerUbicacion = view.findViewById(R.id.btnObtenerUbicacion);
        rvProductosRecientes = view.findViewById(R.id.rvProductosRecientes);
        tvTotalProductos = view.findViewById(R.id.tvTotalProductos);
        tvStockBajo = view.findViewById(R.id.tvStockBajo);

        rvProductosRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        btnObtenerUbicacion.setOnClickListener(v->verificarPermisoYObtenerUbicacion());

        String keyGuardada = ((MainActivity) requireActivity()).sucursalKey;
        String nombreGuardado = ((MainActivity) requireActivity()).sucursalNombre;

        if (!keyGuardada.isEmpty()){
            sucursalActualKey = keyGuardada;
            tvNombreSucursal.setText(nombreGuardado);
            tvDistancia.setText("Última ubicación detectada");
            cargarDatosDeSucursal(keyGuardada, nombreGuardado);
        } else {
            verificarPermisoYObtenerUbicacion();
        }
    }

    private void verificarPermisoYObtenerUbicacion(){
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            obtenerUbicacion();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacion() {
        tvNombreSucursal.setText("Buscando...");
        tvDistancia.setText("");

        fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location == null) {
                        tvNombreSucursal.setText("Ubicación no disponible");
                        return;
                    }

                    Location ubiMetro = new Location("");
                    ubiMetro.setLatitude(LATITUD_METROCENTRO);
                    ubiMetro.setLongitude(LONGITUD_METROCENTRO);

                    Location ubiCentro = new Location("");
                    ubiCentro.setLatitude(LATITUD_CENTRO);
                    ubiCentro.setLongitude(LONGITUD_CENTRO);

                    Location ubiFMO = new Location("");
                    ubiFMO.setLatitude(LATITUD_FMO);
                    ubiFMO.setLongitude(LONGITUD_FMO);

                    float dMetro  = location.distanceTo(ubiMetro);
                    float dCentro = location.distanceTo(ubiCentro);
                    float dFMO    = location.distanceTo(ubiFMO);

                    String nombreSucursal;
                    float distancia;

                    if (dMetro <= dCentro && dMetro <= dFMO) {
                        sucursalActualKey = "sucursal_metrocentro";
                        nombreSucursal    = "Metrocentro San Miguel";
                        distancia         = dMetro;
                    } else if (dCentro <= dFMO) {
                        sucursalActualKey = "sucursal_centro";
                        nombreSucursal    = "Sucursal Centro";
                        distancia         = dCentro;
                    } else {
                        sucursalActualKey = "sucursal_fmo";
                        nombreSucursal    = "FMO - UES";
                        distancia         = dFMO;
                    }

                    ((MainActivity) requireActivity()).sucursalKey = sucursalActualKey;
                    ((MainActivity) requireActivity()).sucursalNombre = nombreSucursal;

                    tvNombreSucursal.setText(nombreSucursal);

                    if (distancia < 500) {
                        tvDistancia.setText("Estás aquí · " + (int) distancia + " m");
                    } else {
                        tvDistancia.setText(String.format("%.1f km de distancia", distancia / 1000f));
                    }

                    cargarDatosDeSucursal(sucursalActualKey, nombreSucursal);
                });
    }

    private void cargarDatosDeSucursal(String sucursalKey, String nombreSucursal){
        FirebaseDatabase.getInstance().getReference("Productos")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Producto> todos = new ArrayList<>();

                        int contadorTotal = 0;
                        int contadorStockBajo = 0;

                        for (DataSnapshot ds : snapshot.getChildren()){
                            Producto p = ds.getValue(Producto.class);

                            if (p == null) continue;

                            int stockSucursal = 0;
                            if (p.getStockPorSucursal() != null && p.getStockPorSucursal().containsKey(sucursalKey)){
                                stockSucursal = p.getStockPorSucursal().get(sucursalKey);
                            }

                            if (stockSucursal > 0){
                                todos.add(p);
                                contadorTotal++;
                                if (stockSucursal <= 5){
                                    contadorStockBajo++;
                                }
                            }
                        }

                        tvTotalProductos.setText(String.valueOf(contadorTotal));
                        tvStockBajo.setText(String.valueOf(contadorStockBajo));
                        tvTituloProductos.setText("Productos recientes en: " + nombreSucursal);

                        // mostrar sólo los últimos 3
                        List<Producto> recientes = todos.size() > 3 ? todos.subList(todos.size() - 3, todos.size()) : todos;
                        rvProductosRecientes.setAdapter(new ProductoHomeAdapter(recientes, sucursalKey));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Error al cargar productos", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}