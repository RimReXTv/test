package com.example.ui

import androidx.compose.animation.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.protocol.CryptoHelper
import com.example.protocol.NetworkType
import com.example.protocol.WhitepaperDocs
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val walletAddr by viewModel.walletAddress.collectAsState()
    val activeKeys by viewModel.activeKeyPair.collectAsState()
    val accountState by viewModel.walletAccountState.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()
    val richList by viewModel.richList.collectAsState()

    var receiverInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var feeInput by remember { mutableStateOf("") }

    var stakeAmountInput by remember { mutableStateOf("") }

    var passphraseInput by remember { mutableStateOf("") }
    var exportPayload by remember { mutableStateOf<String?>(null) }
    var importPayload by remember { mutableStateOf("") }

    var showSendDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        // Operation Status Toast-Like banner
        if (operationStatus != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Notification",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = operationStatus ?: "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // 1. Keys initialization check
        if (walletAddr == null || activeKeys == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Security Keys Needed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AET Secure Wallet Vault",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To participate as an active validator, secure balances, sign blocks, or vote on availability, you must unlock or instantiate cryptographic keys (ECDSA secp256r1).",
                            fontSize = 14.sp,
                            color = MutedSlate,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.createWallet() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Cryptographic Keypair", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.RestorePage, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore wallet from backup JSON", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // Unlocked Wallet UI
            item {
                // Wallet Balance Dashboard Card
                val balanceAet = (accountState?.balance ?: 0) / 1_000_000.0
                val stakedAet = (accountState?.stakedAmount ?: 0) / 1_000_000.0
                val totalAet = balanceAet + stakedAet

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Fancy radial background decoration matching high-design guidelines
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        CosmicCyan.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width, 0f),
                                    radius = size.width * 0.8f
                                )
                            )
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AET Ledger Wallet",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicGreen.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CosmicGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Keys Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "%.4f AET".format(totalAet),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "≈ %,d microAET total pool".format((accountState?.balance ?: 0) + (accountState?.stakedAmount ?: 0)),
                            fontSize = 12.sp,
                            color = MutedSlate
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        // Progress layout representing distribution
                        if (totalAet > 0) {
                            val ratio = stakedAet / totalAet
                            LinearProgressIndicator(
                                progress = { ratio.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Liquid", fontSize = 11.sp, color = MutedSlate)
                                Text("%.2f AET".format(balanceAet), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Staked / Locked", fontSize = 11.sp, color = MutedSlate)
                                Text("%.2f AET".format(stakedAet), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Address Row with copying capability
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallet,
                                contentDescription = null,
                                tint = MutedSlate,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = walletAddr ?: "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(walletAddr ?: ""))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Wallet Address",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Wallet Control Tools (Buttons grid)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showSendDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send AET", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    OutlinedButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // 2. Collateral Staking & Validator Rules (0.19)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Staking & Validator Enrolment",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Run this mobile node as an active validation checker. Lock at least 100 AET (100,000,000 microAET) as collateral to become an immutable participant, earning rewards block-by-block.",
                            fontSize = 12.sp,
                            color = MutedSlate,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = stakeAmountInput,
                                onValueChange = { stakeAmountInput = it },
                                label = { Text("Stake Amount (microAET)") },
                                placeholder = { Text("e.g. 100000000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val amt = stakeAmountInput.toLongOrNull()
                                    if (amt != null) {
                                        viewModel.lockStake(amt)
                                        stakeAmountInput = ""
                                    }
                                },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Stake", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }

                        if ((accountState?.stakedAmount ?: 0) > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.releaseStake() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicRed),
                                border = BorderStroke(1.dp, CosmicRed.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = CosmicRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unstake / Release Collateral")
                            }
                        }
                    }
                }
            }

            // 3. High Stake Rich List Dashboard (0.13)
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Network Rich Ledger",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${richList.size} Accounts",
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            richList.take(6).forEachIndexed { i, account ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (account.isValidator) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.15f
                                                ) else MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.15f
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#${i + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (account.isValidator) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.address,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1
                                        )
                                        if (account.isValidator) {
                                            Text(
                                                text = "Validator Staker (Locked: %,d microAET)".format(account.stakedAmount),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "%,.2f AET".format(account.balance / 1_000_000.0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (i < richList.take(6).lastIndex) {
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Dialog: SEND CRYPTOGRAPHIC PAYMENT
    if (showSendDialog) {
        AlertDialog(
            onDismissRequest = { showSendDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Formulate Payment Proposal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sign and broadcast a transfer payload directly to the network validator mempools.",
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                    OutlinedTextField(
                        value = receiverInput,
                        onValueChange = { receiverInput = it },
                        label = { Text("Receiver Cryptographic Address") },
                        placeholder = { Text("e.g. AET_...") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    // Autocomplete assist helpers for prototype testing
                    Text("Sample Receivers (Tap to load):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedSlate)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "AET_VALIDATOR_ALICE_NODE" to "Alice Node",
                            "AET_VALIDATOR_BOB_NODE" to "Bob Node",
                            "AET_LIQUID_CHARLIE_POOL" to "Charlie Pool"
                        ).forEach { (hash, name) ->
                            SuggestionChip(
                                onClick = { receiverInput = hash },
                                label = { Text(name) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount (microAET)") },
                        placeholder = { Text("e.g. 500000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val activeNetworkType by viewModel.activeNetwork.collectAsState()
                    OutlinedTextField(
                        value = feeInput,
                        onValueChange = { feeInput = it },
                        label = { Text("Incentive Transaction Fee (microAET)") },
                        placeholder = { Text("Min fee: ${activeNetworkType.minFee}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountInput.toLongOrNull() ?: 0L
                        val fee = feeInput.toLongOrNull() ?: 0L
                        if (receiverInput.isNotEmpty() && amount > 0 && fee > 0) {
                            viewModel.sendPayment(receiverInput, amount, fee)
                            showSendDialog = false
                            receiverInput = ""
                            amountInput = ""
                            feeInput = ""
                        }
                    }
                ) {
                    Text("Sign & Propagate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendDialog = false }) {
                    Text("Abort")
                }
            }
        )
    }

    // Backup Wallet Dialog (0.14)
    if (showBackupDialog) {
        val backupPayload = exportPayload
        AlertDialog(
            onDismissRequest = {
                showBackupDialog = false
                passphraseInput = ""
                exportPayload = null
            },
            title = { Text("Encrypted Key Exporter (0.14)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Encapsulates private, cryptographic keys into password-protected AES payloads. Never share your password.",
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("AES Backup Passphrase") },
                        placeholder = { Text("Strong password") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (backupPayload != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "AES/CBC Cipher Backup Output:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = backupPayload,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { clipboard.setText(AnnotatedString(backupPayload)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Encrypted Payload")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (backupPayload == null) {
                    Button(
                        onClick = {
                            if (passphraseInput.isNotEmpty()) {
                                exportPayload = viewModel.exportBackup(passphraseInput)
                            }
                        }
                    ) {
                        Text("Export Key")
                    }
                } else {
                    Button(
                        onClick = {
                            showBackupDialog = false
                            passphraseInput = ""
                            exportPayload = null
                        }
                    ) {
                        Text("Done")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupDialog = false
                        passphraseInput = ""
                        exportPayload = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Restore Wallet Dialog (0.14)
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Key Decryption Restorer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Decrypts your private keys. Provide the identical AES CBC passphrase configured at export.",
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                    OutlinedTextField(
                        value = importPayload,
                        onValueChange = { importPayload = it },
                        label = { Text("Copy-Paste Encrypted Cipher String") },
                        placeholder = { Text("Paste string...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Passphrase") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPayload.isNotEmpty() && passphraseInput.isNotEmpty()) {
                            viewModel.importBackup(importPayload, passphraseInput)
                            showRestoreDialog = false
                            importPayload = ""
                            passphraseInput = ""
                        }
                    }
                ) {
                    Text("Verify & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Abort")
                }
            }
        )
    }
}

@Composable
fun ExplorerScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val blocks by viewModel.blocks.collectAsState()
    val mempool by viewModel.mempool.collectAsState()
    val activeNet by viewModel.activeNetwork.collectAsState()
    val recentTxs by viewModel.recentTransactions.collectAsState()
    val peers by viewModel.peers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResultBlock by remember { mutableStateOf<BlockEntity?>(null) }
    var searchResultTx by remember { mutableStateOf<TxEntity?>(null) }
    var searchResultAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var searchedAddressTxs by remember { mutableStateOf<List<TxEntity>>(emptyList()) }
    var searchPerformed by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        // Universal Search Panel (0.18)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Unified AET Chain Explorer Search (0.18)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Height, Transaction Hash, or Address") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (searchQuery.trim().isNotEmpty()) {
                                    val query = searchQuery.trim()
                                    coroutineScope.launch {
                                        searchPerformed = true
                                        // Reset
                                        searchResultBlock = null
                                        searchResultTx = null
                                        searchResultAccount = null
                                        searchedAddressTxs = emptyList()

                                        // Height search
                                        val heightQuery = query.toLongOrNull()
                                        if (heightQuery != null) {
                                            searchResultBlock = viewModel.lDao.getBlockByHeight(heightQuery, activeNet.chainId)
                                        }

                                        // Hash search
                                        if (searchResultBlock == null) {
                                            searchResultBlock = viewModel.lDao.getBlockByHash(query, activeNet.chainId)
                                        }

                                        // Tx search
                                        if (searchResultBlock == null) {
                                            searchResultTx = viewModel.lDao.getTxByHash(query)
                                        }

                                        // Account search
                                        if (searchResultBlock == null && searchResultTx == null) {
                                            val acc = viewModel.repository.getWalletBalance(query)
                                            if (acc.balance > 0 || acc.stakedAmount > 0 || acc.nonce > 0) {
                                                searchResultAccount = acc
                                                // Get records
                                                searchedAddressTxs = viewModel.lDao.getTxsForAddressFlow(query).first()
                                            }
                                        }
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Lookup", tint = Color.Black)
                        }
                    }

                    // Reset queries
                    if (searchPerformed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                searchPerformed = false
                                searchQuery = ""
                                searchResultBlock = null
                                searchResultTx = null
                                searchResultAccount = null
                                searchedAddressTxs = emptyList()
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Search Filters", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Search Results Section
        if (searchPerformed) {
            item {
                Column {
                    Text("Search Results", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (searchResultBlock != null) {
                        val item = searchResultBlock!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, CosmicGreen.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("FOUND BLOCK #${item.height}", fontWeight = FontWeight.Bold, color = CosmicGreen)
                                    Text("Network: ${item.chainId}", fontSize = 11.sp, color = MutedSlate)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                DetailRow("Hash", item.hash)
                                DetailRow("Parent Hash", item.previousHash)
                                DetailRow("Merkle Root", item.merkleRoot)
                                DetailRow("Validator", item.validatorPublicKey)
                                DetailRow("Timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)))
                                DetailRow("Signature", item.signature)
                            }
                        }
                    } else if (searchResultTx != null) {
                        val tx = searchResultTx!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("FOUND TRANSACTION RECORD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(10.dp))
                                DetailRow("Tx Hash", tx.hash)
                                DetailRow("Sender", tx.sender)
                                DetailRow("Receiver", tx.receiver)
                                DetailRow("Amount", "%,d microAET".format(tx.amount))
                                DetailRow("Fee", "%,d microAET".format(tx.fee))
                                DetailRow("Height", "#${tx.blockHeight}")
                                DetailRow("Timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(tx.timestamp)))
                                DetailRow("Status", tx.status)
                                DetailRow("Signature", tx.signature)
                            }
                        }
                    } else if (searchResultAccount != null) {
                        val acc = searchResultAccount!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("FOUND ACCOUNT STATE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(10.dp))
                                DetailRow("Address", acc.address)
                                DetailRow("Balance", "%.4f AET (%,d microAET)".format(acc.balance / 1_000_000.0, acc.balance))
                                DetailRow("Collateral Staked", "%.4f AET".format(acc.stakedAmount / 1_000_000.0))
                                DetailRow("Is Enrolled Validator", if (acc.isValidator) "YES (Active PoAV)" else "NO")
                                DetailRow("Account Nonce", "${acc.nonce}")

                                if (searchedAddressTxs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Recent Transactions (Address):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    searchedAddressTxs.forEach { tx ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (tx.sender == acc.address) "Sent -> ${tx.receiver.substring(0, 8)}" else "Recv <- ${tx.sender.substring(0, 8)}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text("%.2f AET".format(tx.amount / 1_000_000.0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CosmicRed.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, CosmicRed)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = CosmicRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("No matching block hash, height, transaction, or active account signature found.", fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Custom canvas animations / blockchain visualizations (0.4, 0.20)
        item {
            Column {
                Text(
                    text = "AET P2P Cluster Availability Core",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Availability Network Model",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CosmicGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Online Clusters", fontSize = 11.sp, color = CosmicGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive Canvas Model
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            val activePeersCount = peers.size.coerceAtLeast(3)
                            val centerWidth = size.width / 2
                            val centerHeight = size.height / 2

                            // Draw central genesis block validator
                            drawCircle(
                                color = CosmicCyan,
                                radius = 12f,
                                center = Offset(centerWidth, centerHeight)
                            )

                            // Draw concentric network loops
                            drawCircle(
                                color = CosmicCyan.copy(alpha = 0.3f),
                                radius = 50f,
                                center = Offset(centerWidth, centerHeight),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )

                            // Draw mobile peer nodes surrounding areally
                            for (i in 0 until activePeersCount) {
                                val deg = (360 / activePeersCount) * i
                                val radians = Math.toRadians(deg.toDouble())
                                val pX = centerWidth + (60 * Math.cos(radians)).toFloat()
                                val pY = centerHeight + (60 * Math.sin(radians)).toFloat()

                                // Line connecting peer to central coordinator
                                drawLine(
                                    color = MutedSlate.copy(alpha = 0.4f),
                                    start = Offset(centerWidth, centerHeight),
                                    end = Offset(pX, pY),
                                    strokeWidth = 2f
                                )

                                drawCircle(
                                    color = CosmicAmber,
                                    radius = 6f,
                                    center = Offset(pX, pY)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Light nodes represent smartphones check-voting the block proposals of static pool seeds. This maintains zero battery overhead.",
                            fontSize = 11.sp,
                            color = MutedSlate,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Live Block validation proposal trigger (0.4)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Validation Proposer Core", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${mempool.size} transactions pending in mempool", fontSize = 11.sp, color = MutedSlate)
                        }
                        Button(
                            onClick = { viewModel.proposeMockBlock() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            enabled = mempool.isNotEmpty()
                        ) {
                            Text("Sign Block", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // Mempool (0.8) & Syncing blocks lists
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Mempool (${mempool.size} Tx)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Active Network: ${activeNet.displayName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (mempool.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No pending transaction proposals found in mempool. Run payments or wait for incoming background peer nodes.",
                            color = MutedSlate,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(mempool) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CosmicAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = "Pending", tint = CosmicAmber, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${tx.sender.substring(0, 8)} -> ${tx.receiver.substring(0, 8)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Nonce: ${tx.nonce} | Fee: %,d microAET".format(tx.fee),
                                fontSize = 10.sp,
                                color = MutedSlate
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "%,.2f AET".format(tx.amount / 1_000_000.0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicAmber
                        )
                    }
                }
            }
        }

        // Sync Blocks Ledger Lists (0.18)
        item {
            Text(
                text = "Committed Blocks Root (${blocks.size} Synced)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(blocks) { block ->
            var expandedBlock by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedBlock = !expandedBlock },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (block.height == 0L) CosmicCyan.copy(alpha = 0.12f)
                                    else CosmicGreen.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (block.height == 0L) Icons.Default.BrightnessAuto else Icons.Default.ViewInAr,
                                contentDescription = "Block Icon",
                                tint = if (block.height == 0L) CosmicCyan else CosmicGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Block Height #${block.height}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hash: ${block.hash.substring(0, 16)}...",
                                fontSize = 11.sp,
                                color = MutedSlate,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${block.transactionCount} Tx",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(block.timestamp)),
                                fontSize = 10.sp,
                                color = MutedSlate
                            )
                        }
                    }

                    AnimatedVisibility(visible = expandedBlock) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            DetailRow("Version", "${block.version}")
                            DetailRow("Chain ID", block.chainId)
                            DetailRow("Parent Hash", block.previousHash)
                            DetailRow("Merkle Root", block.merkleRoot)
                            DetailRow("Validator Signer Public", block.validatorPublicKey)
                            DetailRow("Block Signature", block.signature)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RpcScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val rpcLogs by viewModel.rpcCommandLog.collectAsState()

    var selectedMethod by remember { mutableStateOf("getBalance") }
    var parametersInput by remember { mutableStateOf("{\n  \"address\": \"AET_VALIDATOR_ALICE_NODE\"\n}") }

    val methods = listOf("getBalance", "getTransaction", "getBlock", "sendTransaction", "getSyncStatus", "getGenesisInfo")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "AET JSON-RPC API Simulator Interface (0.17)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Query the local smartphone node core via standardized methods. Mimics production ledger backend RPC interfaces.",
                    fontSize = 11.sp,
                    color = MutedSlate,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Select Method:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable row of suggestion chips for methods
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    methods.forEach { method ->
                        FilterChip(
                            selected = (selectedMethod == method),
                            onClick = {
                                selectedMethod = method
                                // Default inputs
                                parametersInput = when (method) {
                                    "getBalance" -> "{\n  \"address\": \"AET_VALIDATOR_ALICE_NODE\"\n}"
                                    "getTransaction" -> "{\n  \"hash\": \"dummy_hash\"\n}"
                                    "getBlock" -> "{\n  \"height\": 0\n}"
                                    "sendTransaction" -> "{\n  \"sender\": \"AET_VALIDATOR_ALICE_NODE\",\n  \"receiver\": \"AET_LIQUID_CHARLIE_POOL\",\n  \"amount\": 5000,\n  \"fee\": 10,\n  \"nonce\": 1\n}"
                                    "getSyncStatus", "getGenesisInfo" -> "{}"
                                    else -> "{}"
                                }
                            },
                            label = { Text(method) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = parametersInput,
                    onValueChange = { parametersInput = it },
                    label = { Text("Moshi Parameters (JSON String)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.sendRpcMethod(selectedMethod, parametersInput) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Invoke RPC Client")
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearRpcLogs() },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    ) {
                        Text("Clear Logger")
                    }
                }
            }
        }

        Text("RPC Console Output Terminal:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        
        // Terminal Window
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF07090C)),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E232F))
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (rpcLogs.isEmpty()) {
                    Text(
                        text = "AET ledger simulator terminal initialized. Command ready...\nTry invoking 'getGenesisInfo' or 'getSyncStatus'.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MutedSlate
                    )
                } else {
                    rpcLogs.forEach { (command, response) ->
                        Column {
                            Text(
                                text = command,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CosmicAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = response,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E5FF)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color(0xFF1E232F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvisorScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val aiResponse by viewModel.aiResponse.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val presetQuestions = listOf(
        "Explain Byzantine fault tolerance inside PoAV consensus.",
        "How does AET lock and distribute staking rewards on mobile?",
        "Detail the transaction layout and ECDSA secp256r1 verification rules."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Expert",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AET AI Protocol Co-Counsel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A specialized system designed to resolve dense blockchain queries. Backed by high-thinking models.",
                    fontSize = 11.sp,
                    color = MutedSlate,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Consult about cryptography, staking, or forks...") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = {
                                    if (searchQuery.trim().isNotEmpty()) {
                                        viewModel.askAdvisor(searchQuery.trim())
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                presetQuestions.forEach { question ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                searchQuery = question
                                viewModel.askAdvisor(question)
                            },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Text(
                            text = question,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (aiResponse != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Co-Counsel Cryptographic Advice:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.clearAdvisorLog() }) {
                    Text("Clear Advice", fontSize = 11.sp)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(14.dp)
                ) {
                    Text(
                        text = aiResponse ?: "",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color.White
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Empty",
                        tint = MutedSlate.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Awaiting counsel query prompt. Tap a topic above to begin.",
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                }
            }
        }
    }
}

@Composable
fun DocsScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf("Protocol Overview") }
    val sections = WhitepaperDocs.specifications.keys.toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEach { section ->
                FilterChip(
                    selected = (selectedTab == section),
                    onClick = { selectedTab = section },
                    label = { Text(section) }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val contentText = WhitepaperDocs.specifications[selectedTab] ?: ""
                
                // Simple customized markdown visual layouts
                contentText.split("\n\n").forEach { paragraph ->
                    if (paragraph.startsWith("# ")) {
                        Text(
                            text = paragraph.replace("# ", ""),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (paragraph.startsWith("## ")) {
                        Text(
                            text = paragraph.replace("## ", ""),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else if (paragraph.startsWith("```")) {
                        val cleanedCode = paragraph.replace("```", "").trim()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF090B0E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cleanedCode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CosmicCyan,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        val bulletFormatted = paragraph.startsWith("- ")
                        Text(
                            text = if (bulletFormatted) paragraph else paragraph,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 10.sp, color = MutedSlate, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}

// Custom flow row layout matching old layout
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        content()
    }
}
