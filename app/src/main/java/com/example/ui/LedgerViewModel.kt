package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ProtocolAdvisor
import com.example.data.AccountEntity
import com.example.data.AppDatabase
import com.example.data.BlockEntity
import com.example.data.LedgerRepository
import com.example.data.MempoolEntity
import com.example.data.PeerEntity
import com.example.data.TxEntity
import com.example.protocol.CryptoHelper
import com.example.protocol.NetworkType
import com.example.protocol.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.KeyPair

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val lDao = db.ledgerDao
    val repository = LedgerRepository(application, lDao)

    // State flows from Repo
    val activeNetwork: StateFlow<NetworkType> = repository.activeNetwork
    val activeKeyPair: StateFlow<KeyPair?> = repository.activeKeyPair
    val walletAddress: StateFlow<String?> = repository.walletAddress
    val syncStatus: StateFlow<String> = repository.syncStatus
    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    // DB flows
    private val _blocks = MutableStateFlow<List<BlockEntity>>(emptyList())
    val blocks: StateFlow<List<BlockEntity>> = _blocks

    private val _mempool = MutableStateFlow<List<MempoolEntity>>(emptyList())
    val mempool: StateFlow<List<MempoolEntity>> = _mempool

    private val _peers = MutableStateFlow<List<PeerEntity>>(emptyList())
    val peers: StateFlow<List<PeerEntity>> = _peers

    // Local Account details
    private val _walletAccountState = MutableStateFlow<AccountEntity?>(null)
    val walletAccountState: StateFlow<AccountEntity?> = _walletAccountState

    // Recent Transactions list
    private val _recentTransactions = MutableStateFlow<List<TxEntity>>(emptyList())
    val recentTransactions: StateFlow<List<TxEntity>> = _recentTransactions

    // Database of all accounts (Rich List)
    private val _richList = MutableStateFlow<List<AccountEntity>>(emptyList())
    val richList: StateFlow<List<AccountEntity>> = _richList

    // --- Interactive Operations Feedback ---
    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus

    // --- AI Advisor Status ---
    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading

    private val advisor = ProtocolAdvisor()

    // --- RPC Simulator Log ---
    private val _rpcCommandLog = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val rpcCommandLog: StateFlow<List<Pair<String, String>>> = _rpcCommandLog

    init {
        // Run initial configuration sync on testnet
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeNetworkState(NetworkType.TESTNET)
            observeLedgerStreams()
            startSimulatedNetworkTraffic()
        }
    }

    private fun observeLedgerStreams() {
        // Collect blocks flow
        viewModelScope.launch {
            activeNetwork.flatMapLatest { net ->
                repository.getBlocksFlow(net)
            }.collect { list ->
                _blocks.value = list
            }
        }

        // Collect mempool flow
        viewModelScope.launch {
            activeNetwork.flatMapLatest { net ->
                repository.getMempoolFlow(net)
            }.collect { list ->
                _mempool.value = list
            }
        }

        // Collect local address state
        viewModelScope.launch {
            walletAddress.collect { addr ->
                if (addr != null) {
                    refreshLocalAccount(addr)
                }
            }
        }

        // Collect recent txs and rich list
        viewModelScope.launch {
            lDao.getRecentTxsFlow(40).collect { list ->
                _recentTransactions.value = list
            }
        }

        viewModelScope.launch {
            lDao.getRichListFlow().collect { list ->
                _richList.value = list
            }
        }

        // Collect connected peers
        viewModelScope.launch {
            repository.getPeersFlow().collect { list ->
                _peers.value = list
            }
        }
    }

    /**
     * Refreshes local wallet metrics.
     */
    suspend fun refreshLocalAccount(address: String) {
        val account = repository.getWalletBalance(address)
        _walletAccountState.value = account
    }

    /**
     * Switechs the network configuration (0.10, 0.11).
     */
    fun selectNetwork(network: NetworkType) {
        viewModelScope.launch {
            repository.switchNetwork(network)
            val addr = walletAddress.value
            if (addr != null) {
                refreshLocalAccount(addr)
            } else {
                _walletAccountState.value = null
            }
        }
    }

    /**
     * Instantiates a custom BIP-39 style or cryptographic secure EC wallet.
     */
    fun createWallet() {
        viewModelScope.launch {
            try {
                repository.createNewWallet()
                _operationStatus.value = "Wallet created and active keys compiled!"
                delay(3000)
                _operationStatus.value = null
            } catch (e: Exception) {
                _operationStatus.value = "Error creating wallet: ${e.message}"
            }
        }
    }

    /**
     * Dispatches a fresh transaction from this wallet to a designated receiver (0.7, 0.8).
     */
    fun sendPayment(receiver: String, amountMicroAet: Long, feeMicroAet: Long) {
        val keys = activeKeyPair.value
        val senderAddr = walletAddress.value ?: return

        if (keys == null) {
            _operationStatus.value = "Wallet not instantiated. Unlock or generate keys first."
            return
        }

        viewModelScope.launch {
            _operationStatus.value = "Formulating client transaction proposal..."
            try {
                // Determine sender nonce from account details
                val acc = repository.getWalletBalance(senderAddr)
                val nextNonce = acc.nonce + 1

                val tx = Transaction(
                    sender = senderAddr,
                    receiver = receiver,
                    amount = amountMicroAet,
                    fee = feeMicroAet,
                    nonce = nextNonce,
                    timestamp = System.currentTimeMillis()
                )

                // Sign using keys (0.7)
                tx.signTransaction(keys.private, activeNetwork.value.chainId)

                // Propagate to mempool (0.8)
                val result = repository.submitTransaction(tx)
                if (result.isSuccess) {
                    _operationStatus.value = "Transaction propagated successfully! Tx Hash: ${result.getOrThrow().substring(0, 16)}..."
                    refreshLocalAccount(senderAddr)
                } else {
                    _operationStatus.value = "Mempool Rejected transaction proposal: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _operationStatus.value = "Error propagating transaction: ${e.message}"
            }
            delay(4000)
            _operationStatus.value = null
        }
    }

    /**
     * Staking lock protocol actions (0.19)
     */
    fun lockStake(amount: Long) {
        viewModelScope.launch {
            _operationStatus.value = "Submitting collateral stake claim to the network..."
            val res = repository.stakeCoins(amount)
            if (res.isSuccess) {
                _operationStatus.value = "Collateral stake committed successfully! TxHash: ${res.getOrThrow().substring(0, 16)}"
                walletAddress.value?.let { refreshLocalAccount(it) }
            } else {
                _operationStatus.value = "Collateral stake rejected: ${res.exceptionOrNull()?.message}"
            }
            delay(4000)
            _operationStatus.value = null
        }
    }

    fun releaseStake() {
        viewModelScope.launch {
            _operationStatus.value = "Broadcasting un-delegation claim..."
            val res = repository.unstakeCoins()
            if (res.isSuccess) {
                _operationStatus.value = "Collateral stake released back to balance!"
                walletAddress.value?.let { refreshLocalAccount(it) }
            } else {
                _operationStatus.value = "Release rejected: ${res.exceptionOrNull()?.message}"
            }
            delay(4000)
            _operationStatus.value = null
        }
    }

    /**
     * Simulates block proposal generation inside the local client (0.4, 0.20).
     */
    fun proposeMockBlock() {
        viewModelScope.launch {
            _operationStatus.value = "Consensus Engine starting block compilation..."
            val result = repository.minePendingBlock(proposerPrivateKey = null)
            if (result.isSuccess) {
                val blockObj = result.getOrThrow()
                _operationStatus.value = "New Block Finalised! Height: #${blockObj.height}, Hash: ${blockObj.hash.substring(0, 12)}..."
                walletAddress.value?.let { refreshLocalAccount(it) }
            } else {
                _operationStatus.value = "Compilation suspended: ${result.exceptionOrNull()?.message}"
            }
            delay(4000)
            _operationStatus.value = null
        }
    }

    /**
     * Secure Export Backup Actions (0.14)
     */
    fun exportBackup(passphrase: String): String? {
        return try {
            repository.createAESEncryptedBackup(passphrase)
        } catch (e: Exception) {
            _operationStatus.value = "Export failed: ${e.message}"
            null
        }
    }

    /**
     * Recovery Backup restore Actions (0.14)
     */
    fun importBackup(encryptedPayload: String, passphrase: String) {
        viewModelScope.launch {
            val res = repository.restoreKeysFromBackup(encryptedPayload, passphrase)
            if (res.isSuccess) {
                _operationStatus.value = "Keys restored and verified! Welcome back."
                walletAddress.value?.let { refreshLocalAccount(it) }
            } else {
                _operationStatus.value = "Restoration aborted: ${res.exceptionOrNull()?.message}"
            }
            delay(4000)
            _operationStatus.value = null
        }
    }

    /**
     * RPC API Console Execution (0.17)
     */
    fun sendRpcMethod(methodName: String, paramsJson: String) {
        viewModelScope.launch {
            val jsonParams = try {
                if (paramsJson.trim().isEmpty()) JSONObject() else JSONObject(paramsJson)
            } catch (e: Exception) {
                _rpcCommandLog.value = _rpcCommandLog.value + ("> Error parsing parameters" to "Provide a valid JSON input, e.g. {\"address\":\"...\"}")
                return@launch
            }

            _rpcCommandLog.value = _rpcCommandLog.value + ("> execute: $methodName($paramsJson)" to "Processing...")
            val result = repository.executeRpc(methodName, jsonParams)
            val currentList = _rpcCommandLog.value.toMutableList()
            if (currentList.isNotEmpty() && currentList.last().second == "Processing...") {
                currentList.removeAt(currentList.lastIndex)
            }
            _rpcCommandLog.value = currentList + ("> execute: $methodName($paramsJson)" to result.toString(2))
        }
    }

    fun clearRpcLogs() {
        _rpcCommandLog.value = emptyList()
    }

    /**
     * Queries the High Thinking Gemini 3.1 Pro API Protocol Advisor (0.21, gemini-api skill).
     */
    fun askAdvisor(prompt: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResponse.value = "The Protocol Advisor is thinking deeply using high-capability models. Please hold..."
            try {
                val advice = advisor.getAdvice(prompt, activeNetwork.value)
                _aiResponse.value = advice
            } catch (e: Exception) {
                _aiResponse.value = "Error loading advice: ${e.message}"
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun clearAdvisorLog() {
        _aiResponse.value = null
    }

    /**
     * Starts a simulated background network loop (0.12, 0.20, 0.8).
     * Simulates external peers propagating transactions on the active chain network.
     */
    private fun startSimulatedNetworkTraffic() {
        viewModelScope.launch(Dispatchers.IO) {
            val mockAddresses = listOf(
                "AET_VALIDATOR_ALICE_NODE",
                "AET_VALIDATOR_BOB_NODE",
                "AET_LIQUID_CHARLIE_POOL",
                "AET_VENTURE_CAPITAL_VAULT",
                "AET_DAO_TREASURY",
                "AET_STAKER_DANIEL_VENTURE"
            )

            while (true) {
                delay(12000) // Run transaction generation every 12 seconds
                val currentNet = activeNetwork.value

                // Propagate a simulation peer transaction
                val randomSender = mockAddresses.random()
                var randomReceiver = mockAddresses.random()
                while (randomReceiver == randomSender) {
                    randomReceiver = mockAddresses.random()
                }

                val amount = (500_000L..15_000_000L).random() // 0.5 to 15 tokens
                val fee = currentNet.minFee + (1..10).random()
                
                // Get sender's nonce from DB
                val senderAcc = lDao.getAccount(randomSender)
                val expectedNonce = (senderAcc?.nonce ?: 0) + 1

                // Simulate peer insert direct to mempool (0.8)
                val mockTx = Transaction(
                    sender = randomSender,
                    receiver = randomReceiver,
                    amount = amount,
                    fee = fee,
                    nonce = expectedNonce,
                    timestamp = System.currentTimeMillis()
                )
                mockTx.signature = CryptoHelper.sha256("peer_signature_" + System.currentTimeMillis())

                // Insert sender if missing from state db
                if (senderAcc == null) {
                    lDao.insertAccount(AccountEntity(randomSender, 500_000_000L, 0, 0))
                }

                // Push item to Database
                val mEntity = MempoolEntity(
                    signature = mockTx.signature,
                    sender = mockTx.sender,
                    receiver = mockTx.receiver,
                    amount = mockTx.amount,
                    fee = mockTx.fee,
                    nonce = mockTx.nonce,
                    timestamp = mockTx.timestamp,
                    chainId = currentNet.chainId,
                    hash = mockTx.calculateHash(currentNet.chainId)
                )
                lDao.insertMempoolItem(mEntity)

                // Add random peer activity sync log (0.12)
                lDao.insertPeer(PeerEntity(
                    ipAddress = "192.168.1." + (2..254).random(),
                    role = "LIGHT_MOBILE_PEER",
                    isHealthy = true,
                    lastSeen = System.currentTimeMillis(),
                    displayName = "PeerNode-" + (100..999).random()
                ))

                // Auto audit: If mempool climbs past 3 transactions, have a peer Validator propose a Block block in the background! (0.4, 0.20)
                val memCount = lDao.getMempoolList(currentNet.chainId).size
                if (memCount >= 3) {
                    delay(3000)
                    repository.minePendingBlock(proposerPrivateKey = null)
                }
            }
        }
    }
}
