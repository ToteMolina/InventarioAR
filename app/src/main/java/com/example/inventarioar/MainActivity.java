    package com.example.inventarioar;

    import android.os.Bundle;

    import androidx.activity.EdgeToEdge;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.navigation.NavController;
    import androidx.navigation.fragment.NavHostFragment;
    import androidx.navigation.ui.NavigationUI;

    import com.google.android.material.bottomnavigation.BottomNavigationView;
    import com.google.firebase.database.FirebaseDatabase;

    public class MainActivity extends AppCompatActivity {
        public String sucursalKey = "";
        public String sucursalNombre = "";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            try {
                // activa la base de datos offline
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            } catch (Exception e) {
                // ignoramos ya que fue inicializada antes
            }
            setContentView(R.layout.activity_main);

            // busca el contenedor principal donde se van a cambian los fragments
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

            // conecta el menu inferior con el sistema de navegación
            if (navHostFragment != null){
                NavController navController = navHostFragment.getNavController();
                BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
                NavigationUI.setupWithNavController(bottomNavigationView, navController);
            }
        }


    }