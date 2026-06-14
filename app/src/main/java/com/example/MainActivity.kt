package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.NetworkType
import com.example.ui.*
import com.example.ui.theme.CosmicGreen
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.CosmicAmber
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val activeNetwork by viewModel.activeNetwork.collectAsState()
                val isSyncing by viewModel.isSyncing.collectAsState()
                val syncStatus by viewModel.syncStatus.collectAsState()

                var currentTab by remember { mutableStateOf("wallet") }
                var showNetworkSelector by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // Immersive top bar matching bg-[#0A0E14] border-b border-slate-800
                        Surface(
                            color = MaterialTheme.colorScheme.surface, // DarkSurface (0xFF0A0E14)
                            modifier = Modifier.statusBarsPadding()
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "AET PROTOCOL CORE",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            letterSpacing = 1.8.sp,
                                            color = Color.White
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            // Real-time custom glowing pulse aura indicator matching design HTML
                                            Box(
                                                modifier = Modifier.size(12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val statusColor = if (isSyncing) CosmicAmber else CosmicCyan
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(statusColor.copy(alpha = 0.4f))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(statusColor)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isSyncing) "Syncing: $syncStatus" else "AET Mainnet: Synced",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSyncing) CosmicAmber else CosmicCyan,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Network select action trigger
                                        Box {
                                            Button(
                                                onClick = { showNetworkSelector = true },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant, // DarkSurfaceElevated
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = activeNetwork.displayName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showNetworkSelector,
                                                onDismissRequest = { showNetworkSelector = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                NetworkType.entries.forEach { netType ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = netType.displayName,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                        },
                                                        onClick = {
                                                            viewModel.selectNetwork(netType)
                                                            showNetworkSelector = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = SlateBorder, thickness = 1.dp)
                            }
                        }
                    },
                    bottomBar = {
                        // Immersive bottom navigation matching bg-[#0A0E14] border-t border-slate-800
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding()
                        ) {
                            HorizontalDivider(color = SlateBorder, thickness = 1.dp)
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                modifier = Modifier.height(68.dp)
                            ) {
                                val lightAlphaCyan = CosmicCyan.copy(alpha = 0.12f)
                                NavigationBarItem(
                                    selected = (currentTab == "wallet"),
                                    onClick = { currentTab = "wallet" },
                                    icon = { Icon(Icons.Default.Wallet, contentDescription = "Wallet") },
                                    label = { Text("Wallet", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CosmicCyan,
                                        selectedTextColor = CosmicCyan,
                                        unselectedIconColor = MutedSlate,
                                        unselectedTextColor = MutedSlate,
                                        indicatorColor = lightAlphaCyan
                                    )
                                )

                                NavigationBarItem(
                                    selected = (currentTab == "explorer"),
                                    onClick = { currentTab = "explorer" },
                                    icon = { Icon(Icons.Default.Language, contentDescription = "Explorer") },
                                    label = { Text("Explorer", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CosmicCyan,
                                        selectedTextColor = CosmicCyan,
                                        unselectedIconColor = MutedSlate,
                                        unselectedTextColor = MutedSlate,
                                        indicatorColor = lightAlphaCyan
                                    )
                                )

                                NavigationBarItem(
                                    selected = (currentTab == "rpc"),
                                    onClick = { currentTab = "rpc" },
                                    icon = { Icon(Icons.Default.Code, contentDescription = "RPC") },
                                    label = { Text("RPC", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CosmicCyan,
                                        selectedTextColor = CosmicCyan,
                                        unselectedIconColor = MutedSlate,
                                        unselectedTextColor = MutedSlate,
                                        indicatorColor = lightAlphaCyan
                                    )
                                )

                                NavigationBarItem(
                                    selected = (currentTab == "advisor"),
                                    onClick = { currentTab = "advisor" },
                                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Advisor") },
                                    label = { Text("Advisor", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CosmicCyan,
                                        selectedTextColor = CosmicCyan,
                                        unselectedIconColor = MutedSlate,
                                        unselectedTextColor = MutedSlate,
                                        indicatorColor = lightAlphaCyan
                                    )
                                )

                                NavigationBarItem(
                                    selected = (currentTab == "docs"),
                                    onClick = { currentTab = "docs" },
                                    icon = { Icon(Icons.Default.Description, contentDescription = "Docs") },
                                    label = { Text("Specs", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CosmicCyan,
                                        selectedTextColor = CosmicCyan,
                                        unselectedIconColor = MutedSlate,
                                        unselectedTextColor = MutedSlate,
                                        indicatorColor = lightAlphaCyan
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground)
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "TabContent"
                        ) { targetTab ->
                            when (targetTab) {
                                "wallet" -> WalletScreen(viewModel = viewModel)
                                "explorer" -> ExplorerScreen(viewModel = viewModel)
                                "rpc" -> RpcScreen(viewModel = viewModel)
                                "advisor" -> AdvisorScreen(viewModel = viewModel)
                                "docs" -> DocsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
