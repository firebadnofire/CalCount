package org.archuser.CalCount

import android.os.Bundle
import androidx.activity.viewModels
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.DynamicColors
import com.google.android.material.snackbar.Snackbar
import org.archuser.CalCount.databinding.ActivityMainBinding
import org.archuser.CalCount.ui.AppViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: AppViewModel by viewModels()
    private val navController by lazy {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navHostFragment.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (viewModel.uiState.value?.goals?.useMaterialYou != false) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_today,
                R.id.nav_history,
                R.id.nav_food_library,
                R.id.nav_create_food,
                R.id.nav_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId != R.id.nav_history && navController.currentDestination?.id == R.id.dayDetailFragment) {
                navController.popBackStack(R.id.nav_history, false)
            }
            NavigationUI.onNavDestinationSelected(item, navController)
        }
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_history) {
                navController.popBackStack(R.id.nav_history, false)
            } else {
                navController.popBackStack(item.itemId, false)
            }
        }

        viewModel.message.observe(this) { message ->
            if (message.isNullOrBlank()) {
                return@observe
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.bottomNavigation)
                .show()

            viewModel.clearMessage()
        }
    }

    fun navigateToCreateFood() {
        binding.bottomNavigation.selectedItemId = R.id.nav_create_food
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
