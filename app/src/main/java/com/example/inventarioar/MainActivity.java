    package com.example.inventarioar;

    import android.os.Bundle;

    import androidx.activity.EdgeToEdge;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.graphics.Insets;
    import androidx.core.view.ViewCompat;
    import androidx.core.view.WindowInsetsCompat;
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
            // activa la base de datos offline
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            } catch (Exception e) {
                // ya fue inicializada antes, ignorar
            }
            setContentView(R.layout.activity_main);

            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

            if (navHostFragment != null){
                NavController navController = navHostFragment.getNavController();
                BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
                NavigationUI.setupWithNavController(bottomNavigationView, navController);
            }
        }


    }