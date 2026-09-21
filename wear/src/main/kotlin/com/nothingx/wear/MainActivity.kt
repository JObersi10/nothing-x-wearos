package com.nothingx.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.nothingx.wear.data.DeviceViewModel
import com.nothingx.wear.ui.DeviceDetailScreen
import com.nothingx.wear.ui.DeviceListScreen
import com.nothingx.wear.ui.PermissionGate
import com.nothingx.wear.ui.theme.NothingXTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NothingXTheme {
                PermissionGate {
                    NothingXApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun NothingXApp(viewModel: DeviceViewModel) {
    val navController = rememberSwipeDismissableNavController()

    MaterialTheme {
        SwipeDismissableNavHost(navController = navController, startDestination = "list") {
            composable("list") {
                DeviceListScreen(viewModel) { device ->
                    val encodedName = Uri.encode(device.name)
                    navController.navigate("detail/${device.address}/$encodedName")
                }
            }
            composable("detail/{address}/{name}") { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address").orEmpty()
                val name = backStackEntry.arguments?.getString("name").orEmpty()
                DeviceDetailScreen(viewModel, address, name)
            }
        }
    }
}
