package com.example.inventarioar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    private TextView tvCoordenadas;
    private MaterialButton btnObtenerUbicacion;
    // El "motor" de Google para obtener coordenadas
    private FusedLocationProviderClient fusedLocationProviderClient;

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
                        tvCoordenadas.setText("Permiso denegado");
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

        tvCoordenadas = view.findViewById(R.id.tvCoordenadas);
        btnObtenerUbicacion = view.findViewById(R.id.btnObtenerUbicacion);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        btnObtenerUbicacion.setOnClickListener(v->verificarPermisoYObtenerUbicacion());
    }

    private void verificarPermisoYObtenerUbicacion(){
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            obtenerUbicacion();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacion(){
        tvCoordenadas.setText("Buscando ubicación...");

        fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(requireActivity(), location -> {
                    // LA UBICACION PUEDE SER NULA SI EL GPS DEL CELULAR ESTÁ APAGADO O ES LA PRIMERA VEZ QUE SE USA
                    if (location != null) {
                        double latitud = location.getLatitude();
                        double longitud = location.getLongitude();
                        android.location.Location ubiMetrocentro = new android.location.Location("Metrocentro San Miguel");
                        ubiMetrocentro.setLatitude(LATITUD_METROCENTRO);
                        ubiMetrocentro.setLongitude(LONGITUD_METROCENTRO);
                        android.location.Location ubiCentro = new android.location.Location("Centro San Miguel");
                        ubiCentro.setLatitude(LATITUD_CENTRO);
                        ubiCentro.setLongitude(LONGITUD_CENTRO);
                        android.location.Location ubiFMO = new android.location.Location("FMO UES");
                        ubiFMO.setLatitude(LATITUD_FMO);
                        ubiFMO.setLongitude(LONGITUD_FMO);

                        float distanciaMetro = location.distanceTo(ubiMetrocentro);
                        float distanciaCentro = location.distanceTo(ubiCentro);
                        float distanciaFMO = location.distanceTo(ubiFMO);

                        String sucursalActual = "";
                        String sucursalCernana = "";
                        float distanciaCercana = 0;

                        float kmAMetrocentro = distanciaMetro / 1000.0f;
                        float kmACentro = distanciaCentro / 1000.0f;
                        float kmAFMO = distanciaFMO / 1000.0f;

                        if (distanciaMetro <= distanciaCentro && distanciaMetro <= distanciaFMO) {
                            sucursalCernana = "Metrocentro San Miguel";
                            String kmFormateados = String.format("%.1f", kmAMetrocentro);
                            sucursalActual = "sucursal_metrocentro";
                            distanciaCercana = distanciaMetro;
                        } else if (distanciaCentro <= distanciaFMO && distanciaCentro <= distanciaMetro) {
                            String kmFormateados = String.format("%.1f", kmACentro);
                            sucursalActual = "sucursal_centro";
                            distanciaCercana = distanciaCentro;
                            sucursalCernana = "Sucursal Centro";
                        } else if (distanciaFMO <= distanciaMetro && distanciaFMO <= distanciaFMO) {
                            String kmFormateados = String.format("%.1f", kmAFMO);
                            sucursalActual = "sucursal_fmo";
                            distanciaCercana = distanciaFMO;
                            sucursalCernana = "Facultad Muldisciplaniria Oriental - UES";
                        }
                        if(distanciaCercana < 500){
                            tvCoordenadas.setText("Ubicación Actual: " + sucursalCernana + "\n" +
                                    "(Estás aquí mismo, a " + (int)distanciaCercana + " metros)");
                        }
                        else {
                            float kmFinales = distanciaCercana / 1000.0f;
                            String kmFormateados = String.format("%.1f", kmFinales);

                            tvCoordenadas.setText("Sucursal más cercana: " + sucursalCernana + "\n" +
                                    "Estás a " + kmFormateados + " km de distancia.");
                        }
                    }

                    else {
                        tvCoordenadas.setText("Ubicación no disponible en este momento.");
                    }
                });
    }
}